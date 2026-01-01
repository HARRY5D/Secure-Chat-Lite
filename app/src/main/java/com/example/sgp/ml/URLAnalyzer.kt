package com.example.sgp.ml

import android.util.Log
import java.net.URL
import java.util.regex.Pattern

/**
 * Advanced URL scanner and threat analyzer for detecting malicious links in messages.
 * Provides offline analysis using pattern matching, domain reputation, and phishing detection.
 */
class URLAnalyzer {

    companion object {
        private const val TAG = "URLAnalyzer"

        // Known malicious domains and patterns (offline threat intelligence)
        private val MALICIOUS_DOMAINS = setOf(
            "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "t.co", // Suspicious shorteners
            "secure-bank-update.com", "paypal-security.net", "amazon-verification.org",
            "microsoft-security.info", "google-security.net", "facebook-security.co",
            "instagram-verify.net", "whatsapp-verify.com", "telegram-security.org"
        )

        // Suspicious TLD patterns
        private val SUSPICIOUS_TLDS = setOf(
            ".tk", ".ml", ".ga", ".cf", ".top", ".work", ".click", ".download",
            ".stream", ".science", ".review", ".date", ".racing", ".accountant"
        )

        // Phishing keywords that commonly appear in malicious URLs
        private val PHISHING_KEYWORDS = listOf(
            "verify", "secure", "update", "confirm", "suspended", "locked",
            "security", "urgent", "immediate", "account", "login", "signin",
            "banking", "paypal", "amazon", "microsoft", "google", "apple",
            "facebook", "instagram", "whatsapp", "telegram"
        )

        // URL patterns for extracting links from text
        private val URL_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
            Pattern.CASE_INSENSITIVE
        )

