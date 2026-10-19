package com.example.expensetracker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.expensetracker.MainActivity
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.ui.util.CurrencyFormatter

object TransactionNotificationHelper {

    private const val TAG = "TransactionNotification"
    const val CHANNEL_ID = "transaction_updates"
    const val CHANNEL_NAME = "Transaction Updates"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications confirming successfully saved transactions"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showTransactionAddedNotification(
        context: Context,
        type: TransactionType,
        amount: Double,
        name: String,
        source: String,
        balance: Double? = null
    ) {
        try {
            // Permission check for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Notification permission POST_NOTIFICATIONS not granted. Skipping notification.")
                    return
                }
            }

            createNotificationChannel(context)

            val title = buildNotificationTitle(type)
            val message = buildNotificationMessage(type, amount, name, source, balance)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            notificationManager?.notify(notificationId, builder.build())

            Log.d(TAG, "Successfully posted transaction notification: Title='$title', Message='$message'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show transaction notification", e)
        }
    }

    fun buildNotificationTitle(type: TransactionType): String {
        return when (type) {
            TransactionType.EXPENSE -> "Expense added"
            TransactionType.INCOME -> "Income added"
            TransactionType.TRANSFER -> "Transfer recorded"
        }
    }

    fun buildNotificationMessage(
        type: TransactionType,
        amount: Double,
        name: String,
        source: String,
        balance: Double? = null
    ): String {
        val formattedAmount = CurrencyFormatter.format(amount)
        val trimmedName = name.trim()
        val isManual = source.equals("MANUAL", ignoreCase = true)

        val transactionMessage = when (type) {
            TransactionType.EXPENSE -> {
                when {
                    trimmedName.isBlank() -> "$formattedAmount recorded"
                    trimmedName.startsWith("at ", ignoreCase = true) || trimmedName.startsWith("for ", ignoreCase = true) -> "$formattedAmount $trimmedName"
                    isManual -> "$formattedAmount for $trimmedName"
                    else -> "$formattedAmount at $trimmedName"
                }
            }
            TransactionType.INCOME -> {
                when {
                    trimmedName.isBlank() -> "$formattedAmount recorded"
                    trimmedName.startsWith("from ", ignoreCase = true) -> "$formattedAmount $trimmedName"
                    else -> "$formattedAmount from $trimmedName"
                }
            }
            TransactionType.TRANSFER -> {
                formattedAmount
            }
        }
        return if (balance != null && balance >= 0.0) {
            "$transactionMessage\nAvailable balance: ${CurrencyFormatter.format(balance)}"
        } else {
            transactionMessage
        }
    }
}
