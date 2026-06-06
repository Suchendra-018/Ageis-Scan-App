package com.example.myapplication18

data class AppRiskMetadata(
    val apps: List<AppRisk> = emptyList()
)

data class AppRisk(
    val app_name: String = "",
    val risk_level: String = "Safe",
    val scam_type: String = "Verified Safe",
    val description: String = "No known threats found.",
    val average_rating: Double = 4.5,
    val reviews: List<AppReview> = emptyList(),
    val gemini_explanation: String? = null
)

data class AppReview(
    val user: String = "Anonymous",
    val stars: Int = 5,
    val comment: String = ""
)

data class ScanResult(
    val appName: String,
    val packageName: String,
    val riskLevel: String, // "Safe", "Moderate", "High"
    val riskScore: Int,
    val scamType: String,
    val description: String,
    val averageRating: Double,
    val reviews: List<AppReview>,
    var isExpanded: Boolean = false,
    val geminiExplanation: String? = null
)