        // IP address pattern (often used in phishing)
        private val IP_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
        )

        // Suspicious URL patterns
        private val SUSPICIOUS_PATTERNS = listOf(
            Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"), // IP addresses
            Pattern.compile("[a-z0-9]{20,}\\.[a-z]{2,}"), // Long random subdomains
            Pattern.compile("([a-z]+-){3,}[a-z]+\\.[a-z]{2,}"), // Multiple hyphens
            Pattern.compile("\\w*secure\\w*|\\w*bank\\w*|\\w*verify\\w*", Pattern.CASE_INSENSITIVE) // Phishing keywords in domain
        )
    }

    /**
     * Extract all URLs from a given text message
     */
    fun extractURLs(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = URL_PATTERN.matcher(text)

        while (matcher.find()) {
            var url = matcher.group()
            // Ensure URL has protocol
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            urls.add(url)
        }

        Log.d(TAG, "Extracted ${urls.size} URLs from message: $urls")
        return urls
    }

    /**
     * Analyze a single URL for threats and return detailed results
     */
    fun analyzeURL(url: String): URLThreatResult {
        Log.d(TAG, "Analyzing URL: $url")

        try {
            val cleanedUrl = cleanURL(url)
            val urlObj = URL(cleanedUrl)
            val domain = urlObj.host?.lowercase() ?: ""

            var threatScore = 0.0f
            val threats = mutableListOf<String>()
            val details = mutableMapOf<String, Any>()

            // Analysis 1: Known malicious domains
            if (MALICIOUS_DOMAINS.contains(domain)) {
                threatScore += 0.8f
                threats.add("Known malicious domain")
                details["malicious_domain"] = true
            }

            // Analysis 2: Suspicious TLDs
            val tld = domain.substringAfterLast(".")
            if (SUSPICIOUS_TLDS.contains(".$tld")) {
                threatScore += 0.4f
                threats.add("Suspicious top-level domain (.$tld)")
                details["suspicious_tld"] = tld
            }

            // Analysis 3: IP address hosting
            if (IP_PATTERN.matcher(domain).matches()) {
                threatScore += 0.6f
                threats.add("Hosted on IP address instead of domain")
                details["ip_hosting"] = true
            }

            // Analysis 4: Phishing keywords in domain
            val phishingKeywords = PHISHING_KEYWORDS.filter { domain.contains(it) }
            if (phishingKeywords.isNotEmpty()) {
                threatScore += 0.3f * phishingKeywords.size
                threats.add("Contains phishing keywords: ${phishingKeywords.joinToString(", ")}")
                details["phishing_keywords"] = phishingKeywords
            }

            // Analysis 5: Suspicious patterns
            for (pattern in SUSPICIOUS_PATTERNS) {
                if (pattern.matcher(domain).find()) {
                    threatScore += 0.3f
                    threats.add("Matches suspicious domain pattern")
                    details["suspicious_pattern"] = true
                    break
                }
            }

            // Analysis 6: URL shortener detection
            if (isURLShortener(domain)) {
                threatScore += 0.5f
                threats.add("URL shortener detected - may hide destination")
                details["url_shortener"] = true
            }

            // Analysis 7: Homograph attack detection
            if (containsHomographs(domain)) {
                threatScore += 0.7f
                threats.add("Contains homograph characters (possible spoofing)")
                details["homograph_attack"] = true
            }

            // Analysis 8: Domain length and complexity
            if (domain.length > 50) {
                threatScore += 0.2f
                threats.add("Unusually long domain name")
                details["long_domain"] = true
            }

            // Analysis 9: Check for typosquatting of popular domains
            val typosquattingResult = checkTyposquatting(domain)
            if (typosquattingResult.isNotEmpty()) {
                threatScore += 0.6f
                threats.add("Possible typosquatting of: $typosquattingResult")
                details["typosquatting_target"] = typosquattingResult
            }

            // Normalize threat score (max 1.0)
            threatScore = threatScore.coerceAtMost(1.0f)

            val isThreat = threatScore > 0.4f
            val threatLevel = when {
                threatScore > 0.8f -> ThreatLevel.HIGH
                threatScore > 0.5f -> ThreatLevel.MEDIUM
                else -> ThreatLevel.LOW
            }

            Log.d(TAG, "URL analysis complete - Threat: $isThreat, Score: $threatScore, Level: $threatLevel")

            return URLThreatResult(
                url = cleanedUrl,
                domain = domain,
                isThreat = isThreat,
                threatScore = threatScore,
                threatLevel = threatLevel,
                threats = threats,
                details = details
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing URL: $url", e)
            return URLThreatResult(
                url = url,
                domain = "unknown",
                isThreat = true,
                threatScore = 0.5f,
                threatLevel = ThreatLevel.MEDIUM,
                threats = listOf("Invalid URL format"),
                details = mapOf("error" to e.message.orEmpty())
            )
        }
    }

    /**
     * Analyze all URLs in a message text
     */
    fun analyzeMessageURLs(messageText: String): MessageURLAnalysis {
        val urls = extractURLs(messageText)
        val urlResults = urls.map { analyzeURL(it) }

        val threatfulUrls = urlResults.filter { it.isThreat }
        val maxThreatScore = urlResults.maxOfOrNull { it.threatScore } ?: 0.0f

        val overallThreatLevel = when {
            maxThreatScore > 0.8f -> ThreatLevel.HIGH
            maxThreatScore > 0.5f -> ThreatLevel.MEDIUM
            else -> ThreatLevel.LOW
        }

        Log.d(TAG, "Message URL analysis: ${urls.size} URLs, ${threatfulUrls.size} threats, max score: $maxThreatScore")

        return MessageURLAnalysis(
            totalUrls = urls.size,
            threatfulUrls = threatfulUrls.size,
            urlResults = urlResults,
            overallThreatLevel = overallThreatLevel,
            maxThreatScore = maxThreatScore
        )
    }

    private fun cleanURL(url: String): String {
        var cleaned = url.trim()
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            cleaned = "http://$cleaned"
        }
        return cleaned
    }

    private fun isURLShortener(domain: String): Boolean {
        val shorteners = setOf(
            "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "t.co", "short.link",
            "tiny.cc", "is.gd", "buff.ly", "rebrand.ly", "cutt.ly"
        )
        return shorteners.contains(domain)
    }

    private fun containsHomographs(domain: String): Boolean {
        // Check for common homograph attacks (Cyrillic, Greek characters in Latin domains)
        val homographPatterns = listOf(
            "[а-яё]", // Cyrillic
            "[α-ωΑ-Ω]", // Greek
            "[０-９]", // Fullwidth digits
            "[ａ-ｚＡ-Ｚ]" // Fullwidth Latin
        )

        return homographPatterns.any { pattern ->
            Pattern.compile(pattern).matcher(domain).find()
        }
    }

    private fun checkTyposquatting(domain: String): String {
        val popularDomains = mapOf(
            "google.com" to listOf("gooogle.com", "googlee.com", "goolge.com", "goggle.com"),
            "facebook.com" to listOf("facebok.com", "faceboook.com", "facebokk.com"),
            "amazon.com" to listOf("amason.com", "amazom.com", "amazone.com"),
            "paypal.com" to listOf("paypaI.com", "payp4l.com", "paypayl.com", "paipal.com"),
            "microsoft.com" to listOf("mircosoft.com", "microsooft.com", "microsft.com"),
            "apple.com" to listOf("aple.com", "appIe.com", "aplle.com"),
            "instagram.com" to listOf("instgram.com", "instragram.com", "instagra.com"),
            "whatsapp.com" to listOf("whatsap.com", "whatsaap.com", "whatssapp.com"),
            "telegram.org" to listOf("telegram.com", "telegrame.org", "telegrm.org")
        )

        for ((legitimate, typos) in popularDomains) {
            if (typos.contains(domain) || isTypoOf(domain, legitimate)) {
                return legitimate
            }
        }

        return ""
    }

    private fun isTypoOf(candidate: String, legitimate: String): Boolean {
        // Simple Levenshtein distance check for typosquatting
        return levenshteinDistance(candidate, legitimate) <= 2 && candidate != legitimate
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1.length < s2.length) return levenshteinDistance(s2, s1)
        if (s2.isEmpty()) return s1.length

        var previousRow = IntArray(s2.length + 1) { it }

        for (i in s1.indices) {
            val currentRow = IntArray(s2.length + 1)
            currentRow[0] = i + 1

            for (j in s2.indices) {
                val cost = if (s1[i] == s2[j]) 0 else 1
                currentRow[j + 1] = minOf(
                    currentRow[j] + 1,
                    previousRow[j + 1] + 1,
                    previousRow[j] + cost
                )
            }
            previousRow = currentRow
        }

        return previousRow[s2.length]
    }
}

/**
 * Result of URL threat analysis
 */
data class URLThreatResult(
    val url: String,
    val domain: String,
    val isThreat: Boolean,
    val threatScore: Float,
    val threatLevel: ThreatLevel,
    val threats: List<String>,
    val details: Map<String, Any>
)

/**
 * Analysis result for all URLs in a message
 */
data class MessageURLAnalysis(
    val totalUrls: Int,
    val threatfulUrls: Int,
    val urlResults: List<URLThreatResult>,
    val overallThreatLevel: ThreatLevel,
    val maxThreatScore: Float
)

