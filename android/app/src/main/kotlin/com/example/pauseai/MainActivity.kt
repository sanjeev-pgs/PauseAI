package com.example.pauseai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : FlutterActivity() {

    private val CHANNEL = "pauseai/notifications"
    private val HIGH_RISK_CHANNEL = "pauseai_high_risk"

    override fun configureFlutterEngine(
        @NonNull flutterEngine: FlutterEngine
    ) {
        super.configureFlutterEngine(flutterEngine)

        createNotificationChannel()

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->

            when (call.method) {

                "getLatestNotification" -> {
                    result.success(
                        getLatestNotification()
                    )
                }

                "getNotificationAnalysis" -> {

                    val id =
                        call.argument<String>("id")

                    if (id == null) {
                        result.error(
                            "INVALID_ID",
                            "Notification ID is missing",
                            null
                        )
                    } else {
                        result.success(
                            getNotificationAnalysis(id)
                        )
                    }
                }

                "clearPendingNotifications" -> {

                    val ids =
                        call.argument<List<String>>("ids")

                    clearPendingNotifications(ids ?: emptyList())

                    result.success(true)
                }

                "saveScamHistory" -> {

                    val history =
                        call.argument<String>("history")

                    if (history == null) {
                        result.error(
                            "INVALID_HISTORY",
                            "History is missing",
                            null
                        )
                    } else {

                        getSharedPreferences(
                            "pauseai_history",
                            Context.MODE_PRIVATE
                        )
                            .edit()
                            .putString(
                                "scamHistory",
                                history
                            )
                            .apply()

                        result.success(true)
                    }
                }

                "getScamHistory" -> {
                    result.success(
                        getScamHistory()
                    )
                }

                "clearScamHistory" -> {

                    getSharedPreferences(
                        "pauseai_history",
                        Context.MODE_PRIVATE
                    )
                        .edit()
                        .remove("scamHistory")
                        .apply()

                    result.success(true)
                }

                "showHighRiskNotification" -> {

                    val message =
                        call.argument<String>("message")
                            ?: ""

                    showHighRiskNotification(
                        message
                    )

                    result.success(true)
                }

                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun getLatestNotification(): String {

        val prefs =
            getSharedPreferences(
                "pauseai_notifications",
                Context.MODE_PRIVATE
            )

        val json =
            prefs.getString(
                "pendingNotifications",
                "[]"
            ) ?: "[]"

        return try {

            val array =
                JSONArray(json)

            val sorted =
                mutableListOf<JSONObject>()

            for (i in 0 until array.length()) {

                sorted.add(
                    array.getJSONObject(i)
                )
            }

            sorted.sortByDescending {
                it.optLong(
                    "timestamp",
                    0L
                )
            }

            val result =
                JSONObject()

            val output =
                JSONArray()

            for (item in sorted) {
                output.put(item)
            }

            result.put(
                "allNotifications",
                output
            )

            result.toString()

        } catch (e: Exception) {

            JSONObject()
                .put(
                    "allNotifications",
                    JSONArray()
                )
                .toString()
        }
    }

    private fun getNotificationAnalysis(
        notificationId: String
    ): String {

        val prefs =
            getSharedPreferences(
                "pauseai_notifications",
                Context.MODE_PRIVATE
            )

        val json =
            prefs.getString(
                "pendingNotifications",
                "[]"
            ) ?: "[]"

        return try {

            val array =
                JSONArray(json)

            for (i in 0 until array.length()) {

                val item =
                    array.getJSONObject(i)

                if (
                    item.optString("id") ==
                    notificationId
                ) {

                    val result =
                        JSONObject()

                    result.put(
                        "found",
                        true
                    )

                    result.put(
                        "aiReady",
                        item.optBoolean(
                            "aiReady",
                            false
                        )
                    )

                    result.put(
                        "aiScam",
                        item.optBoolean(
                            "aiScam",
                            false
                        )
                    )

                    result.put(
                        "aiProbability",
                        item.optDouble(
                            "aiProbability",
                            0.0
                        )
                    )

                    result.put(
                        "aiRiskScore",
                        item.optDouble(
                            "aiRiskScore",
                            0.0
                        )
                    )

                    result.put(
                        "riskLevel",
                        item.optString(
                            "riskLevel",
                            "analysing"
                        )
                    )

                    result.put(
                        "aiLabel",
                        item.optString(
                            "aiLabel",
                            ""
                        )
                    )

                    result.put(
                        "category",
                        item.optString(
                            "category",
                            "General Message"
                        )
                    )

                    result.put(
                        "reasons",
                        item.optJSONArray(
                            "reasons"
                        ) ?: JSONArray()
                    )

                    result.put(
                        "recommendation",
                        item.optString(
                            "recommendation",
                            ""
                        )
                    )

                    result.put(
                        "aiResult",
                        item.optString(
                            "aiResult",
                            ""
                        )
                    )

                    result.put(
                        "urlRiskScore",
                        item.optDouble(
                            "urlRiskScore",
                            0.0
                        )
                    )

                    result.put(
                        "highRisk",
                        item.optBoolean(
                            "highRisk",
                            false
                        )
                    )

                    result.put(
                        "paymentVerification",
                        item.optBoolean(
                            "paymentVerification",
                            false
                        )
                    )

                    result.put(
                        "virusTotalReady",
                        item.optBoolean(
                            "virusTotalReady",
                            false
                        )
                    )

                    result.put(
                        "virusTotalMalicious",
                        item.optInt(
                            "virusTotalMalicious",
                            0
                        )
                    )

                    result.put(
                        "virusTotalSuspicious",
                        item.optInt(
                            "virusTotalSuspicious",
                            0
                        )
                    )

                    return result.toString()
                }
            }

            JSONObject()
                .put(
                    "found",
                    false
                )
                .toString()

        } catch (e: Exception) {

            JSONObject()
                .put(
                    "found",
                    false
                )
                .put(
                    "error",
                    e.message ?: "Unknown error"
                )
                .toString()
        }
    }

    private fun clearPendingNotifications(
        ids: List<String>
    ) {

        if (ids.isEmpty()) {
            return
        }

        val prefs =
            getSharedPreferences(
                "pauseai_notifications",
                Context.MODE_PRIVATE
            )

        val oldJson =
            prefs.getString(
                "pendingNotifications",
                "[]"
            ) ?: "[]"

        try {

            val oldArray =
                JSONArray(oldJson)

            val newArray =
                JSONArray()

            for (i in 0 until oldArray.length()) {

                val item =
                    oldArray.getJSONObject(i)

                val id =
                    item.optString("id")

                if (!ids.contains(id)) {
                    newArray.put(item)
                }
            }

            prefs.edit()
                .putString(
                    "pendingNotifications",
                    newArray.toString()
                )
                .apply()

        } catch (_: Exception) {
        }
    }

    private fun getScamHistory(): String {

        val prefs =
            getSharedPreferences(
                "pauseai_history",
                Context.MODE_PRIVATE
            )

        return prefs.getString(
            "scamHistory",
            "[]"
        ) ?: "[]"
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            val channel =
                NotificationChannel(
                    HIGH_RISK_CHANNEL,
                    "PauseAI High Risk Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )

            channel.description =
                "High-risk scam and suspicious notification alerts"

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun showHighRiskNotification(
        message: String
    ) {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val builder =
            androidx.core.app.NotificationCompat.Builder(
                this,
                HIGH_RISK_CHANNEL
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_alert
                )
                .setContentTitle(
                    "⚠ High-Risk Message Detected"
                )
                .setContentText(
                    message
                )
                .setStyle(
                    androidx.core.app.NotificationCompat
                        .BigTextStyle()
                        .bigText(message)
                )
                .setPriority(
                    androidx.core.app.NotificationCompat
                        .PRIORITY_HIGH
                )
                .setAutoCancel(true)

        manager.notify(
            "pauseai_manual_${System.currentTimeMillis()}"
                .hashCode(),
            builder.build()
        )
    }
}