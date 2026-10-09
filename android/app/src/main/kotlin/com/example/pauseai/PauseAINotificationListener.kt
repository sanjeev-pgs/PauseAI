package com.example.pauseai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class PauseAINotificationListener : NotificationListenerService() {

    private val prefsName = "pauseai_notifications"
    private val queueKey = "pendingNotifications"
    private val aiUrl = "http://10.57.153.174:5000/detect"
    private val virusTotalStatusUrl = "http://10.57.153.174:5000/virustotal_status"

    companion object {
        private const val TAG = "PauseAIListener"
        private const val MAX_QUEUE_SIZE = 50
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "LISTENER_CONNECTED")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "LISTENER_DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "NOTIFICATION_POSTED")

        try {
            val packageName = sbn.packageName

            if (packageName == applicationContext.packageName) {
                return
            }

            val notification = sbn.notification

            if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
                return
            }

            if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) {
                return
            }

            val extras = notification.extras ?: return

            var title =
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

            var message =
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            val messages =
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)

            if (messages != null && messages.isNotEmpty()) {
                val lastMessage = messages.lastOrNull()

                if (lastMessage is android.os.Bundle) {
                    val bundleText =
                        lastMessage.getCharSequence("text")?.toString()

                    if (!bundleText.isNullOrBlank()) {
                        message = bundleText
                    }

                    val bundleSender =
                        lastMessage.getCharSequence("sender")?.toString()

                    if (!bundleSender.isNullOrBlank()) {
                        title = bundleSender
                    }
                }
            }

            title = title.trim()
            message = message.trim()

            if (message.isBlank()) {
                return
            }

            if (message == title && message.length < 3) {
                return
            }

            if (isSummaryNotification(message)) {
                return
            }

            val appName = getAppName(packageName)

            val notificationId =
                "${packageName}_${sbn.id}_${sbn.postTime}"

            if (
                isDuplicateNotification(
                    packageName,
                    title,
                    message,
                    notificationId
                )
            ) {
                return
            }

            saveNotification(
                notificationId,
                packageName,
                appName,
                title,
                message,
                sbn.postTime
            )

            analyzeInBackground(
                notificationId,
                packageName,
                appName,
                title,
                message
            )

        } catch (e: Exception) {
            Log.e(TAG, "NOTIFICATION_ERROR", e)
        }
    }

    private fun isSummaryNotification(message: String): Boolean {
        val text = message.trim()

        if (
            text.matches(
                Regex(
                    "\\d+ new messages?",
                    RegexOption.IGNORE_CASE
                )
            )
        ) {
            return true
        }

        if (
            text.equals(
                "You have new messages",
                ignoreCase = true
            )
        ) {
            return true
        }

        if (
            text.contains("new messages", true) &&
            text.length <= 40
        ) {
            return true
        }

        return false
    }

    private fun isDuplicateNotification(
        packageName: String,
        title: String,
        message: String,
        notificationId: String
    ): Boolean {
        return synchronized(this) {
            try {
                val prefs =
                    getSharedPreferences(
                        prefsName,
                        Context.MODE_PRIVATE
                    )

                val oldJson =
                    prefs.getString(
                        queueKey,
                        "[]"
                    ) ?: "[]"

                val oldArray = JSONArray(oldJson)
                val now = System.currentTimeMillis()

                for (i in 0 until oldArray.length()) {
                    val item =
                        oldArray.optJSONObject(i)
                            ?: continue

                    if (
                        item.optString("id") ==
                        notificationId
                    ) {
                        return@synchronized true
                    }

                    val oldPackage =
                        item.optString("packageName")

                    val oldTitle =
                        item.optString("title")

                    val oldMessage =
                        item.optString("message")

                    val oldTimestamp =
                        item.optLong(
                            "timestamp",
                            0L
                        )

                    if (
                        oldPackage == packageName &&
                        oldTitle == title &&
                        oldMessage == message &&
                        now - oldTimestamp < 5000
                    ) {
                        return@synchronized true
                    }
                }

                false

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "DUPLICATE_CHECK_ERROR",
                    e
                )

                false
            }
        }
    }

    private fun saveNotification(
        notificationId: String,
        packageName: String,
        appName: String,
        title: String,
        message: String,
        timestamp: Long
    ) {
        synchronized(this) {
            try {
                val prefs =
                    getSharedPreferences(
                        prefsName,
                        Context.MODE_PRIVATE
                    )

                val oldJson =
                    prefs.getString(
                        queueKey,
                        "[]"
                    ) ?: "[]"

                val oldArray = JSONArray(oldJson)
                val newArray = JSONArray()

                for (i in 0 until oldArray.length()) {
                    val item =
                        oldArray.optJSONObject(i)
                            ?: continue

                    if (
                        item.optString("id") !=
                        notificationId
                    ) {
                        newArray.put(item)
                    }
                }

                val item = JSONObject()

                item.put("id", notificationId)
                item.put("packageName", packageName)
                item.put("appName", appName)
                item.put("title", title)
                item.put("message", message)
                item.put("timestamp", timestamp)

                item.put("aiReady", false)
                item.put("aiScam", false)
                item.put("aiProbability", 0.0)
                item.put("aiRiskScore", 0.0)
                item.put("riskLevel", "analysing")
                item.put("aiLabel", "")
                item.put("aiResult", "AI analysing...")

                item.put("category", "Analysing")
                item.put("reasons", JSONArray())
                item.put(
                    "recommendation",
                    "Please wait for analysis."
                )

                item.put("urlRiskScore", 0.0)
                item.put("highRisk", false)
                item.put("paymentVerification", false)

                item.put("virusTotalReady", false)
                item.put("virusTotalMalicious", 0)
                item.put("virusTotalSuspicious", 0)

                newArray.put(item)

                val start =
                    maxOf(
                        0,
                        newArray.length() - MAX_QUEUE_SIZE
                    )

                val finalArray = JSONArray()

                for (i in start until newArray.length()) {
                    finalArray.put(
                        newArray.getJSONObject(i)
                    )
                }

                prefs.edit()
                    .putString(
                        queueKey,
                        finalArray.toString()
                    )
                    .apply()

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "SAVE_NOTIFICATION_ERROR",
                    e
                )
            }
        }
    }

    private fun updateNotificationResult(
        notificationId: String,
        isScam: Boolean,
        probability: Double,
        aiRiskScore: Double,
        riskLevel: String,
        label: String,
        category: String,
        reasons: JSONArray,
        recommendation: String,
        urlRiskScore: Double,
        highRisk: Boolean,
        aiResult: String,
        paymentVerification: Boolean = false
    ) {
        synchronized(this) {
            try {
                val prefs =
                    getSharedPreferences(
                        prefsName,
                        Context.MODE_PRIVATE
                    )

                val oldJson =
                    prefs.getString(
                        queueKey,
                        "[]"
                    ) ?: "[]"

                val oldArray = JSONArray(oldJson)
                val newArray = JSONArray()

                for (i in 0 until oldArray.length()) {
                    val item =
                        oldArray.optJSONObject(i)
                            ?: continue

                    if (
                        item.optString("id") ==
                        notificationId
                    ) {
                        item.put(
                            "aiReady",
                            true
                        )

                        item.put(
                            "aiScam",
                            isScam
                        )

                        item.put(
                            "aiProbability",
                            probability
                        )

                        item.put(
                            "aiRiskScore",
                            aiRiskScore
                        )

                        item.put(
                            "riskLevel",
                            riskLevel
                        )

                        item.put(
                            "aiLabel",
                            label
                        )

                        item.put(
                            "category",
                            category
                        )

                        item.put(
                            "reasons",
                            reasons
                        )

                        item.put(
                            "recommendation",
                            recommendation
                        )

                        item.put(
                            "aiResult",
                            aiResult
                        )

                        item.put(
                            "urlRiskScore",
                            urlRiskScore
                        )

                        item.put(
                            "highRisk",
                            highRisk
                        )

                        item.put(
                            "paymentVerification",
                            paymentVerification
                        )
                    }

                    newArray.put(item)
                }

                prefs.edit()
                    .putString(
                        queueKey,
                        newArray.toString()
                    )
                    .apply()

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "AI_RESULT_SAVE_ERROR",
                    e
                )
            }
        }
    }

    private fun analyzeInBackground(
        notificationId: String,
        packageName: String,
        appName: String,
        title: String,
        message: String
    ) {
        thread {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(aiUrl)

                connection =
                    url.openConnection()
                        as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val body =
                    JSONObject()
                        .put(
                            "message",
                            message
                        )
                        .toString()

                connection.outputStream.use {
                    it.write(
                        body.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    it.flush()
                }

                val responseCode =
                    connection.responseCode

                if (
                    responseCode !=
                    HttpURLConnection.HTTP_OK
                ) {
                    handleAiFailure(
                        notificationId,
                        packageName,
                        appName,
                        title,
                        message
                    )

                    return@thread
                }

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                val result = JSONObject(response)

                val isScam =
                    result.optBoolean(
                        "is_scam",
                        false
                    )

                val probability =
                    result.optDouble(
                        "probability",
                        0.0
                    )

                val aiRiskScore =
                    result.optDouble(
                        "risk_score",
                        0.0
                    )

                val riskLevel =
                    result.optString(
                        "risk_level",
                        "safe"
                    )

                val label =
                    result.optString(
                        "label",
                        ""
                    )

                val category =
                    result.optString(
                        "category",
                        detectCategory(message)
                    )

                val reasons =
                    result.optJSONArray(
                        "reasons"
                    ) ?: buildReasons(
                        message,
                        result
                    )

                val recommendation =
                    result.optString(
                        "recommendation",
                        buildRecommendation(
                            category,
                            aiRiskScore
                        )
                    )

                val urlScore =
                    calculateUrlRisk(message)

                val paymentRisk =
                    containsPaymentWords(message) &&
                    containsStrongPaymentRisk(message)

                val paymentClaim =
                    isPaymentClaim(message)

                val doubtfulPayment =
                    paymentClaim &&
                    !isBankSender(
                        title,
                        packageName
                    ) &&
                    (
                        paymentRisk ||
                        urlScore >= 30.0 ||
                        containsStrongScamWords(message)
                    )

                val strongScamMessage =
                    isScam &&
                    probability >= 95.0 &&
                    (
                        paymentRisk ||
                        urlScore >= 70.0 ||
                        containsStrongScamWords(message)
                    )

                val highRisk =
                    (
                        isScam &&
                        probability >= 95.0
                    ) ||
                    (
                        isScam &&
                        aiRiskScore >= 95.0
                    ) ||
                    urlScore >= 70.0 ||
                    (
                        paymentRisk &&
                        isScam &&
                        probability >= 95.0
                    ) ||
                    strongScamMessage

                Log.d(
                    TAG,
                    "AI_DECISION scam=$isScam probability=$probability risk=$aiRiskScore url=$urlScore highRisk=$highRisk"
                )

                val aiResult =
                    when {
                        highRisk ->
                            "Suspicious message"

                        doubtfulPayment ->
                            "Check payment independently"

                        else ->
                            "No scam detected"
                    }

                updateNotificationResult(
                    notificationId,
                    isScam,
                    probability,
                    aiRiskScore,
                    riskLevel,
                    label,
                    category,
                    reasons,
                    recommendation,
                    urlScore,
                    highRisk,
                    aiResult,
                    doubtfulPayment
                )

                if (highRisk) {
                    saveScamHistory(
                        message,
                        probability,
                        appName,
                        packageName,
                        title,
                        extractUrl(message),
                        urlScore,
                        aiRiskScore,
                        category,
                        reasons,
                        recommendation
                    )

                    showHighRiskNotification(
                        message,
                        probability,
                        appName,
                        packageName,
                        extractUrl(message),
                        urlScore,
                        notificationId
                    )

                } else if (doubtfulPayment) {
                    saveScamHistory(
                        message,
                        probability,
                        appName,
                        packageName,
                        title,
                        extractUrl(message),
                        urlScore,
                        aiRiskScore,
                        "Payment Scam",
                        reasons,
                        "Verify the transaction in your official banking or UPI app."
                    )

                    showPaymentVerificationNotification(
                        message,
                        appName,
                        packageName
                    )
                }

                val messageUrl =
                    extractUrl(message)

                if (messageUrl != null) {
                    startVirusTotalStatusCheck(
                        notificationId,
                        message,
                        probability,
                        appName,
                        packageName,
                        title,
                        messageUrl
                    )
                }

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "AI_ERROR",
                    e
                )

                handleAiFailure(
                    notificationId,
                    packageName,
                    appName,
                    title,
                    message
                )

            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun handleAiFailure(
        notificationId: String,
        packageName: String,
        appName: String,
        title: String,
        message: String
    ) {
        val urlScore =
            calculateUrlRisk(message)

        val paymentVerification =
            isDoubtfulPayment(
                message,
                title,
                packageName
            )

        val highRisk =
            urlScore >= 70.0

        val category =
            when {
                highRisk ->
                    "Malicious Link"

                paymentVerification ->
                    "Payment Scam"

                else ->
                    "Unknown"
            }

        val reasons = JSONArray()

        if (urlScore >= 70.0) {
            reasons.put(
                "The message contains a high-risk URL."
            )
        }

        if (paymentVerification) {
            reasons.put(
                "The message contains a payment-related claim that should be independently verified."
            )
        }

        if (reasons.length() == 0) {
            reasons.put(
                "AI server was unavailable, so a complete AI analysis could not be performed."
            )
        }

        val recommendation =
            when {
                highRisk ->
                    "Do not open the link. Verify the sender through an official source."

                paymentVerification ->
                    "Verify the transaction directly in your official banking or UPI app."

                else ->
                    "Be careful and verify unexpected requests before acting."
            }

        updateNotificationResult(
            notificationId,
            false,
            0.0,
            0.0,
            "unknown",
            "",
            category,
            reasons,
            recommendation,
            urlScore,
            highRisk,
            if (highRisk)
                "Suspicious message"
            else if (paymentVerification)
                "Check payment independently"
            else
                "AI unavailable",
            paymentVerification
        )

        if (highRisk) {
            saveScamHistory(
                message,
                0.0,
                appName,
                packageName,
                title,
                extractUrl(message),
                urlScore,
                0.0,
                category,
                reasons,
                recommendation
            )

            showHighRiskNotification(
                message,
                0.0,
                appName,
                packageName,
                extractUrl(message),
                urlScore,
                notificationId
            )

        } else if (paymentVerification) {
            saveScamHistory(
                message,
                0.0,
                appName,
                packageName,
                title,
                extractUrl(message),
                urlScore,
                0.0,
                category,
                reasons,
                recommendation
            )

            showPaymentVerificationNotification(
                message,
                appName,
                packageName
            )
        }

        val messageUrl =
            extractUrl(message)

        if (messageUrl != null) {
            startVirusTotalStatusCheck(
                notificationId,
                message,
                0.0,
                appName,
                packageName,
                title,
                messageUrl
            )
        }
    }

    private fun detectCategory(
        message: String
    ): String {
        val text =
            message.lowercase()

        return when {
            containsStrongPaymentRisk(text) ||
                containsPaymentWords(text) ->
                "Payment Scam"

            text.contains("otp") ||
                text.contains("pin") ||
                text.contains("password") ||
                text.contains("cvv") ->
                "Credential Theft"

            text.contains("kyc") ||
                text.contains("account blocked") ||
                text.contains("account suspended") ->
                "Account/KYC Scam"

            text.contains("won") ||
                text.contains("prize") ||
                text.contains("reward") ||
                text.contains("lottery") ||
                text.contains("cashback") ->
                "Prize/Reward Scam"

            extractUrl(text) != null ->
                "Suspicious Link"

            text.contains("job") ||
                text.contains("work from home") ||
                text.contains("salary") ->
                "Job Scam"

            else ->
                "Suspicious Message"
        }
    }

    private fun buildReasons(
        message: String,
        result: JSONObject
    ): JSONArray {
        val reasons = JSONArray()

        val signals =
            result.optJSONObject("signals")

        if (signals != null) {
            if (signals.optInt("urgency", 0) > 0) {
                reasons.put(
                    "The message uses urgency or time pressure."
                )
            }

            if (signals.optInt("threat", 0) > 0) {
                reasons.put(
                    "The message contains a threat or account restriction."
                )
            }

            if (signals.optInt("financial", 0) > 0) {
                reasons.put(
                    "The message contains financial or payment-related terms."
                )
            }

            if (signals.optInt("sensitive_info", 0) > 0) {
                reasons.put(
                    "The message requests or mentions sensitive information."
                )
            }

            if (signals.optInt("impersonation", 0) > 0) {
                reasons.put(
                    "The message may be impersonating an organisation or support team."
                )
            }

            if (signals.optInt("reward", 0) > 0) {
                reasons.put(
                    "The message contains reward, prize, or winning language."
                )
            }

            if (signals.optInt("action_request", 0) > 0) {
                reasons.put(
                    "The message asks the user to take an immediate action."
                )
            }

            if (signals.optBoolean("has_url", false)) {
                reasons.put(
                    "The message contains a link."
                )
            }
        }

        if (reasons.length() == 0) {
            reasons.put(
                "The AI model analysed the message for scam indicators."
            )
        }

        return reasons
    }

    private fun buildRecommendation(
        category: String,
        riskScore: Double
    ): String {
        return when {
            category == "Payment Scam" ->
                "Verify the payment directly in your official banking or UPI app."

            category == "Credential Theft" ->
                "Never share OTP, PIN, CVV, passwords, or verification codes."

            category == "Account/KYC Scam" ->
                "Contact the organisation using its official website or app."

            category == "Prize/Reward Scam" ->
                "Do not pay fees or share sensitive information to claim a prize."

            category == "Suspicious Link" ||
                category == "Malicious Link" ->
                "Do not open the link. Verify the sender and website independently."

            riskScore >= 75 ->
                "Avoid taking action until the message is independently verified."

            else ->
                "Stay cautious and verify unexpected requests."
        }
    }

    private fun isDoubtfulPayment(
        message: String,
        title: String,
        packageName: String
    ): Boolean {
        val paymentClaim =
            isPaymentClaim(message)

        if (!paymentClaim) {
            return false
        }

        if (
            isBankSender(
                title,
                packageName
            )
        ) {
            return false
        }

        val urlRisk =
            calculateUrlRisk(message)

        val strongPaymentRisk =
            containsPaymentWords(message) &&
            containsStrongPaymentRisk(message)

        val strongScam =
            containsStrongScamWords(message)

        return strongPaymentRisk ||
            urlRisk >= 30.0 ||
            strongScam
    }

    private fun isBankSender(
        title: String,
        packageName: String
    ): Boolean {
        val text =
            title.lowercase()

        val pkg =
            packageName.lowercase()

        val bankNames =
            listOf(
                "sbi",
                "state bank",
                "hdfc",
                "icici",
                "axis bank",
                "kotak",
                "kotak mahindra",
                "indusind",
                "yes bank",
                "idfc",
                "idfc first",
                "federal bank",
                "bank of baroda",
                "bob",
                "canara bank",
                "union bank",
                "pnb",
                "punjab national bank",
                "bank of india",
                "indian bank",
                "uco bank",
                "central bank",
                "rbl bank",
                "au small finance bank",
                "bandhan bank",
                "paytm payments bank",
                "airtel payments bank",
                "india post payments bank",
                "phonepe",
                "google pay",
                "gpay",
                "paytm",
                "amazon pay",
                "bhim"
            )

        val bankingPackage =
            pkg.contains("bank") ||
            pkg.contains("phonepe") ||
            pkg.contains("paytm") ||
            pkg.contains("google.android.apps.nbu") ||
            pkg.contains("bhim")

        return bankNames.any {
            text.contains(it)
        } || bankingPackage
    }

    private fun showPaymentVerificationNotification(
        message: String,
        appName: String,
        packageName: String
    ) {
        try {
            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            val channelId =
                "pauseai_payment_verification"

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                val channel =
                    NotificationChannel(
                        channelId,
                        "PauseAI Payment Verification",
                        NotificationManager.IMPORTANCE_HIGH
                    )

                channel.description =
                    "PauseAI payment verification warnings"

                manager.createNotificationChannel(
                    channel
                )
            }

            val text =
                "Source: $appName\n\n$message\n\nVerify the transaction in your official UPI or banking app before taking any action."

            val builder =
                NotificationCompat.Builder(
                    this,
                    channelId
                )
                    .setSmallIcon(
                        android.R.drawable.ic_dialog_alert
                    )
                    .setContentTitle(
                        "⚠ Check Payment Independently"
                    )
                    .setContentText(
                        "PauseAI recommends verifying this payment independently"
                    )
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(text)
                    )
                    .setPriority(
                        NotificationCompat.PRIORITY_HIGH
                    )
                    .setAutoCancel(true)

            manager.notify(
                "pauseai_payment_${System.currentTimeMillis()}".hashCode(),
                builder.build()
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "PAYMENT_VERIFICATION_NOTIFICATION_ERROR",
                e
            )
        }
    }

    private fun startVirusTotalStatusCheck(
        notificationId: String,
        message: String,
        probability: Double,
        appName: String,
        packageName: String,
        title: String,
        url: String
    ) {
        thread {
            for (attempt in 1..30) {
                var connection: HttpURLConnection? = null

                try {
                    val encodedUrl =
                        URLEncoder.encode(
                            url,
                            "UTF-8"
                        )

                    val statusUrl =
                        URL(
                            "$virusTotalStatusUrl?url=$encodedUrl"
                        )

                    connection =
                        statusUrl.openConnection()
                            as HttpURLConnection

                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode =
                        connection.responseCode

                    if (
                        responseCode ==
                        HttpURLConnection.HTTP_OK
                    ) {
                        val response =
                            connection.inputStream
                                .bufferedReader()
                                .use {
                                    it.readText()
                                }

                        val result =
                            JSONObject(response)

                        if (
                            result.optBoolean(
                                "ready",
                                false
                            )
                        ) {
                            val virusTotal =
                                result.optJSONObject(
                                    "virustotal"
                                )

                            val malicious =
                                virusTotal?.optInt(
                                    "malicious",
                                    0
                                ) ?: 0

                            val suspicious =
                                virusTotal?.optInt(
                                    "suspicious",
                                    0
                                ) ?: 0

                            updateVirusTotalResult(
                                notificationId,
                                malicious,
                                suspicious
                            )

                            if (malicious > 0) {
                                updateNotificationResult(
                                    notificationId,
                                    true,
                                    probability,
                                    100.0,
                                    "high",
                                    "malicious_url",
                                    "Malicious Link",
                                    JSONArray().apply {
                                        put(
                                            "VirusTotal detected malicious engines for this URL."
                                        )
                                    },
                                    "Do not open this URL.",
                                    100.0,
                                    true,
                                    "Malicious URL detected",
                                    false
                                )

                                updateVirusTotalHistory(
                                    message,
                                    probability,
                                    appName,
                                    packageName,
                                    title,
                                    url
                                )

                                showHighRiskNotification(
                                    message,
                                    probability,
                                    appName,
                                    packageName,
                                    url,
                                    100.0,
                                    notificationId
                                )
                            }

                            return@thread
                        }
                    }

                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "VT_STATUS_ERROR",
                        e
                    )

                } finally {
                    connection?.disconnect()
                }

                Thread.sleep(2000)
            }
        }
    }

    private fun updateVirusTotalResult(
        notificationId: String,
        malicious: Int,
        suspicious: Int
    ) {
        synchronized(this) {
            try {
                val prefs =
                    getSharedPreferences(
                        prefsName,
                        Context.MODE_PRIVATE
                    )

                val oldJson =
                    prefs.getString(
                        queueKey,
                        "[]"
                    ) ?: "[]"

                val oldArray = JSONArray(oldJson)

                for (i in 0 until oldArray.length()) {
                    val item =
                        oldArray.optJSONObject(i)
                            ?: continue

                    if (
                        item.optString("id") ==
                        notificationId
                    ) {
                        item.put(
                            "virusTotalReady",
                            true
                        )

                        item.put(
                            "virusTotalMalicious",
                            malicious
                        )

                        item.put(
                            "virusTotalSuspicious",
                            suspicious
                        )
                    }
                }

                prefs.edit()
                    .putString(
                        queueKey,
                        oldArray.toString()
                    )
                    .apply()

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "VT_RESULT_SAVE_ERROR",
                    e
                )
            }
        }
    }

    private fun updateVirusTotalHistory(
        message: String,
        probability: Double,
        appName: String,
        packageName: String,
        title: String,
        url: String
    ): Boolean {
        synchronized(this) {
            try {
                val prefs =
                    getSharedPreferences(
                        "pauseai_history",
                        Context.MODE_PRIVATE
                    )

                val oldJson =
                    prefs.getString(
                        "scamHistory",
                        "[]"
                    ) ?: "[]"

                val history = JSONArray(oldJson)

                for (i in 0 until history.length()) {
                    val item =
                        history.optJSONObject(i)
                            ?: continue

                    if (
                        item.optString("message") ==
                        message &&
                        item.optString("packageName") ==
                        packageName &&
                        item.optString("url") ==
                        url
                    ) {
                        item.put(
                            "urlRiskScore",
                            100.0
                        )

                        item.put(
                            "probability",
                            maxOf(
                                item.optDouble(
                                    "probability",
                                    0.0
                                ),
                                probability
                            )
                        )

                        item.put(
                            "category",
                            "Malicious Link"
                        )

                        if (title.isNotBlank()) {
                            item.put(
                                "title",
                                title
                            )
                        }

                        prefs.edit()
                            .putString(
                                "scamHistory",
                                history.toString()
                            )
                            .apply()

                        return true
                    }
                }

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "VT_HISTORY_UPDATE_ERROR",
                    e
                )
            }

            return false
        }
    }

    private fun containsPaymentWords(
        message: String
    ): Boolean {
        val text =
            message.lowercase()

        val words =
            listOf(
                "upi",
                "payment",
                "pay",
                "transfer",
                "send money",
                "receive money",
                "bank",
                "account",
                "otp",
                "pin",
                "cvv",
                "refund",
                "cashback",
                "fee",
                "charge",
                "wallet",
                "qr code",
                "scan qr"
            )

        return words.any {
            text.contains(it)
        }
    }

    private fun containsStrongPaymentRisk(
        message: String
    ): Boolean {
        val text =
            message.lowercase()

        val strongWords =
            listOf(
                "share otp",
                "share your otp",
                "enter otp",
                "send otp",
                "share pin",
                "share your pin",
                "share cvv",
                "send money",
                "transfer money",
                "pay immediately",
                "payment required",
                "claim cashback",
                "pay fee",
                "scan qr",
                "scan this qr"
            )

        return strongWords.any {
            text.contains(it)
        }
    }

    private fun isPaymentClaim(
        message: String
    ): Boolean {
        val text =
            message.lowercase()

        val transactionWords =
            listOf(
                "credited",
                "credit",
                "debited",
                "debit",
                "received",
                "transaction successful",
                "transaction completed",
                "payment successful",
                "payment received",
                "amount credited",
                "amount debited",
                "money credited",
                "money debited"
            )

        val financialContext =
            listOf(
                "upi",
                "bank",
                "account",
                "a/c",
                "ac ",
                "wallet",
                "transaction",
                "payment"
            )

        val hasTransactionWord =
            transactionWords.any {
                text.contains(it)
            }

        val hasFinancialContext =
            financialContext.any {
                text.contains(it)
            }

        val hasAmount =
            Regex(
                "(₹|rs\\.?|inr)\\s*[0-9][0-9,]*(\\.[0-9]{1,2})?"
            ).containsMatchIn(text) ||
            Regex(
                "\\b[0-9][0-9,]*(\\.[0-9]{1,2})?\\s*(rupees|rs|inr)\\b"
            ).containsMatchIn(text)

        return hasTransactionWord &&
            (
                hasFinancialContext ||
                hasAmount
            )
    }

    private fun containsStrongScamWords(
        message: String
    ): Boolean {
        val text =
            message.lowercase()

        val words =
            listOf(
                "account will be blocked",
                "account has been blocked",
                "account suspended",
                "verify your kyc",
                "verify kyc",
                "verify your account",
                "click the link",
                "click here immediately",
                "send money",
                "transfer money",
                "share otp",
                "share your otp",
                "enter otp",
                "claim your reward",
                "claim your prize",
                "you have won",
                "winner",
                "urgent action required",
                "limited time",
                "pay immediately",
                "payment required",
                "refund pending",
                "bank account blocked",
                "password will expire",
                "account will be suspended",
                "verify immediately"
            )

        return words.any {
            text.contains(it)
        }
    }

    private fun extractUrl(
        message: String
    ): String? {
        val regex =
            Regex(
                "(https?://[^\\s<>\"\\]]+|www\\.[^\\s<>\"\\]]+)",
                RegexOption.IGNORE_CASE
            )

        val match =
            regex.find(message)
                ?: return null

        return match.value.trimEnd(
            '.',
            ',',
            '!',
            '?',
            ':',
            ';',
            ')',
            ']',
            '}'
        )
    }

    private fun calculateUrlRisk(
        message: String
    ): Double {
        val urlText =
            extractUrl(message)
                ?: return 0.0

        var score = 0.0

        try {
            val normalized =
                if (
                    urlText.startsWith(
                        "www.",
                        true
                    )
                ) {
                    "https://$urlText"
                } else {
                    urlText
                }

            val parsed =
                java.net.URI(normalized)

            val host =
                parsed.host?.lowercase()
                    ?: ""

            val path =
                parsed.path
                    ?: ""

            val query =
                parsed.rawQuery
                    ?.lowercase()
                    ?: ""

            val lowerUrl =
                normalized.lowercase()

            val shorteners =
                listOf(
                    "bit.ly",
                    "tinyurl.com",
                    "t.co",
                    "goo.gl",
                    "is.gd",
                    "ow.ly",
                    "buff.ly",
                    "cutt.ly",
                    "rb.gy",
                    "shorturl.at",
                    "rebrand.ly",
                    "tiny.cc"
                )

            if (
                shorteners.any {
                    host == it ||
                    host.endsWith(
                        ".$it"
                    )
                }
            ) {
                score += 30
            }

            if (
                host.matches(
                    Regex(
                        "^\\d{1,3}(\\.\\d{1,3}){3}$"
                    )
                )
            ) {
                score += 40
            }

            val suspiciousWords =
                listOf(
                    "verify",
                    "verification",
                    "login",
                    "signin",
                    "secure",
                    "security",
                    "update",
                    "kyc",
                    "bank",
                    "account",
                    "refund",
                    "reward",
                    "prize",
                    "cashback",
                    "bonus",
                    "claim",
                    "otp",
                    "upi",
                    "payment",
                    "wallet",
                    "blocked",
                    "suspended",
                    "confirm",
                    "credential",
                    "password"
                )

            suspiciousWords.forEach { word ->
                if (
                    lowerUrl.contains(word)
                ) {
                    score += 8
                }
            }

            if (
                host.split(".").size >= 5
            ) {
                score += 20
            }

            if (
                host.length > 45
            ) {
                score += 10
            }

            val labels =
                host.split(".")

            labels.forEach { label ->
                val numbers =
                    label.count {
                        it.isDigit()
                    }

                val letters =
                    label.count {
                        it.isLetter()
                    }

                if (
                    label.length >= 15 &&
                    numbers >= 2 &&
                    letters >= 5
                ) {
                    score += 15
                }

                if (
                    label.split("-").size >= 4
                ) {
                    score += 15
                }
            }

            if (
                lowerUrl.contains("@")
            ) {
                score += 30
            }

            if (
                lowerUrl.contains("%")
            ) {
                score += 5
            }

            if (
                !lowerUrl.startsWith(
                    "https://"
                )
            ) {
                score += 10
            }

            if (
                path.length > 100
            ) {
                score += 10
            }

            val longToken =
                path.split(
                    "/",
                    "?",
                    "&",
                    "="
                ).any { token ->
                    token.count {
                        it.isLetterOrDigit()
                    } >= 20
                }

            if (longToken) {
                score += 10
            }

            if (
                host.contains("xn--")
            ) {
                score += 30
            }

            val redirectParameters =
                listOf(
                    "redirect=",
                    "redirect_url=",
                    "redirect_uri=",
                    "url=",
                    "target=",
                    "next=",
                    "return=",
                    "return_url=",
                    "continue=",
                    "destination="
                )

            redirectParameters.forEach { parameter ->
                if (
                    query.contains(parameter)
                ) {
                    score += 12
                }
            }

            val suspiciousPorts =
                listOf(
                    81,
                    8080,
                    8000,
                    8008,
                    8888,
                    3000,
                    5000,
                    8001,
                    9000
                )

            if (
                parsed.port in suspiciousPorts
            ) {
                score += 15
            }

            if (
                host.count {
                    it.isDigit()
                } >= 6
            ) {
                score += 10
            }

            if (
                host.count {
                    it == '-'
                } >= 3
            ) {
                score += 10
            }

            val encodedIndicators =
                listOf(
                    "%2f",
                    "%2e",
                    "%40",
                    "%3a",
                    "%3f",
                    "%26",
                    "%3d"
                )

            if (
                encodedIndicators.any {
                    lowerUrl.contains(it)
                }
            ) {
                score += 10
            }

            var suspiciousIndicatorCount = 0

            if (
                shorteners.any {
                    host == it ||
                    host.endsWith(
                        ".$it"
                    )
                }
            ) {
                suspiciousIndicatorCount++
            }

            if (
                host.matches(
                    Regex(
                        "^\\d{1,3}(\\.\\d{1,3}){3}$"
                    )
                )
            ) {
                suspiciousIndicatorCount++
            }

            if (
                lowerUrl.contains("@")
            ) {
                suspiciousIndicatorCount++
            }

            if (
                host.contains("xn--")
            ) {
                suspiciousIndicatorCount++
            }

            if (
                redirectParameters.any {
                    query.contains(it)
                }
            ) {
                suspiciousIndicatorCount++
            }

            if (
                suspiciousIndicatorCount >= 2
            ) {
                score += 15
            }

        } catch (e: Exception) {
            score += 20
        }

        return minOf(
            score,
            100.0
        )
    }

    private fun saveScamHistory(
        message: String,
        probability: Double,
        appName: String,
        packageName: String,
        title: String,
        url: String?,
        urlRiskScore: Double,
        aiRiskScore: Double = 0.0,
        category: String = "Suspicious Message",
        reasons: JSONArray = JSONArray(),
        recommendation: String = ""
    ) {
        synchronized(this) {
            try {
                val prefs =
                    getSharedPreferences(
                        "pauseai_history",
                        Context.MODE_PRIVATE
                    )

                val old =
                    prefs.getString(
                        "scamHistory",
                        "[]"
                    ) ?: "[]"

                val oldArray = JSONArray(old)

                val item = JSONObject()

                item.put(
                    "message",
                    message
                )

                item.put(
                    "probability",
                    probability
                )

                item.put(
                    "aiRiskScore",
                    aiRiskScore
                )

                item.put(
                    "category",
                    category
                )

                item.put(
                    "app",
                    appName
                )

                item.put(
                    "title",
                    title
                )

                item.put(
                    "packageName",
                    packageName
                )

                item.put(
                    "timestamp",
                    System.currentTimeMillis()
                )

                if (url != null) {
                    item.put(
                        "url",
                        url
                    )
                }

                item.put(
                    "urlRiskScore",
                    urlRiskScore
                )

                item.put(
                    "reasons",
                    reasons
                )

                item.put(
                    "recommendation",
                    recommendation
                )

                val newArray = JSONArray()

                newArray.put(item)

                for (
                    i in 0 until
                    minOf(
                        oldArray.length(),
                        49
                    )
                ) {
                    newArray.put(
                        oldArray.getJSONObject(i)
                    )
                }

                prefs.edit()
                    .putString(
                        "scamHistory",
                        newArray.toString()
                    )
                    .apply()

                Log.d(
                    TAG,
                    "HISTORY_SAVED title=$title message=$message"
                )

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "HISTORY_ERROR",
                    e
                )
            }
        }
    }

    private fun showHighRiskNotification(
        message: String,
        probability: Double,
        appName: String,
        packageName: String,
        url: String?,
        urlRiskScore: Double,
        notificationKey: String? = null
    ) {
        try {
            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            val channelId =
                "pauseai_high_risk"

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                val channel =
                    NotificationChannel(
                        channelId,
                        "PauseAI High Risk Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    )

                channel.description =
                    "High-risk scam and suspicious notification alerts"

                manager.createNotificationChannel(
                    channel
                )
            }

            val intent =
                packageManager.getLaunchIntentForPackage(
                    packageName
                )

            val pendingIntent =
                if (intent != null) {
                    PendingIntent.getActivity(
                        this,
                        System.currentTimeMillis().toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    null
                }

            val urlText =
                if (
                    urlRiskScore >= 70 &&
                    url != null
                ) {
                    "\nURL Risk: ${urlRiskScore.toInt()}/100"
                } else {
                    ""
                }

            val text =
                "Source: $appName\n\n$message$urlText\n\nVerify independently before taking any action."

            val builder =
                NotificationCompat.Builder(
                    this,
                    channelId
                )
                    .setSmallIcon(
                        android.R.drawable.ic_dialog_alert
                    )
                    .setContentTitle(
                        "⚠ High-Risk Message Detected"
                    )
                    .setContentText(
                        "PauseAI detected a potentially dangerous message"
                    )
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(text)
                    )
                    .setPriority(
                        NotificationCompat.PRIORITY_HIGH
                    )
                    .setAutoCancel(true)

            if (pendingIntent != null) {
                builder.setContentIntent(
                    pendingIntent
                )
            }

            val notificationId =
                notificationKey?.hashCode()
                    ?: "pauseai_${System.currentTimeMillis()}".hashCode()

            manager.notify(
                notificationId,
                builder.build()
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "HIGH_RISK_NOTIFICATION_ERROR",
                e
            )
        }
    }

    private fun getAppName(
        packageName: String
    ): String {
        return try {
            val info =
                packageManager.getApplicationInfo(
                    packageName,
                    0
                )

            packageManager
                .getApplicationLabel(info)
                .toString()

        } catch (_: Exception) {
            when (packageName) {
                "com.whatsapp" ->
                    "WhatsApp"

                "com.instagram.android" ->
                    "Instagram"

                "org.telegram.messenger" ->
                    "Telegram"

                "com.microsoft.teams" ->
                    "Microsoft Teams"

                "com.google.android.apps.messaging" ->
                    "Messages"

                else ->
                    packageName
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?
    ) {
        super.onNotificationRemoved(sbn)
    }
}