package com.example.myapplication18

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication18.databinding.ItemAppRiskBinding
import java.util.Locale

class AppRiskAdapter(
    private var items: List<ScanResult>,
    private val onUninstallClick: (ScanResult) -> Unit
) : RecyclerView.Adapter<AppRiskAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAppRiskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppRiskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        with(holder.binding) {
            tvAppName.text = item.appName
            tvScamType.text = item.scamType
            tvDescription.text = item.description
            tvRating.text = context.getString(R.string.rating_format, item.averageRating)
            riskMeter.progress = item.riskScore

            // Format reviews
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

            // Load app icon
            try {
                val icon = context.packageManager.getApplicationIcon(item.packageName)
                ivAppIcon.setImageDrawable(icon)
            } catch (_: Exception) {
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            // Set colors based on risk
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

            // Expansion logic
            expandableLayout.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            ivExpand.rotation = if (item.isExpanded) 180f else 0f
            
            // AI insight
            tvGeminiExplanation.text = item.geminiExplanation ?: context.getString(R.string.no_insights)

            holder.itemView.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val clickedItem = items[currentPos]
                    clickedItem.isExpanded = !clickedItem.isExpanded
                    notifyItemChanged(currentPos)
                }
            }

            btnUninstall.setOnClickListener {
                onUninstallClick(item)
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ScanResult>) {
        items = newItems
        notifyDataSetChanged()
    }
}
