package com.example.myapplication18

import android.accessibilityservice.AccessibilityService
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
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            // Monitor Google Play Store
            if (event.packageName == "com.android.vending") {
                val rootNode = rootInActiveWindow ?: return
                checkNodes(rootNode)
            }
        } catch (e: Exception) {}
    }

    private fun checkNodes(node: AccessibilityNodeInfo?) {
        if (node == null) return

        try {
            val text = node.text?.toString()
            if (text != null) {
                val match = riskMetadata.find { it.app_name.equals(text, ignoreCase = true) }
                if (match != null && match.risk_level.equals("High", ignoreCase = true)) {
                    showWarning(match.app_name, match.scam_type)
                }
            }

            for (i in 0 until node.childCount) {
                checkNodes(node.getChild(i))
            }
        } catch (e: Exception) {}
    }

    private fun showWarning(appName: String, scamType: String) {
        Toast.makeText(this, "⚠️ AegisAI SHIELD: $appName is flagged as $scamType!", Toast.LENGTH_LONG).show()
    }

    override fun onInterrupt() {}
}
