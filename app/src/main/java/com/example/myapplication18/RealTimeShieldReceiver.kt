package com.example.myapplication18

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RealTimeShieldReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                val packageName = intent.data?.encodedSchemeSpecificPart ?: return
                checkInstalledApp(context, packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkInstalledApp(context: Context, packageName: String) {
        val riskMetadata = loadMetadata(context)
        val pm = context.packageManager
        
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            
            val match = riskMetadata.find { it.app_name.equals(appName, ignoreCase = true) }
            if (match != null && match.risk_level.equals("High", ignoreCase = true)) {
                // Using a simpler string for the background toast
                Toast.makeText(context, "🚨 AegisAI ALERT: $appName (High Risk) has been installed!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadMetadata(context: Context): List<AppRisk> {
        return try {
            val jsonString = context.assets.open("app_risk_metadata.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<AppRiskMetadata>() {}.type
            val metadata: AppRiskMetadata? = Gson().fromJson(jsonString, type)
            metadata?.apps ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
