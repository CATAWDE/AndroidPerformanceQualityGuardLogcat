# Android Performance & Quality Guard — Dynamic Scan Edition

A lightweight Kotlin/Android development aid combining a Gradle source/resource scanner with selected runtime checks. Findings are heuristic and only appear when a rule actually matches, or when runtime instrumentation reports the behavior.

## Compatibility

- Android Gradle Plugin: 8.6.0-beta02
- Kotlin: 1.9.24
- Gradle distribution: 8.7
- JDK: 17
- compileSdk: 35

## Open and run

1. Extract the ZIP and open `AndroidPerformanceQualityGuard-Logcat` in Android Studio.
2. Set **Gradle JDK = JDK 17** and allow Gradle sync to complete.
3. Build and run `sample-app`.
4. Open Logcat and filter by `tag:QualityGuard`.
5. For warnings only, use `tag:QualityGuard level:WARN`.

The sample no longer prints hardcoded PG001/PG003/PG004/PG005/PG006 startup warnings. It prints actual findings from the latest generated scan report and actual runtime checks. Therefore, not every rule ID must appear on every launch.

> The ZIP contains wrapper scripts and `gradle-wrapper.properties`, but does not include `gradle-wrapper.jar`. If the wrapper cannot start, open/sync/build with Android Studio or generate a complete wrapper using an installed Gradle 8.7 distribution.

## Rule behavior

| Rule | Detection method | When it appears |
|---|---|---|
| PG001 — Layout complexity | Gradle heuristic counts XML tags and estimates nesting depth; runtime `scanViewHierarchy()` checks rendered depth | When configured XML thresholds or runtime view-depth threshold are exceeded |
| PG002 — Large resources | Gradle scanner checks supported resource files | When a supported image/SVG exceeds 1 MB or supported animation/video/JSON file exceeds 512 KB |
| PG003 — API-call bursts | Runtime `QualityGuard.recordApiCall()` counter | When 10 calls are reported for the same screen within 5 seconds |
| PG004 — Nested scrolling | Gradle heuristic checks XML for multiple common scrolling widgets; runtime hierarchy check detects nested scroll containers | When a matching nested-scrolling pattern is found |
| PG005 — Duplicate resources | Gradle scanner hashes XML/SVG file contents | When two or more scanned XML/SVG resource files are byte-identical |
| PG006 — Duplicate utility methods | Gradle heuristic searches for simple one-line Kotlin/Java methods with normalized matching bodies | Only for method patterns supported by the simple regex; this is not a full parser |

## Dynamic code/resource changes

Static rules are not hardcoded to the sample's current findings. The Gradle scanner walks supported files under each included module's `src` source-set folders (including `main`, `debug`, `release`, `res/drawable`, `mipmap`, `raw`, and `assets`). When you change source/resource files, rebuild the project. The scanner generates a fresh report during the build, the report is packaged as an app asset, and `QualityGuard.start()` logs that report when the newly built app is launched.

This is **build-time scanning**, not a live file watcher. Editing a file without rebuilding will not refresh the packaged scan report. Only rules whose conditions match produce findings; absence of a warning does not prove the code is issue-free.

## Runtime API-call monitoring (PG003)

The library cannot automatically observe every Retrofit/OkHttp request unless the application integrates the counter. Call `recordApiCall()` before each actual request, preferably from a central network interceptor or repository/network wrapper:

```kotlin
QualityGuard.recordApiCall("HomeScreen", "/users")
repository.loadUsers()
```

The sample's **Simulate 12 API calls** button is a test control that intentionally reports 12 simulated calls. It is not automatic interception of arbitrary network traffic. In a real app, instrument actual calls instead.

## Runtime view hierarchy checks (PG001 / PG004)

The sample calls `scanViewHierarchy()` after `setContentView`. For other screens, call it after the view hierarchy has been created. The scanner reports actual rendered depth/nested scrolling; the source scanner separately checks XML patterns.

## Scanner thresholds and supported file types

- Raster/SVG images: greater than **1 MB**.
- Animation/video/JSON files: greater than **512 KB**.
- Supported extensions include PNG, JPG/JPEG, WebP, GIF, APNG, BMP, TIFF, AVIF, SVG, JSON, Lottie, Rive, TGS, MP4, and WebM.

The scanner prints results in Gradle Build output and writes a generated report asset that is logged on app launch under `QualityGuard`. The report contains a message if no supported static rule matched. PG003 is runtime-only and is not produced by the static scan.

## Use in another project

For local development, include `quality-guard-runtime` as a module and add:

```kotlin
dependencies {
    implementation(project(":quality-guard-runtime"))
}
```

Call `QualityGuard.start(applicationContext)` once in your application startup. Call `recordApiCall()` from actual network request instrumentation and `scanViewHierarchy()` for screens that need runtime view checks. To run static scans in another project, copy and apply `quality-guard-scan.gradle` from the root build script and adapt module names if needed; the current report packaging bridge targets the sample module `:sample-app`.

## Limitations

- Threshold-based warnings are heuristics, not proof of a performance defect.
- XML nesting parsing is deliberately lightweight and is not a complete XML/UAST analysis.
- PG005 only detects byte-identical XML/SVG content, not every semantically equivalent resource.
- PG006 only detects a narrow subset of simple one-line method patterns and can miss real duplicates.
- PG003 only counts calls explicitly reported to the library.
- Build-time findings require a rebuild and relaunch to appear in the packaged report.
- This library complements Android Lint, Layout Inspector, profiling, tests, and code review; it does not replace them.

## JVM target

Both Android modules use Java compatibility 17 and Kotlin `jvmTarget = "17"`. Set Android Studio's Gradle JDK to JDK 17.
