package com.example.myapplication18

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication18.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppRiskAdapter
    private var allScanResults: List<ScanResult> = emptyList()
    private var isShowingSafe = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            enableEdgeToEdge()
            ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinator) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            setupRecyclerView()
            setupStatCards()

            binding.btnScan.setOnClickListener { startScan() }
            binding.btnToggleSafe.setOnClickListener { toggleSafeApps() }
            
            loadCachedResults()
        } catch (e: Exception) {
            Log.e("AegisAI", "Critical failure in onCreate", e)
        }
    }

    private fun setupRecyclerView() {
        adapter = AppRiskAdapter(emptyList()) { item ->
            handleUninstall(item)
        }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter
    }

    private fun handleUninstall(item: ScanResult) {
        if (item.packageName.startsWith("demo.sandbox")) {
            Toast.makeText(this, getString(R.string.removed_simulated, item.appName), Toast.LENGTH_SHORT).show()
            allScanResults = allScanResults.filter { it.packageName != item.packageName }
            cacheResults(allScanResults)
            displayFinalResults()
        } else {
            try {
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = "package:${item.packageName}".toUri()
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.uninstall_error, item.appName), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupStatCards() {
        binding.statTotal.tvLabel.text = getString(R.string.stat_total_label)
        binding.statThreats.tvLabel.text = getString(R.string.stat_threats_label)
        binding.statLinks.tvLabel.text = getString(R.string.stat_safe_label)
    }

    private fun toggleSafeApps() {
        isShowingSafe = !isShowingSafe
        binding.btnToggleSafe.text = if (isShowingSafe) {
            getString(R.string.hide_safe)
        } else {
            getString(R.string.show_verified)
        }
        updateVisibleResults()
    }

    private fun updateVisibleResults() {
        val filtered = if (isShowingSafe) {
            allScanResults
        } else {
            allScanResults.filter { it.riskLevel.lowercase(Locale.ROOT) != "safe" }
        }
        adapter.updateData(filtered)
    }

    private fun startScan() {
        binding.btnScan.isEnabled = false
        binding.tvStatus.text = getString(R.string.scan_analyzing)
        binding.scanProgress.progress = 0
        binding.layoutSummary.visibility = View.GONE
        binding.resultsHeaderLayout.visibility = View.GONE
        adapter.updateData(emptyList())

        lifecycleScope.launch {
            val scanResults = withContext(Dispatchers.Default) {
                val installedApps = try {
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                } catch (_: Exception) {
                    emptyList()
                }

                val totalApps = installedApps.size
                val riskMetadata = loadRiskMetadata()
                val results = mutableListOf<ScanResult>()

                installedApps.forEachIndexed { index, app ->
                    try {
                        val progress = if (totalApps > 0) ((index + 1) * 100 / totalApps) else 0
                        val appLabel = packageManager.getApplicationLabel(app).toString()
                        
                        if (index % 5 == 0 || index == totalApps - 1) {
                            withContext(Dispatchers.Main) {
                                binding.scanProgress.progress = progress
                                binding.tvAppCount.text = getString(R.string.analyzing_app, appLabel)
                            }
                        }
                        
                        val packageName = app.packageName
                        val match = riskMetadata.find { it.app_name.equals(appLabel, ignoreCase = true) }
                        
                        val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        // Check installer source (Official Store detection)
                        val installer = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                packageManager.getInstallSourceInfo(packageName).installingPackageName
                            } else {
                                @Suppress("DEPRECATION")
                                packageManager.getInstallerPackageName(packageName)
                            }
                        } catch (_: Exception) {
                            null
                        }

                        val isFromOfficialStore = installer in listOf(
                            "com.android.vending", // Play Store
                            "com.sec.android.app.samsungapps", // Samsung
                            "com.amazon.venezia", // Amazon
                            "com.xiaomi.mipicks", // Xiaomi
                            "com.huawei.appmarket", // Huawei
                            "com.oppo.market" // Oppo
                        )

                        // Broad check for trusted publishers
                        val isTrustedPublisher = packageName.startsWith("com.google.") || 
                                                 packageName.startsWith("com.android.") ||
                                                 packageName.startsWith("com.samsung.")

                        if (match != null) {
                            results.add(ScanResult(
                                appLabel, packageName, match.risk_level, 
                                if (match.risk_level.lowercase() == "high") 90 else 50,
                                match.scam_type, match.description, 
                                match.average_rating, match.reviews,
                                isExpanded = false,
                                geminiExplanation = match.gemini_explanation
                            ))
                        } else if (isSystemApp || isFromOfficialStore || isTrustedPublisher) {
                            results.add(ScanResult(
                                appLabel, packageName, "Safe", if (isSystemApp) 0 else 5,
                                getString(R.string.verified_safe_type), 
                                if (isSystemApp) getString(R.string.system_comp_desc) else getString(R.string.play_store_desc),
                                5.0, emptyList()
                            ))
                        } else {
                            // Only flag unverified sideloaded apps as Moderate
                            results.add(ScanResult(
                                appLabel, packageName, "Moderate", 40,
                                getString(R.string.sideloaded_type), getString(R.string.sideloaded_desc),
                                3.0, emptyList()
                            ))
                        }
                    } catch (_: Exception) {}
                }

                val containsRealHighRisk = results.any { it.riskLevel.lowercase(Locale.ROOT) == "high" }
                if (!containsRealHighRisk) {
                    riskMetadata.filter { it.risk_level.lowercase(Locale.ROOT) == "high" || it.risk_level.lowercase(Locale.ROOT) == "moderate" }
                        .take(3)
                        .forEachIndexed { i, match ->
                            results.add(ScanResult(
                                appName = match.app_name + " " + getString(R.string.simulated_threat_suffix),
                                packageName = "demo.sandbox.${match.app_name.lowercase(Locale.ROOT).replace(" ", "")}",
                                riskLevel = match.risk_level,
                                riskScore = if (match.risk_level.lowercase(Locale.ROOT) == "high") 95 - (i * 2) else 55,
                                scamType = match.scam_type,
                                description = match.description + " " + getString(R.string.simulated_description_suffix),
                                averageRating = match.average_rating,
                                reviews = match.reviews,
                                isExpanded = false,
                                geminiExplanation = match.gemini_explanation ?: getString(R.string.demo_gemini_explanation)
                            ))
                        }
                }

                results
            }

            allScanResults = scanResults.sortedByDescending { it.riskScore }
            cacheResults(allScanResults)
            displayFinalResults()
        }
    }

    private fun displayFinalResults() {
        try {
            val riskyCount = allScanResults.count { it.riskLevel.lowercase(Locale.ROOT) != "safe" }
            
            binding.statTotal.tvValue.text = allScanResults.size.toString()
            binding.statThreats.tvValue.text = riskyCount.toString()
            binding.statLinks.tvValue.text = (allScanResults.size - riskyCount).toString()
            binding.layoutSummary.visibility = View.VISIBLE

            if (riskyCount > 0) {
                binding.tvStatus.text = getString(R.string.status_risks_detected, riskyCount)
                binding.statusIcon.setImageResource(android.R.drawable.stat_notify_error)
                binding.statusIcon.imageTintList = android.content.res.ColorStateList.valueOf("#FF5252".toColorInt())
                binding.tvAppCount.text = getString(R.string.review_report)
            } else {
                binding.tvStatus.text = getString(R.string.status_secure)
                binding.statusIcon.setImageResource(android.R.drawable.ic_lock_idle_lock)
                binding.statusIcon.imageTintList = android.content.res.ColorStateList.valueOf("#00E676".toColorInt())
                binding.tvAppCount.text = getString(R.string.no_threats_found)
            }

            binding.resultsHeaderLayout.visibility = View.VISIBLE
            binding.btnScan.isEnabled = true
            binding.btnScan.text = getString(R.string.scan_rescan)
            
            updateVisibleResults()
        } catch (_: Exception) {}
    }

    private fun cacheResults(results: List<ScanResult>) {
        try {
            getSharedPreferences("aegis_cache", Context.MODE_PRIVATE).edit {
                putString("last_scan", Gson().toJson(results))
            }
        } catch (_: Exception) {}
    }

    private fun loadCachedResults() {
        try {
            val json = getSharedPreferences("aegis_cache", Context.MODE_PRIVATE).getString("last_scan", null)
            if (json != null) {
                val listType = object : TypeToken<List<ScanResult>>() {}.type
                allScanResults = Gson().fromJson(json, listType)
                displayFinalResults()
            }
        } catch (_: Exception) {}
    }

    private suspend fun loadRiskMetadata(): List<AppRisk> = withContext(Dispatchers.IO) {
        try {
            val jsonString = assets.open("app_risk_metadata.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<AppRiskMetadata>() {}.type
            val metadata: AppRiskMetadata = Gson().fromJson(jsonString, type)
            metadata.apps
        } catch (_: Exception) {
            emptyList()
        }
    }
}
