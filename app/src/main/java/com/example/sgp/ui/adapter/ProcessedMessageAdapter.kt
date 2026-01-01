package com.example.sgp.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sgp.databinding.ItemProcessedMessageBinding
import com.example.sgp.model.ProcessedMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProcessedMessageAdapter : ListAdapter<ProcessedMessage, ProcessedMessageAdapter.ProcessedMessageViewHolder>(ProcessedMessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProcessedMessageViewHolder {
        val binding = ItemProcessedMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProcessedMessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProcessedMessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProcessedMessageViewHolder(private val binding: ItemProcessedMessageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProcessedMessage) {
            // Format timestamp
            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            val date = Date(item.timestamp)

            binding.apply {
                tvMessageTimestamp.text = dateFormat.format(date)
                tvSourceApp.text = item.sourceApp
                tvSender.text = if (item.sender.isNotEmpty()) "• ${item.sender}" else ""

                // Set threat status and color
                if (item.isThreat) {
                    tvThreatStatus.text = item.threatType
                    tvThreatStatus.setTextColor(binding.root.context.getColor(android.R.color.holo_red_light))
                    tvConfidenceScore.text = "${item.confidenceScore}%"
                    progressConfidence.progress = item.confidenceScore
                } else {
                    tvThreatStatus.text = "SAFE"
                    tvThreatStatus.setTextColor(binding.root.context.getColor(android.R.color.holo_green_light))
                    tvConfidenceScore.text = "${item.confidenceScore}%"
                    progressConfidence.progress = item.confidenceScore
                }

                // Enhanced message content with compact URL info
                tvMessageContent.text = buildString {
                    append(item.messageContent.take(120))
                    if (item.messageContent.length > 120) append("...")

                    // Add URL information in a compact format
                    when {
                        item.urlThreatCount > 0 -> {
                            append("\n\n${item.urlThreatCount} Malicious URL(s):")
                            item.maliciousUrls.take(2).forEach { url -> // Limit to 2 URLs for UI
                                append("\n• ${if (url.length > 40) url.take(40) + "..." else url}")
                            }
                            if (item.maliciousUrls.size > 2) {
                                append("\n... and ${item.maliciousUrls.size - 2} more")
                            }
                        }
                        item.urlAnalysis != null && item.urlAnalysis.totalUrls > 0 -> {
                            append("\n\n${item.urlAnalysis.totalUrls} URL(s) - All Safe")
                        }
                    }
                }

                // Show explanation if available
                if (item.explanation.isNotEmpty()) {
                    tvThreatExplanation.text = item.explanation
                    tvThreatExplanation.visibility = android.view.View.VISIBLE
                } else {
                    tvThreatExplanation.visibility = android.view.View.GONE
                }
            }
        }
    }

    class ProcessedMessageDiffCallback : DiffUtil.ItemCallback<ProcessedMessage>() {
        override fun areItemsTheSame(oldItem: ProcessedMessage, newItem: ProcessedMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProcessedMessage, newItem: ProcessedMessage): Boolean {
            return oldItem == newItem
        }
    }
}
