package com.catawde.qualityguardsample

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import com.catawde.qualityguard.QualityGuard

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        QualityGuard.start(applicationContext)
        window.decorView.post {
            QualityGuard.scanViewHierarchy(
                findViewById(android.R.id.content),
                screenName = "MainActivity.kt | layout=activity_main.xml"
            )
        }

        // Only findings detected by the current build scan or runtime checks are logged.
        // No hardcoded demo warnings are emitted on startup.

        // Demo button: simulates instrumentation calls so you can verify PG003 in Logcat.
        // In a real app, call recordApiCall() immediately before each actual Retrofit/OkHttp request.
        findViewById<Button>(R.id.simulate_api_burst).setOnClickListener {
            repeat(12) { index -> QualityGuard.recordApiCall("MainActivity.kt | layout=activity_main.xml", "/demo/endpoint/$index") }
        }

        fun formatAmount(amount: Double): String {
            return String.format("%.2f", amount)
        }

        fun formatPrice(price: Double): String {
            return String.format("%.2f", price)
        }
    }
}
