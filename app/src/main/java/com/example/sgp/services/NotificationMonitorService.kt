package com.example.sgp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sgp.MainActivity
import com.example.sgp.R
import com.example.sgp.ml.MessageClassifier
import kotlinx.coroutines.*

class NotificationMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationMonitor"
        private const val CHANNEL_ID = "security_alerts"
        private const val THREAT_CHANNEL_ID = "threat_alerts"
    }

    private val targetPackages = mapOf(
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "com.facebook.orca" to "Messenger",
        "com.google.android.apps.messaging" to "Messages",
        "com.android.mms" to "SMS",
        "com.samsung.android.messaging" to "Samsung Messages",
        "com.instagram.android" to "Instagram",
        "com.snapchat.android" to "Snapchat",
        "com.viber.voip" to "Viber",
        "com.skype.raider" to "Skype",
        "com.discord" to "Discord",
        "jp.naver.line.android" to "LINE"
    )

    private var messageClassifier: MessageClassifier? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isBroadcastReceiverRegistered = false

    // Track processed notifications to prevent duplicates
    private val processedNotifications = mutableSetOf<String>()

    private val scanBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.sgp.REFRESH_NOTIFICATION_SCAN") {
                Log.d(TAG, "Received manual scan request via broadcast")
                processNotificationHistory()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationMonitorService starting...")

        try {
            createNotificationChannels()
            initializeMessageClassifier()
            registerBroadcastReceiver()
            processNotificationHistory()
            Log.d(TAG, "NotificationMonitorService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NotificationMonitorService", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "NotificationMonitorService onBind called")
        return try {
            super.onBind(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onBind", e)
            null
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "NotificationMonitorService onUnbind called")
        return try {
            super.onUnbind(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onUnbind", e)
            false
        }
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "NotificationMonitorService onRebind called")
        try {
            super.onRebind(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onRebind", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListener connected successfully")
        processNotificationHistory()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListener disconnected")
    }

    private fun initializeMessageClassifier() {
        try {
            messageClassifier = MessageClassifier(applicationContext)
            Log.d(TAG, "Message classifier initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize message classifier", e)
        }
    }

    private fun processNotificationHistory() {
        try {
            val activeNotifications = getActiveNotifications()
            Log.d(TAG, "Processing ${activeNotifications.size} active notifications from history")

            for (notification in activeNotifications) {
                if (targetPackages.containsKey(notification.packageName)) {
                    val appName = targetPackages[notification.packageName] ?: "Unknown App"
                    Log.d(TAG, "Processing historical notification from $appName")
                    extractAndProcessMessage(notification, appName, true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process notification history", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        if (!targetPackages.containsKey(sbn.packageName)) {
            return
        }

        val appName = targetPackages[sbn.packageName] ?: "Unknown App"
        Log.d(TAG, "Processing notification from $appName (${sbn.packageName})")

        try {
            extractAndProcessMessage(sbn, appName, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification from $appName", e)
        }
    }

    private fun extractAndProcessMessage(sbn: StatusBarNotification, appName: String, isHistorical: Boolean = false) {
        val extras = sbn.notification?.extras ?: return

        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        val messageContent = when {
            bigText.isNotBlank() && bigText != text -> bigText
            text.isNotBlank() -> text
            title.isNotBlank() -> title
            else -> return
        }

        val sender = when {
            title.isNotBlank() && text.isNotBlank() -> title
            subText.isNotBlank() -> subText
            else -> appName
        }

        // Create a unique key for this notification to prevent duplicate processing
        val notificationKey = "${sbn.packageName}_${sender}_${messageContent.hashCode()}_${sbn.postTime}"

        // Check if we've already processed this notification
        if (processedNotifications.contains(notificationKey)) {
            Log.d(TAG, "Skipping duplicate notification from $sender via $appName")
            return
        }

        // Add to processed set
        processedNotifications.add(notificationKey)

        // Clean up old entries if the set gets too large
        if (processedNotifications.size > 1000) {
            val iterator = processedNotifications.iterator()
            repeat(200) { if (iterator.hasNext()) iterator.next(); iterator.remove() }
        }

        Log.d(TAG, "Message extracted - Sender: $sender, Content length: ${messageContent.length}")
        processMessageAsync(sender, messageContent, appName, sbn.packageName, isHistorical)
    }

    private fun processMessageAsync(sender: String, message: String, appName: String, packageName: String, isHistorical: Boolean = false) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Analyzing message from $sender via $appName: '${message.take(50)}${if (message.length > 50) "..." else ""}'")

                val result = messageClassifier?.classifyMessage(message) ?: performRuleBasedDetection(message)

                Log.d(TAG, "Classification result - Threat: ${result.isThreat}, Type: ${result.threatType}, Confidence: ${result.confidence}")

                // Increment total scanned count for every message processed
                val threatRepository = com.example.sgp.repository.ThreatRepository.getInstance()
                threatRepository.incrementScannedCount()

                if (result.isThreat) {
                    Log.w(TAG, " THREAT DETECTED: ${result.threatType.uppercase()} with ${(result.confidence * 100).toInt()}% confidence from $sender via $appName")

                    if (!isHistorical) {
                        withContext(Dispatchers.Main) {
                            sendSecurityAlert(sender, message, result, appName)
                        }
                    }
                    logThreatDetection(sender, message, result, appName, packageName, isHistorical)
                } else {
                    Log.d(TAG, " Message classified as safe: ${result.explanation}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message from $sender", e)
            }
        }
    }

    private fun performRuleBasedDetection(message: String): MessageClassifier.ClassificationResult {
        val lowerMessage = message.lowercase()

        // Simplified fallback detection when MessageClassifier fails
        val phishingKeywords = listOf(
            "click here", "verify account", "suspended", "urgent action required",
            "confirm identity", "update payment", "security alert", "account locked",
            "win prize", "congratulations", "claim reward", "limited time offer"
        )

        val spamKeywords = listOf(
            "make money fast", "work from home", "buy now", "limited offer",
            "call now", "free gift", "no obligation", "guarantee"
        )

        val abusiveKeywords = listOf(
            "hate", "kill", "die", "stupid", "idiot", "threat"
        )

        return when {
            phishingKeywords.any { lowerMessage.contains(it) } ->
                MessageClassifier.ClassificationResult(true, "phishing", 0.8f, "Fallback rule-based phishing detection", null)
            spamKeywords.any { lowerMessage.contains(it) } ->
                MessageClassifier.ClassificationResult(true, "spam", 0.75f, "Fallback rule-based spam detection", null)
            abusiveKeywords.any { lowerMessage.contains(it) } ->
                MessageClassifier.ClassificationResult(true, "abusive", 0.7f, "Fallback rule-based abuse detection", null)
            else ->
                MessageClassifier.ClassificationResult(false, "safe", 0.9f, "No threats detected - Fallback analysis", null)
        }
    }

    private fun sendSecurityAlert(
        sender: String,
        message: String,
        result: MessageClassifier.ClassificationResult,
        appName: String
    ) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("DETECTED_MESSAGE", message)
                putExtra("SENDER", sender)
                putExtra("THREAT_TYPE", result.threatType)
                putExtra("APP_NAME", appName)
                putExtra("CONFIDENCE", result.confidence)
                putExtra("EXPLANATION", result.explanation)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                this, System.currentTimeMillis().toInt(), intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val threatTypeDisplay = when (result.threatType) {
                "phishing" -> "Phishing Attempt"
                "spam" -> "Spam Message"
                "abusive" -> "Abusive Content"
                else -> "Security Threat"
            }

            val confidencePercent = (result.confidence * 100).toInt()

            // Build URL threat information - compact format (no emojis)
            val urlThreatInfo = if (result.urlAnalysis != null && result.urlAnalysis.totalUrls > 0) {
                val maliciousUrls = result.urlAnalysis.urlResults.filter { it.isThreat }
                if (maliciousUrls.isNotEmpty()) {
                    "\n${maliciousUrls.size}/${result.urlAnalysis.totalUrls} Malicious URLs\nWarning: Do not click links!"
                } else {
                    "\n${result.urlAnalysis.totalUrls} URL(s) - Safe"
                }
            } else {
                ""
            }

            val notification = NotificationCompat.Builder(this, THREAT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_security_warning)
                .setContentTitle("Security Alert - $threatTypeDisplay")
                .setContentText("Suspicious message from $sender via $appName")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("THREAT DETECTED ($confidencePercent% confidence)\n" +
                            "App: $appName\n" +
                            "Sender: $sender\n" +
                            "Type: $threatTypeDisplay" +
                            urlThreatInfo + "\n\n" +
                            "Message: \"${message.take(120)}${if (message.length > 120) "..." else ""}\"\n\n" +
                            result.explanation))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 100, 500))
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            Log.i(TAG, "Enhanced security alert with URL analysis sent for ${result.threatType} message from $sender via $appName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send security alert", e)
        }
    }

    private fun logThreatDetection(
        sender: String,
        message: String,
        result: MessageClassifier.ClassificationResult,
        appName: String,
        packageName: String,
        isHistorical: Boolean = false
    ) {
        // Enhanced logging with URL analysis
        val urlInfo = if (result.urlAnalysis != null) {
            "URLs: ${result.urlAnalysis.totalUrls} total, ${result.urlAnalysis.threatfulUrls} malicious"
        } else {
            "No URL analysis available"
        }

        Log.w(TAG, """
            ${if (isHistorical) "HISTORICAL " else ""}THREAT DETECTED:
            App: $appName ($packageName)
            Sender: $sender
            Threat Type: ${result.threatType}
            Confidence: ${(result.confidence * 100).toInt()}%
            Message Length: ${message.length}
            $urlInfo
            Explanation: ${result.explanation}
            Timestamp: ${System.currentTimeMillis()}
        """.trimIndent())

        serviceScope.launch {
            try {
                val threatRepository = com.example.sgp.repository.ThreatRepository.getInstance()

                // Extract malicious URLs for storage
                val maliciousUrls = result.urlAnalysis?.urlResults?.filter { it.isThreat }?.map { it.url } ?: emptyList()
                val urlThreatCount = result.urlAnalysis?.threatfulUrls ?: 0

                val threatItem = com.example.sgp.model.ThreatItem(
                    id = System.currentTimeMillis(),
                    messageContent = message,
                    sender = sender,
                    sourceApp = appName,
                    threatType = result.threatType.uppercase(),
                    confidenceScore = (result.confidence * 100).toInt(),
                    timestamp = System.currentTimeMillis(),
                    explanation = result.explanation,
                    urlAnalysis = result.urlAnalysis,
                    maliciousUrls = maliciousUrls,
                    urlThreatCount = urlThreatCount
                )
                threatRepository.addThreat(threatItem)
                Log.d(TAG, "Enhanced threat with URL analysis added to repository successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add threat to repository", e)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                val securityChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Security Monitoring",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "General security monitoring notifications"
                }

                val threatChannel = NotificationChannel(
                    THREAT_CHANNEL_ID,
                    "Threat Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High priority notifications for detected security threats"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 100, 500)
                }

                notificationManager.createNotificationChannel(securityChannel)
                notificationManager.createNotificationChannel(threatChannel)
                Log.d(TAG, "Notification channels created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification channels", e)
            }
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun registerBroadcastReceiver() {
        val filter = IntentFilter("com.example.sgp.REFRESH_NOTIFICATION_SCAN")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(scanBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                // For older versions, we register without the flag as required
                registerReceiver(scanBroadcastReceiver, filter)
            }
            isBroadcastReceiverRegistered = true
            Log.d(TAG, "Broadcast receiver registered for manual scan requests")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register broadcast receiver", e)
            isBroadcastReceiverRegistered = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NotificationMonitorService stopping...")

        try {
            if (isBroadcastReceiverRegistered) {
                try {
                    unregisterReceiver(scanBroadcastReceiver)
                    isBroadcastReceiverRegistered = false
                    Log.d(TAG, "Broadcast receiver unregistered successfully")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Broadcast receiver was not registered or already unregistered", e)
                }
            }

            serviceScope.cancel()
            messageClassifier?.close()
            processedNotifications.clear() // Clear the processed notifications set
            Log.d(TAG, "NotificationMonitorService stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null && targetPackages.containsKey(sbn.packageName)) {
            Log.d(TAG, "Notification removed from ${targetPackages[sbn.packageName]}")
        }
    }
}
