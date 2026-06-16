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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppRiskAdapter
    private var allScanResults: List<ScanResult> = emptyList()
    private var isShowingSafe = false
    private var updateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // MUST call enableEdgeToEdge() first for Android 15/16 compatibility
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Apply Window Insets for Edge-to-Edge
            ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinator) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                // Only pad the bottom and sides; let AppBarLayout handle the top
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
                insets
            }

            setupRecyclerView()
            setupStatCards()

            binding.btnScan.setOnClickListener { startScan() }
            binding.btnToggleSafe.setOnClickListener { toggleSafeApps() }
            
            binding.statLinks.root.setOnClickListener { if (!isShowingSafe) toggleSafeApps() }
            binding.statThreats.root.setOnClickListener { if (isShowingSafe) toggleSafeApps() }
            
            loadCachedResults()
        } catch (e: Exception) {
            Log.e("AegisAI", "Fatal startup error", e)
        }
    }

    private fun setupRecyclerView() {
        adapter = AppRiskAdapter { item -> handleUninstall(item) }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter
    }

    private fun handleUninstall(item: ScanResult) {
        if (item.packageName.startsWith("demo.sandbox")) {
            Toast.makeText(this, getString(R.string.removed_simulated, item.appName), Toast.LENGTH_SHORT).show()
            allScanResults = allScanResults.filter { it.packageName != item.packageName }
            lifecycleScope.launch {
                cacheResults(allScanResults)
                displayFinalResults()
            }
        } else {
            try {
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = "package:${item.packageName}".toUri()
                }
                startActivity(intent)
            } catch (e: Exception) {
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
        binding.btnToggleSafe.text = if (isShowingSafe) getString(R.string.hide_safe) else getString(R.string.show_verified)
        updateVisibleResults()
    }

    private fun updateVisibleResults() {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                if (isShowingSafe) allScanResults 
                else allScanResults.filter { it.riskLevel.lowercase(Locale.ROOT) != "safe" }
            }
            adapter.submitList(filtered)
        }
    }

    private fun startScan() {
        binding.btnScan.isEnabled = false
        binding.tvStatus.text = getString(R.string.scan_analyzing)
        binding.scanProgress.progress = 0
        binding.layoutSummary.visibility = View.GONE
        binding.resultsHeaderLayout.visibility = View.GONE

        lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val installedApps = try {
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                } catch (e: Exception) { emptyList() }
                
                val riskMetadata = loadRiskMetadata()
                val scanResults = mutableListOf<ScanResult>()

                installedApps.forEachIndexed { index, app ->
                    val appLabel = packageManager.getApplicationLabel(app).toString()
                    val packageName = app.packageName
                    
                    withContext(Dispatchers.Main) {
                        binding.scanProgress.progress = if (installedApps.isNotEmpty()) ((index + 1) * 100 / installedApps.size) else 0
                        binding.tvAppCount.text = getString(R.string.analyzing_app, appLabel)
                    }

                    val match = riskMetadata.find { it.app_name.equals(appLabel, ignoreCase = true) }
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    if (match != null) {
                        scanResults.add(ScanResult(appLabel, packageName, match.risk_level, 
                            if (match.risk_level.lowercase() == "high") 90 else 50,
                            match.scam_type, match.description, match.average_rating, emptyList()))
                    } else {
                        scanResults.add(ScanResult(appLabel, packageName, "Safe", if (isSystem) 0 else 5,
                            getString(R.string.verified_safe_type), getString(R.string.play_store_desc), 5.0, emptyList()))
                    }
                }
                scanResults.sortedByDescending { it.riskScore }
            }

            allScanResults = results
            cacheResults(allScanResults)
            displayFinalResults()
        }
    }

    private fun displayFinalResults() {
        val riskyCount = allScanResults.count { it.riskLevel.lowercase(Locale.ROOT) != "safe" }
        binding.statTotal.tvValue.text = allScanResults.size.toString()
        binding.statThreats.tvValue.text = riskyCount.toString()
        binding.statLinks.tvValue.text = (allScanResults.size - riskyCount).toString()
        binding.layoutSummary.visibility = View.VISIBLE
        binding.resultsHeaderLayout.visibility = View.VISIBLE
        binding.btnScan.isEnabled = true
        
        binding.tvStatus.text = if (riskyCount > 0) getString(R.string.status_risks_detected, riskyCount) else getString(R.string.status_secure)
        
        updateVisibleResults()
    }

    private suspend fun cacheResults(results: List<ScanResult>) = withContext(Dispatchers.IO) {
        val json = Gson().toJson(results)
        getSharedPreferences("aegis_cache", Context.MODE_PRIVATE).edit {
            putString("last_scan", json)
        }
    }

    private fun loadCachedResults() {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                getSharedPreferences("aegis_cache", Context.MODE_PRIVATE).getString("last_scan", null)
            }
            if (json != null) {
                try {
                    val listType = object : TypeToken<List<ScanResult>>() {}.type
                    allScanResults = Gson().fromJson(json, listType)
                    displayFinalResults()
                } catch (e: Exception) { Log.e("AegisAI", "Cache load failed", e) }
            }
        }
    }

    private suspend fun loadRiskMetadata(): List<AppRisk> = withContext(Dispatchers.IO) {
        try {
            val jsonString = assets.open("app_risk_metadata.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<AppRiskMetadata>() {}.type
            val metadata: AppRiskMetadata = Gson().fromJson(jsonString, type)
            metadata.apps
        } catch (e: Exception) { emptyList() }
    }
}
