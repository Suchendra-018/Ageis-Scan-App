package com.example.myapplication18

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication18.databinding.ItemAppRiskBinding
import kotlinx.coroutines.*
import java.util.Locale

class AppRiskAdapter(
    private val onUninstallClick: (ScanResult) -> Unit
) : ListAdapter<ScanResult, AppRiskAdapter.ViewHolder>(ScanDiffCallback()) {

    private val iconCache = mutableMapOf<String, Drawable>()
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    class ViewHolder(val binding: ItemAppRiskBinding) : RecyclerView.ViewHolder(binding.root) {
        var iconJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppRiskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvAppName.text = item.appName
            tvScamType.text = item.scamType
            tvDescription.text = item.description
            tvRating.text = "Rating: ${item.averageRating} ★"
            riskMeter.progress = item.riskScore

            if (item.reviews.isNotEmpty()) {
                val reviewText = item.reviews.joinToString("\n\n") { 
                    "${it.user} (${it.stars}★): ${it.comment}"
                }
                tvReviews.text = reviewText
                tvReviewsLabel.visibility = View.VISIBLE
                tvRating.visibility = View.VISIBLE
                tvReviews.visibility = View.VISIBLE
            } else {
                tvReviewsLabel.visibility = View.GONE
                tvRating.visibility = View.GONE
                tvReviews.visibility = View.GONE
            }

            // Load app icon asynchronously
            holder.iconJob?.cancel()
            val cachedIcon = iconCache[item.packageName]
            if (cachedIcon != null) {
                ivAppIcon.setImageDrawable(cachedIcon)
            } else {
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                holder.iconJob = adapterScope.launch {
                    val icon = withContext(Dispatchers.IO) {
                        try {
                            context.packageManager.getApplicationIcon(item.packageName)
                        } catch (_: Exception) { null }
                    }
                    if (icon != null) {
                        iconCache[item.packageName] = icon
                        ivAppIcon.setImageDrawable(icon)
                    }
                }
            }

            val risk = item.riskLevel.lowercase(Locale.ROOT)
            val highColor = ContextCompat.getColor(context, R.color.risk_high)
            val moderateColor = ContextCompat.getColor(context, R.color.risk_moderate)
            val safeColor = ContextCompat.getColor(context, R.color.risk_safe)

            when (risk) {
                "safe" -> {
                    tvRiskLevel.text = "SAFE"
                    tvRiskLevel.setTextColor(safeColor)
                    tvRiskLevel.backgroundTintList = ColorStateList.valueOf(safeColor).withAlpha(30)
                    riskMeter.setIndicatorColor(safeColor)
                    btnUninstall.visibility = View.GONE
                }
                "moderate" -> {
                    tvRiskLevel.text = "MODERATE"
                    tvRiskLevel.setTextColor(moderateColor)
                    tvRiskLevel.backgroundTintList = ColorStateList.valueOf(moderateColor).withAlpha(30)
                    riskMeter.setIndicatorColor(moderateColor)
                    btnUninstall.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
                }
                "high" -> {
                    tvRiskLevel.text = "HIGH RISK"
                    tvRiskLevel.setTextColor(highColor)
                    tvRiskLevel.backgroundTintList = ColorStateList.valueOf(highColor).withAlpha(30)
                    riskMeter.setIndicatorColor(highColor)
                    btnUninstall.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
                }
            }

            expandableLayout.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            ivExpand.rotation = if (item.isExpanded) 180f else 0f
            tvGeminiExplanation.text = item.geminiExplanation ?: "AegisAI analyzed this app and found no critical issues."

            holder.itemView.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val currentItem = getItem(currentPos)
                    currentItem.isExpanded = !currentItem.isExpanded
                    notifyItemChanged(currentPos)
                }
            }

            btnUninstall.setOnClickListener {
                onUninstallClick(item)
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
    }

    class ScanDiffCallback : DiffUtil.ItemCallback<ScanResult>() {
        override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult) = 
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult) = 
            oldItem == newItem && oldItem.isExpanded == newItem.isExpanded
    }
}
