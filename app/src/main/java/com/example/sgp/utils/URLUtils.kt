package com.example.sgp.utils

import com.example.sgp.ml.MessageURLAnalysis
import com.example.sgp.ml.URLThreatResult
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for URL-related operations and formatting
 */
object URLUtils {

    /**
     * Format URL threat information for display in UI
     */
    fun formatURLThreatSummary(urlAnalysis: MessageURLAnalysis): String {
        if (urlAnalysis.totalUrls == 0) return ""

        val maliciousUrls = urlAnalysis.urlResults.filter { it.isThreat }
        return if (maliciousUrls.isNotEmpty()) {
            "${maliciousUrls.size}/${urlAnalysis.totalUrls} malicious URLs detected"
        } else {
            "${urlAnalysis.totalUrls} URL(s) - All appear safe"
        }
    }

    /**
     * Get detailed URL threat information for display
     */
    fun getDetailedURLThreatInfo(urlAnalysis: MessageURLAnalysis): String {
        if (urlAnalysis.totalUrls == 0) return "No URLs detected"

        val stringBuilder = StringBuilder()
        stringBuilder.append("URL Analysis Results:\n")
        stringBuilder.append("Total URLs: ${urlAnalysis.totalUrls}\n")
        stringBuilder.append("Malicious URLs: ${urlAnalysis.threatfulUrls}\n")
        stringBuilder.append("Overall Threat Level: ${urlAnalysis.overallThreatLevel}\n\n")

        urlAnalysis.urlResults.forEach { urlResult ->
            stringBuilder.append("URL: ${urlResult.url}\n")
            stringBuilder.append("Domain: ${urlResult.domain}\n")
            stringBuilder.append("Threat Score: ${(urlResult.threatScore * 100).toInt()}%\n")
            stringBuilder.append("Is Threat: ${if (urlResult.isThreat) "YES" else "NO"}\n")

            if (urlResult.threats.isNotEmpty()) {
                stringBuilder.append("Threats Found:\n")
                urlResult.threats.forEach { threat ->
                    stringBuilder.append("  • $threat\n")
                }
            }
            stringBuilder.append("\n")
        }

        return stringBuilder.toString()
    }

    /**
     * Extract domain from URL safely
     */
    fun extractDomain(url: String): String {
        return try {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "http://$url"
            } else {
                url
            }
            java.net.URL(cleanUrl).host ?: url
        } catch (e: Exception) {
            url
        }
    }

    /**
     * Check if a string contains any URLs
     */
    fun containsURL(text: String): Boolean {
        val urlPattern = "(?i)\\b(?:https?://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
        return text.contains(Regex(urlPattern))
    }

    /**
     * Sanitize URL for safe display (truncate if too long)
     */
    fun sanitizeURLForDisplay(url: String, maxLength: Int = 50): String {
        return if (url.length > maxLength) {
            "${url.take(maxLength)}..."
        } else {
            url
        }
    }

    /**
     * Get threat level text for display (no emojis)
     */
    fun getThreatLevelText(threatLevel: com.example.sgp.ml.ThreatLevel): String {
        return when (threatLevel) {
            com.example.sgp.ml.ThreatLevel.HIGH -> "HIGH"
            com.example.sgp.ml.ThreatLevel.MEDIUM -> "MEDIUM"
            com.example.sgp.ml.ThreatLevel.LOW -> "LOW"
        }
    }

    /**
     * Format timestamp for threat display
     */
    fun formatThreatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    /**
     * Generate a summary of all threats found in a message (no emojis)
     */
    fun generateThreatSummary(
        threatType: String,
        confidence: Float,
        urlAnalysis: MessageURLAnalysis?,
        explanation: String
    ): String {
        val confidencePercent = (confidence * 100).toInt()

        val urlInfo = if (urlAnalysis != null && urlAnalysis.totalUrls > 0) {
            if (urlAnalysis.threatfulUrls > 0) {
                " + ${urlAnalysis.threatfulUrls} malicious URL(s)"
            } else {
                " (${urlAnalysis.totalUrls} safe URLs)"
            }
        } else {
            ""
        }

        return "${threatType.uppercase()} ($confidencePercent%)$urlInfo"
    }
}
