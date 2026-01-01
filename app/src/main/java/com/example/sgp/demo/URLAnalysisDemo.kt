package com.example.sgp.demo

import android.util.Log
import com.example.sgp.ml.URLAnalyzer
import com.example.sgp.ml.MessageClassifier
import android.content.Context

/**
 * Demo class to test URL analysis functionality
 */
class URLAnalysisDemo(private val context: Context) {

    companion object {
        private const val TAG = "URLAnalysisDemo"
    }

    private val urlAnalyzer = URLAnalyzer()
    private val messageClassifier = MessageClassifier(context)

    /**
     * Run demo tests with various URL types
     */
    fun runURLAnalysisDemo() {
        Log.d(TAG, "=== Starting URL Analysis Demo ===")

        val testMessages = listOf(
            "Check out this great offer: http://secure-bank-update.com/verify-account",
            "Your account has been suspended! Verify now: https://paypal-security.net/urgent",
            "Win $1000000! Click here: http://bit.ly/fake-lottery",
            "Meeting link: https://zoom.us/j/1234567890",
            "Check this out: http://192.168.1.100/malicious-site",
            "Visit our legitimate site: https://www.google.com",
            "Suspicious link: http://gooogle.com/fake-search",
            "Normal message without any links",
            "Multiple URLs: https://facebook-security.co/hack and http://amazon-verification.org/scam"
        )

        testMessages.forEachIndexed { index, message ->
            Log.d(TAG, "\n--- Test Message ${index + 1} ---")
            Log.d(TAG, "Message: $message")

            // Test URL analysis
            val urlAnalysis = urlAnalyzer.analyzeMessageURLs(message)
            Log.d(TAG, "URLs found: ${urlAnalysis.totalUrls}")
            Log.d(TAG, "Malicious URLs: ${urlAnalysis.threatfulUrls}")
            Log.d(TAG, "Threat Level: ${urlAnalysis.overallThreatLevel}")

            if (urlAnalysis.totalUrls > 0) {
                urlAnalysis.urlResults.forEach { urlResult ->
                    Log.d(TAG, "URL: ${urlResult.url}")
                    Log.d(TAG, "  Domain: ${urlResult.domain}")
                    Log.d(TAG, "  Threat: ${urlResult.isThreat}")
                    Log.d(TAG, "  Score: ${(urlResult.threatScore * 100).toInt()}%")
                    Log.d(TAG, "  Threats: ${urlResult.threats}")
                }
            }

            // Test full message classification
            val classificationResult = messageClassifier.classifyMessage(message)
            Log.d(TAG, "Classification: ${classificationResult.threatType}")
            Log.d(TAG, "Confidence: ${(classificationResult.confidence * 100).toInt()}%")
            Log.d(TAG, "Explanation: ${classificationResult.explanation}")
        }

        Log.d(TAG, "=== URL Analysis Demo Complete ===")
    }

    /**
     * Test individual URL analysis
     */
    fun testIndividualURL(url: String) {
        Log.d(TAG, "=== Testing Individual URL ===")
        Log.d(TAG, "URL: $url")

        val result = urlAnalyzer.analyzeURL(url)
        Log.d(TAG, "Domain: ${result.domain}")
        Log.d(TAG, "Is Threat: ${result.isThreat}")
        Log.d(TAG, "Threat Score: ${(result.threatScore * 100).toInt()}%")
        Log.d(TAG, "Threat Level: ${result.threatLevel}")
        Log.d(TAG, "Threats Found: ${result.threats}")
        Log.d(TAG, "Details: ${result.details}")
    }

    fun cleanup() {
        messageClassifier.close()
    }
}
