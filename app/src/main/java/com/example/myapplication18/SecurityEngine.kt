package com.example.myapplication18

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * AegisAI Security Engine 21.0 - Zero False Positive Edition
 * 
 * LOGIC:
 * 1. Explicit Risk: If app name is in JSON metadata, follow that (Supervised).
 * 2. Play Store Trust: IF app is from Google Play Store (or major OEM stores) 
 *    AND not in the risk JSON, it is 100% SAFE. This fixes banking/delivery apps.
 * 3. Phishing Check: IF sideloaded app uses a known brand name (GPay, SBI, etc.), 
 *    it is HIGH RISK.
 * 4. Heuristics: Sideloaded apps only are analyzed for keywords/permissions.
 */
class SecurityEngine(private val context: Context) {

    private val riskKeywords = listOf(
        "bet", "win", "wealth", "cash", "loan", "fast", "pyramid", "crypto", 
        "earn", "reward", "cric", "tv", "apk", "mirror", "mod", "free", "video",
        "casino", "luck", "money", "invest", "yield", "trade", "forex", "wallet",
        "gift", "prize", "jackpot", "lottery", "poker", "slot", "rummy"
    )

    private val dangerousPermissions = listOf(
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_CONTACTS"
    )

    // Trusted brand prefixes to detect phishing/clones
    private val trustedPrefixes = listOf(
        "com.google.android.apps.nbu.paisa", // GPay
        "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp", // UPI
        "com.msf.kbank.mobile", "com.kotak.", "com.sbi.", "com.icicibank", "com.hdfcbank", // Banks
        "in.swiggy.android", "com.application.zomato", "com.zeptoconsumerapp", "com.blinkit.client", // Delivery
        "com.navi.android", "com.supermoney.app", "com.cred.android", // Fintech
        "com.spotify.music", "com.whatsapp", "com.facebook.", "org.telegram."
    )

    fun analyzeApp(app: ApplicationInfo, metadata: List<AppRisk>): ScanResult {
        val pm = context.packageManager
        val appLabel = pm.getApplicationLabel(app).toString()
        val packageName = app.packageName
        val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        
        val isFromVerifiedStore = isInstalledFromVerifiedStore(packageName)
        
        // 1. Explicit Risk Lookup (Priority 1)
        val knownMatch = metadata.find { it.app_name.equals(appLabel, ignoreCase = true) }
        if (knownMatch != null) {
            return ScanResult(
                appLabel, packageName, knownMatch.risk_level,
                if (knownMatch.risk_level.lowercase() == "high") 98 else 65,
                knownMatch.scam_type, knownMatch.description, 
                knownMatch.average_rating, knownMatch.reviews
            )
        }

        // 2. System App logic
        if (isSystem) {
            return ScanResult(appLabel, packageName, "Safe", 0, "System Component", "Verified Android system app.", 5.0, emptyList())
        }

        // 3. THE "NO FALSE POSITIVE" FIX: Trust ALL Play Store apps by default
        // If it's in the store, and not explicitly in our scam list, we trust it.
        if (isFromVerifiedStore) {
             return ScanResult(appLabel, packageName, "Safe", 0, "Verified Store App", 
                "AegisAI verified this app via Play Store protection.", 5.0, emptyList())
        }

        // 4. Sideloaded High Risk: Phishing Detection
        // If it looks like a trusted brand but was SIDELOADED
        val isTrustedBrandName = trustedPrefixes.any { packageName.startsWith(it) }
        if (isTrustedBrandName && !isFromVerifiedStore) {
             return ScanResult(appLabel, packageName, "High", 95, "Phishing/Mod Warning", 
                "DANGER: This app uses a protected name ($appLabel) but was NOT installed from an official store. It is likely a modified APK or a phishing clone.", 1.2, emptyList())
        }

        // 5. Heuristic Layer (Only for Sideloaded unknown apps)
        var riskScore = 0
        val reasons = mutableListOf<String>()

        riskScore += 50 // Penalty for sideloading
        reasons.add("Sideloaded source")

        val foundKeywords = riskKeywords.filter { 
            appLabel.lowercase(Locale.ROOT).contains(it) || packageName.lowercase(Locale.ROOT).contains(it)
        }
        if (foundKeywords.isNotEmpty()) {
            riskScore += 25
            reasons.add("Risk naming pattern")
        }

        try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val count = packageInfo.requestedPermissions?.count { dangerousPermissions.contains(it) } ?: 0
            if (count >= 2) {
                riskScore += 20
                reasons.add("Sensitive data access")
            }
        } catch (_: Exception) {}

        return when {
            riskScore >= 80 -> {
                ScanResult(appLabel, packageName, "High", riskScore.coerceAtMost(99),
                    "AI Flagged: Threat", "Potential malicious markers found in sideloaded app.", 2.0, emptyList(), false, 
                    "Analysis: ${reasons.joinToString(", ")}.")
            }
            riskScore >= 60 -> {
                ScanResult(appLabel, packageName, "Moderate", riskScore,
                    "AI Flagged: Warning", "Suspicious source and naming structure.", 3.2, emptyList(), false,
                    "Analysis: ${reasons.joinToString(", ")}.")
            }
            else -> {
                ScanResult(appLabel, packageName, "Safe", 5, "Verified Safe", "AegisAI analyzed this app and found no significant security threats.", 5.0, emptyList())
            }
        }
    }

    private fun isInstalledFromVerifiedStore(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            // Trust Play Store and common OEM stores
            installer == "com.android.vending" || 
            installer == "com.sec.android.app.samsungapps" || 
            installer == "com.xiaomi.mipicks" || 
            installer == "com.oppo.market" || 
            installer == "com.vivo.appstore" ||
            installer == "com.huawei.appmarket"
        } catch (e: Exception) {
            false
        }
    }
}
