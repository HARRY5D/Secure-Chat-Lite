package com.example.sgp.model

import com.example.sgp.ml.MessageClassifier

data class ProcessedMessage(
    val id: Long,
    val messageContent: String,
    val sender: String,
    val sourceApp: String,
    val isThreat: Boolean,
    val threatType: String,
    val confidenceScore: Int,
    val timestamp: Long,
    val explanation: String,
    val urlAnalysis: MessageClassifier.URLAnalysis? = null,
    val maliciousUrls: List<String> = emptyList(),
    val urlThreatCount: Int = 0
)
