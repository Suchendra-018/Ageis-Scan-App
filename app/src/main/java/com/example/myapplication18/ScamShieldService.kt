package com.example.myapplication18

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ScamShieldService : AccessibilityService() {

    private var riskMetadata: List<AppRisk> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadMetadata()
    }

    private fun loadMetadata() {
        try {
            val jsonString = assets.open("app_risk_metadata.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<AppRiskMetadata>() {}.type
            val metadata: AppRiskMetadata? = Gson().fromJson(jsonString, type)
            riskMetadata = metadata?.apps ?: emptyList()
        } catch (e: Exception) {
            Log.e("AegisAI", "Failed to load metadata in service", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only monitor Google Play Store to save resources
        if (event.packageName == "com.android.vending") {
            try {
                val rootNode = rootInActiveWindow ?: return
                checkNodes(rootNode)
            } catch (e: Exception) {
                Log.e("AegisAI", "Error processing accessibility event", e)
            }
        }
    }

    private fun checkNodes(node: AccessibilityNodeInfo?) {
        if (node == null) return

        try {
            val nodeText = node.text?.toString()
            if (!nodeText.isNullOrBlank()) {
                val match = riskMetadata.find { it.app_name.equals(nodeText, ignoreCase = true) }
                if (match != null && match.risk_level.equals("High", ignoreCase = true)) {
                    showWarning(match.app_name, match.scam_type)
                }
            }

            // Recurse children safely
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    checkNodes(child)
                }
            }
        } catch (e: Exception) {
            // Silently handle node errors during dynamic UI changes
        }
    }

    private fun showWarning(appName: String, scamType: String) {
        // Accessibility services can show toasts on any screen
        Toast.makeText(applicationContext, "🚨 AegisAI: $appName is flagged for $scamType!", Toast.LENGTH_LONG).show()
    }

    override fun onInterrupt() {}
}
