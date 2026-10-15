package com.catawde.qualityguard

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ConcurrentHashMap

/** Runtime checks. Source-file checks are provided separately by the qualityGuardScan Gradle task. */
object QualityGuard {
    private const val TAG = "QualityGuard"
    private const val API_BURST_WINDOW_MS = 5_000L
    private const val API_BURST_LIMIT = 10
    private const val MAX_VIEW_DEPTH = 8
    private val apiCallsByScreen = ConcurrentHashMap<String, MutableList<Long>>()

    /** Logs a startup banner. Call [scanViewHierarchy] after the screen view has been created. */
    fun start(context: Context) {
        Log.i(TAG, "Android Performance & Quality Guard started | app=${context.packageName}")
        logBuildScanReport(context)
    }

    private fun logBuildScanReport(context: Context) {
        try {
            context.assets.open("qualityguard-findings.txt").bufferedReader().useLines { lines ->
                Log.i(TAG, "========== Build-time source/resource scan ==========")
                lines.forEach { line ->
                    when {
                        line.startsWith("[PG") -> Log.w(TAG, line)
                        else -> Log.i(TAG, line)
                    }
                }
                Log.i(TAG, "========== End source/resource scan ==========")
            }
        } catch (_: java.io.IOException) {
            Log.w(TAG, "No packaged scanner report found. Build the app again so qualityGuardScan can generate it.")
        }
    }

    /** Call immediately before each real network request, using a stable screen name. */
    @Synchronized
    fun recordApiCall(screenName: String, endpoint: String? = null) {
        val now = System.currentTimeMillis()
        val calls = apiCallsByScreen.getOrPut(screenName) { mutableListOf() }
        calls.removeAll { now - it > API_BURST_WINDOW_MS }
        calls.add(now)
        if (calls.size == API_BURST_LIMIT) {
            warn("PG003", "screen=$screenName | ${calls.size} API calls within ${API_BURST_WINDOW_MS / 1000}s${endpoint?.let { " | latestEndpoint=$it" } ?: ""}", "Review whether calls can be combined, delayed, cached, or started only when needed.")
        }
    }

    /** Inspect the actual rendered view tree. Call after setContentView / ComposeView setup. */
    fun scanViewHierarchy(root: View, screenName: String = root.context.javaClass.simpleName) {
        val result = HierarchyResult()
        inspect(root, 1, false, result)
        if (result.maxDepth > MAX_VIEW_DEPTH) {
            warn("PG001", "screen=$screenName | rendered view hierarchy depth=${result.maxDepth} levels", "Inspect layout XML for this screen and flatten unnecessary containers; use Layout Inspector to locate the views.")
        }
        if (result.hasNestedScroll) {
            warn("PG004", "screen=$screenName | a scrolling container appears inside another scrolling container", "Inspect this screen's layout XML and prefer one primary scrolling parent unless nested scrolling is intentional.")
        }
        Log.i(TAG, "View hierarchy scan complete | screen=$screenName | maxDepth=${result.maxDepth} | nestedScroll=${result.hasNestedScroll}")
    }

    private class HierarchyResult { var maxDepth = 0; var hasNestedScroll = false }

    private fun inspect(view: View, depth: Int, insideScroll: Boolean, result: HierarchyResult) {
        result.maxDepth = maxOf(result.maxDepth, depth)
        val isScroll = view is ScrollView || view is RecyclerView || view.javaClass.name.contains("NestedScrollView") || view.javaClass.name.contains("HorizontalScrollView")
        if (isScroll && insideScroll) {
            result.hasNestedScroll = true
            val resourceName = view.id.takeIf { it != View.NO_ID }?.let { id ->
                runCatching { view.resources.getResourceEntryName(id) }.getOrNull()
            } ?: "no_resource_id"
            Log.d(TAG, "Nested scrolling view | screen view class=${view.javaClass.simpleName} | viewId=$resourceName")
        }
        if (view is ViewGroup) {
            val childInsideScroll = insideScroll || isScroll
            for (index in 0 until view.childCount) inspect(view.getChildAt(index), depth + 1, childInsideScroll, result)
        }
    }

    fun warn(ruleId: String, issue: String, suggestion: String) {
        Log.w(TAG, "[$ruleId] $issue | Suggestion: $suggestion")
    }
}
