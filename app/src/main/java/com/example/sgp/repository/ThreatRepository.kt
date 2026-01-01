package com.example.sgp.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.sgp.model.ThreatItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThreatRepository {

    private val _threatItems = MutableLiveData<MutableList<ThreatItem>>(mutableListOf())
    val threatItems: LiveData<MutableList<ThreatItem>> = _threatItems

    private val _totalScannedMessages = MutableLiveData<Int>(0)
    val totalScannedMessages: LiveData<Int> = _totalScannedMessages

    companion object {
        @Volatile
        private var INSTANCE: ThreatRepository? = null

        fun getInstance(): ThreatRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThreatRepository().also { INSTANCE = it }
            }
        }
    }

    init {
        // Load demo threats initially and set initial scan count
        loadDemoThreats()
        // Set initial scan count to reflect some demo scanning activity
        _totalScannedMessages.value = 47 // 3 demo threats + 44 safe messages scanned
    }

    suspend fun addThreat(threat: ThreatItem) {
        withContext(Dispatchers.IO) {
            val currentList = _threatItems.value ?: mutableListOf()
            currentList.add(0, threat) // Add to beginning for newest first
            _threatItems.postValue(currentList)
        }
    }

    suspend fun clearAllThreats() {
        withContext(Dispatchers.IO) {
            _threatItems.postValue(mutableListOf())
            _totalScannedMessages.postValue(0) // Also reset the scanned count
        }
    }

    suspend fun removeThreat(threat: ThreatItem) {
        withContext(Dispatchers.IO) {
            val currentList = _threatItems.value ?: mutableListOf()
            currentList.remove(threat)
            _threatItems.postValue(currentList)
        }
    }

    fun getThreatCount(): Int {
        return _threatItems.value?.size ?: 0
    }

    fun getTotalScannedCount(): Int {
        return _totalScannedMessages.value ?: 0
    }

    suspend fun incrementScannedCount() {
        withContext(Dispatchers.IO) {
            val currentCount = _totalScannedMessages.value ?: 0
            _totalScannedMessages.postValue(currentCount + 1)
        }
    }

    suspend fun resetCounts() {
        withContext(Dispatchers.IO) {
            _totalScannedMessages.postValue(0)
            _threatItems.postValue(mutableListOf())
        }
    }

    private fun loadDemoThreats() {
        val demoThreats = mutableListOf(
            ThreatItem(
                id = 1L,
                messageContent = "Congratulations! You've won $1,000,000! Click here to claim your prize now! Limited time offer! Visit: http://fake-lottery-winner.tk/claim",
                sender = "+1-555-SCAM",
                sourceApp = "SMS",
                threatType = "PHISHING",
                confidenceScore = 95,
                timestamp = System.currentTimeMillis(),
                explanation = "Detected lottery scam with malicious URL",
                maliciousUrls = listOf("http://fake-lottery-winner.tk/claim"),
                urlThreatCount = 1
            ),
            ThreatItem(
                id = 2L,
                messageContent = "Your bank account has been suspended. Please verify your identity immediately by clicking this link: https://secure-bank-update.com/verify-account",
                sender = "fake-bank@scam.com",
                sourceApp = "WhatsApp",
                threatType = "PHISHING",
                confidenceScore = 89,
                timestamp = System.currentTimeMillis() - 2 * 60 * 60 * 1000, // 2 hours ago
                explanation = "Bank impersonation phishing with suspicious domain",
                maliciousUrls = listOf("https://secure-bank-update.com/verify-account"),
                urlThreatCount = 1
            ),
            ThreatItem(
                id = 3L,
                messageContent = "URGENT: Your account will be deleted in 24 hours. Verify now to prevent loss of data! Click: http://192.168.1.100/urgent-verify",
                sender = "security-alert@fake.com",
                sourceApp = "Gmail",
                threatType = "PHISHING",
                confidenceScore = 78,
                timestamp = System.currentTimeMillis() - 24 * 60 * 60 * 1000, // 1 day ago
                explanation = "Account deletion threat with IP-based hosting",
                maliciousUrls = listOf("http://192.168.1.100/urgent-verify"),
                urlThreatCount = 1
            )
        )
        _threatItems.postValue(demoThreats)
    }
}
