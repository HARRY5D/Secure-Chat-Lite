package com.example.sgp.model

import com.example.sgp.ml.MessageURLAnalysis

data class ThreatItem(
    val id: Long,
    val messageContent: String,
    val sender: String,
    val sourceApp: String,
    val threatType: String,
    val confidenceScore: Int,
    val timestamp: Long,
    val explanation: String = "",
    val urlAnalysis: MessageURLAnalysis? = null,
    val maliciousUrls: List<String> = emptyList(),
    val urlThreatCount: Int = 0
)
