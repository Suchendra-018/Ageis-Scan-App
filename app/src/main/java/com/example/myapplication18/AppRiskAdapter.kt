package com.example.myapplication18

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
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
            tvRating.text = context.getString(R.string.rating_format, item.averageRating)
            riskMeter.progress = item.riskScore

            if (item.reviews.isNotEmpty()) {
                val reviewText = item.reviews.joinToString("\n\n") { 
                    context.getString(R.string.review_item_format, it.user, it.stars, it.comment)
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
            when (risk) {
                "safe" -> {
                    val green = "#00E676".toColorInt()
                    tvRiskLevel.text = context.getString(R.string.risk_safe)
                    tvRiskLevel.setTextColor(green)
                    tvRiskLevel.backgroundTintList = ColorStateList.valueOf("#152C22".toColorInt())
                    riskMeter.setIndicatorColor(green)
                    btnUninstall.visibility = View.GONE
                }
                "moderate" -> {
                    val orange = "#FFD600".toColorInt()
                    tvRiskLevel.text = context.getString(R.string.risk_moderate)
                    tvRiskLevel.setTextColor(orange)
                    tvRiskLevel.backgroundTintList = ColorStateList.valueOf("#2C2A15".toColorInt())
                    riskMeter.setIndicatorColor(orange)
                    btnUninstall.visibility = View.GONE
                }
                "high" -> {
                    val red = "#FF5252".toColorInt()
                    tvRiskLevel.text = context.getString(R.string.risk_high)
                    tvRiskLevel.setTextColor(red)
                    tvRiskLevel.backgroundTintList = ColorStateList.valueOf("#2C1515".toColorInt())
                    riskMeter.setIndicatorColor(red)
                    btnUninstall.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
                }
            }

            expandableLayout.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            ivExpand.rotation = if (item.isExpanded) 180f else 0f
            tvGeminiExplanation.text = item.geminiExplanation ?: context.getString(R.string.no_insights)

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
