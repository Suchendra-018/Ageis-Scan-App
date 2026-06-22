package com.example.myapplication18

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

class SecurityEngine(private val context: Context) {

    private val riskKeywords = listOf(
        "bet", "win", "wealth", "cash", "loan", "fast", "pyramid", "crypto", 
        "earn", "reward", "cric", "tv", "apk", "mirror", "mod", "free", "video",
        "casino", "luck", "money", "invest", "yield", "trade", "forex", "wallet",
        "gift", "prize", "jackpot", "lottery", "poker", "slot"
    )

    private val dangerousPermissions = listOf(
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_CONTACTS"
    )

    // Trusted prefixes for UPI, Banking, and Global Tech apps to avoid false positives
    private val trustedPrefixes = listOf(
        "com.google.android.apps.nbu.paisa", // GPay
        "com.phonepe.app", // PhonePe
        "net.one97.paytm", // Paytm
        "in.org.npci.upiapp", // BHIM
        "com.microsoft.",
        "com.google.android.",
        "com.apple.",
        "com.spotify.music", // Spotify Official
        "com.whatsapp",
        "com.facebook.",
        "com.instagram.",
        "com.twitter.",
        "com.linkedin.",
        "org.telegram."
    )

    fun analyzeApp(app: ApplicationInfo, metadata: List<AppRisk>): ScanResult {
        val pm = context.packageManager
        val appLabel = pm.getApplicationLabel(app).toString()
        val packageName = app.packageName
        val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        
        // 1. Check Trust-list (Supervised Safety)
        if (trustedPrefixes.any { packageName.startsWith(it) }) {
             return ScanResult(appLabel, packageName, "Safe", 0, "Verified Official App", 
                 "This application is recognized as a trusted official service.", 5.0, emptyList())
        }

        // 2. Database Lookup (Supervised Risk)
        val knownMatch = metadata.find { it.app_name.equals(appLabel, ignoreCase = true) }
        if (knownMatch != null) {
            return ScanResult(
                appLabel, packageName, knownMatch.risk_level,
                if (knownMatch.risk_level.lowercase() == "high") 98 else 65,
                knownMatch.scam_type, knownMatch.description, 
                knownMatch.average_rating, knownMatch.reviews
            )
        }

        // 3. System App logic
        if (isSystem) {
            return ScanResult(appLabel, packageName, "Safe", 0, "System Component", "Verified Android system app.", 5.0, emptyList())
        }

        // 4. Heuristic/ML Layer (Unsupervised Pattern Detection)
        var riskScore = 0
        val reasons = mutableListOf<String>()

        // Analyze Label for high-risk keywords
        val foundKeywords = riskKeywords.filter { 
            appLabel.lowercase(Locale.ROOT).contains(it) || packageName.lowercase(Locale.ROOT).contains(it)
        }
        if (foundKeywords.isNotEmpty()) {
            riskScore += 30 + (foundKeywords.size * 10)
            reasons.add("Risk-associated keywords found")
        }

        // Permission Profiling
        try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val requested = packageInfo.requestedPermissions
            val count = requested?.count { dangerousPermissions.contains(it) } ?: 0
            if (count > 1) {
                riskScore += count * 12
                reasons.add("Sensitive permission access requested")
            }
        } catch (_: Exception) {}

        // Source Analysis
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val installer = pm.getInstallSourceInfo(packageName).installingPackageName
                if (installer == null || (installer != "com.android.vending" && installer != "com.amazon.venezia")) {
                    riskScore += 25
                    reasons.add("Side-loaded / Unverified installation source")
                }
            } catch (_: Exception) {}
        }

        // Final Classification
        return when {
            riskScore >= 70 -> {
                ScanResult(
                    appLabel, packageName, "High", riskScore.coerceAtMost(99),
                    "AI Flagged: Critical",
                    "Multiple suspicious structural patterns detected.",
                    2.0, emptyList(), false, 
                    "Heuristic Diagnosis: ${reasons.joinToString(", ")}."
                )
            }
            riskScore >= 35 -> {
                ScanResult(
                    appLabel, packageName, "Moderate", riskScore,
                    "AI Flagged: Review",
                    "Application exhibits atypical characteristics.",
                    3.5, emptyList(), false,
                    "Heuristic Diagnosis: ${reasons.joinToString(", ")}."
                )
            }
            else -> {
                ScanResult(appLabel, packageName, "Safe", 5, "Unrecognized", "No known risks detected by AegisAI.", 5.0, emptyList())
            }
        }
    }
}
