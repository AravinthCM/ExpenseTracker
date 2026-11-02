package com.example.expensetracker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.expensetracker.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "ExpenseTrackerNotification"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService connected")
        try {
            val activeNotifs = activeNotifications
            if (activeNotifs != null) {
                Log.d(TAG, "Connected. Checking ${activeNotifs.size} active notification(s)...")
                for (sbn in activeNotifs) {
                    inspectAndLogNotification(sbn)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting active notifications on connect", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            inspectAndLogNotification(sbn)

            val packageName = sbn.packageName ?: "Unknown"
            val notification = sbn.notification
            if (notification != null && (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
                Log.d(TAG, "Ignoring group summary notification from $packageName")
                return
            }
            val extras = notification?.extras

            val notifId = sbn.id
            val notifTag = sbn.tag
            val notifKey = sbn.key
            val receivedAt = System.currentTimeMillis()

            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val textLines = extractTextLines(extras?.get(Notification.EXTRA_TEXT_LINES))
            val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            val summaryText = extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            val titleBig = extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            val rawExtras = extractRawExtrasString(extras)

            val isGmail = packageName == "com.google.android.gm" || packageName.contains("gmail", ignoreCase = true)
            val postTime = sbn.postTime

            // Body extraction with fallback order: EXTRA_BIG_TEXT -> EXTRA_TEXT -> title/text fallback
            val bodyText = when {
                isGmail && !bigText.isNullOrBlank() -> bigText
                isGmail && !text.isNullOrBlank() -> text
                else -> text ?: ""
            }

            if (isGmail) {
                Log.d(TAG, "GMAIL_BODY_EXTRACTED:\n$bodyText")
            }

            // Standard notification log
            Log.d(
                TAG,
                """
                Notification received
                Package: $packageName
                Title: $title
                Text: $text
                Time: $postTime
                """.trimIndent()
            )

            serviceScope.launch {
                try {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val enabledTemplates = db.paymentTemplateDao().getEnabledTemplatesSync()

                    val detectionResult = TransactionDetectionCoordinator.detectTransaction(
                        packageName = packageName,
                        title = title ?: "",
                        text = bodyText,
                        postTime = postTime,
                        enabledTemplates = enabledTemplates
                    )

                    val status = if (detectionResult.detectedPayment) "SUCCESS" else "FAIL"
                    val parsedTransaction = detectionResult.parsedTransaction

                    val testEntity = com.example.expensetracker.data.local.TestNotificationEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        receivedAt = receivedAt,
                        packageName = packageName,
                        notificationId = notifId,
                        notificationTag = notifTag,
                        notificationKey = notifKey,
                        title = title,
                        text = text,
                        bigText = bigText,
                        textLines = textLines,
                        subText = subText,
                        summaryText = summaryText,
                        titleBig = titleBig,
                        rawExtras = rawExtras,
                        status = status,
                        detectedPayment = detectionResult.detectedPayment,
                        amount = detectionResult.amount,
                        transactionType = detectionResult.transactionType,
                        merchant = detectionResult.merchant,
                        source = parsedTransaction?.source,
                        failureReason = detectionResult.failureReason,
                        detectionMethod = detectionResult.detectionMethod,
                        matchedTemplateName = detectionResult.matchedTemplateName,
                        accountNumber = detectionResult.accountNumber,
                        referenceNumber = detectionResult.referenceNumber,
                        confidence = detectionResult.confidence
                    )

                    db.testNotificationDao().insert(testEntity)
                    db.testNotificationDao().pruneOldRecords()

                    val rawText = if (isGmail && bodyText.isNotBlank()) bodyText else "${title ?: ""} ${text ?: ""}".trim()

                    // 1. Account Identification
                    val effectiveSuffix = detectionResult.accountNumber ?: parsedTransaction?.accountSuffix ?: NotificationParser.extractAccountSuffix(rawText)
                    val accounts = db.accountDao().getAllAccountsSync()
                    val confirmedMappings = db.sourceAccountMappingDao().getConfirmedMappingsSync()
                    val sourceStr = parsedTransaction?.source ?: packageName
                    val matchResult = com.example.expensetracker.model.AccountIdentifier.identifyAccount(
                        extractedSuffix = effectiveSuffix,
                        source = sourceStr,
                        senderOrPackage = packageName,
                        rawContent = rawText,
                        accounts = accounts,
                        confirmedMappings = confirmedMappings
                    )
                    val identifiedAccountId = matchResult.accountId
                    Log.d(TAG, "ACCOUNT_IDENTIFIER_RESULT: $matchResult -> identifiedAccountId=$identifiedAccountId")

                    // 2. Balance Observation Recording
                    val observedBalance = detectionResult.balance ?: parsedTransaction?.balance ?: NotificationParser.extractBalance(rawText)
                    val transactionAccountId = if (identifiedAccountId != null || observedBalance != null) {
                        identifiedAccountId
                    } else {
                        // A transaction without a bank balance may safely use the active primary account.
                        db.accountDao().getPrimaryAccountSync()?.id
                    }
                    if (observedBalance != null && observedBalance >= 0) {
                        val ref = parsedTransaction?.referenceNumber ?: NotificationParser.extractReferenceNumber(rawText)
                        val balancePaise = Math.round(observedBalance * 100.0)

                        val isDuplicateObservation = if (!ref.isNullOrBlank()) {
                            db.balanceObservationDao().findDuplicateByReferenceSync(ref, balancePaise) != null
                        } else {
                            val windowStart = postTime - 2 * 60 * 1000L
                            val windowEnd = postTime + 2 * 60 * 1000L
                            db.balanceObservationDao().findDuplicateByTimestampSync(sourceStr, balancePaise, windowStart, windowEnd) != null
                        }

                        if (!isDuplicateObservation) {
                            val observation = com.example.expensetracker.data.local.BalanceObservationEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                accountId = identifiedAccountId,
                                accountSuffix = effectiveSuffix,
                                balancePaise = balancePaise,
                                balanceType = "AVAILABLE",
                                currency = "INR",
                                timestamp = postTime,
                                source = sourceStr,
                                senderOrPackage = packageName,
                                referenceNumber = ref,
                                rawText = rawText,
                                isResolved = identifiedAccountId != null,
                                isReconciled = false
                            )
                            db.balanceObservationDao().insert(observation)
                            Log.d(TAG, "BALANCE_OBSERVATION_SAVED: id=${observation.id} accountId=$identifiedAccountId balance=₹$observedBalance resolved=${observation.isResolved}")

                            if (identifiedAccountId != null) {
                                val targetAccount = db.accountDao().getAccountByIdSync(identifiedAccountId)
                                val updatedAccount = targetAccount?.let {
                                    com.example.expensetracker.model.BalanceObservationApplier.applyIfNewer(it, observation)
                                }
                                if (updatedAccount != null) {
                                    db.accountDao().updateAccount(updatedAccount)
                                    Log.d(TAG, "ACCOUNT_BALANCE_BASELINE_UPDATED: accountId=$identifiedAccountId newBalancePaise=${updatedAccount.balanceBaselinePaise}")
                                }
                            }
                        } else {
                            Log.d(TAG, "BALANCE_OBSERVATION_SKIPPED: Duplicate observation for ref=$ref / balance=₹$observedBalance")
                        }
                    }

                    // 3. Process Transaction if detected
                    if (parsedTransaction != null) {
                        Log.d(TAG, "PARSE_RESULT (${detectionResult.detectionMethod}): amount=${parsedTransaction.amount} type=${parsedTransaction.type} name=${parsedTransaction.name} source=${parsedTransaction.source}")

                        val normalizedTransaction = TransactionNormalizer.normalize(
                            parsed = parsedTransaction,
                            rawText = rawText,
                            accountId = transactionAccountId,
                            balance = detectionResult.balance ?: parsedTransaction.balance
                        )
                        Log.d(TAG, "NORMALIZE_RESULT: amount=${normalizedTransaction.amount} type=${normalizedTransaction.type} name=${normalizedTransaction.name} source=${normalizedTransaction.source} accountId=${normalizedTransaction.accountId}")

                        val mappings = db.categoryMappingDao().getAllMappingsSync()
                        val mappedCategory = CategoryMapper.matchCategory(rawText, mappings)
                        Log.d(TAG, "CATEGORY_RESULT: category=$mappedCategory for transaction '${normalizedTransaction.name}'")

                        TransactionDeduplicator.processAndSave(db, normalizedTransaction, mappedCategory, applicationContext)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification in service scope", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification", e)
        }
    }

    private fun extractTextLines(value: Any?): String? {
        if (value == null) return null
        return try {
            when (value) {
                is Array<*> -> value.joinToString("\n") { it?.toString() ?: "" }
                is List<*> -> value.joinToString("\n") { it?.toString() ?: "" }
                is CharSequence -> value.toString()
                else -> value.toString()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractRawExtrasString(extras: android.os.Bundle?): String? {
        if (extras == null) return null
        return try {
            val sb = StringBuilder()
            for (key in extras.keySet()) {
                val valStr = formatExtraValue(extras.get(key))
                sb.append("$key: $valStr\n")
            }
            sb.toString().trim().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun inspectAndLogNotification(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: "Unknown"
        val notification = sbn.notification ?: return
        val extras = notification.extras

        val isGmail = packageName == "com.google.android.gm" || packageName.contains("gmail", ignoreCase = true)

        if (isGmail) {
            Log.d(TAG, "GMAIL_NOTIFICATION ==========================================")
            Log.d(TAG, "GMAIL_NOTIFICATION Package: $packageName | PostTime: ${sbn.postTime}")
            Log.d(TAG, "GMAIL_NOTIFICATION TickerText: ${notification.tickerText}")

            Log.d(TAG, "GMAIL_NOTIFICATION --- Specific Requested Extras ---")
            Log.d(TAG, "GMAIL_NOTIFICATION Notification.EXTRA_TITLE (${Notification.EXTRA_TITLE}): ${formatExtraValue(extras?.get(Notification.EXTRA_TITLE))}")
            Log.d(TAG, "GMAIL_NOTIFICATION Notification.EXTRA_TITLE_BIG (${Notification.EXTRA_TITLE_BIG}): ${formatExtraValue(extras?.get(Notification.EXTRA_TITLE_BIG))}")
            Log.d(TAG, "GMAIL_NOTIFICATION Notification.EXTRA_TEXT (${Notification.EXTRA_TEXT}): ${formatExtraValue(extras?.get(Notification.EXTRA_TEXT))}")
            Log.d(TAG, "GMAIL_NOTIFICATION Notification.EXTRA_BIG_TEXT (${Notification.EXTRA_BIG_TEXT}): ${formatExtraValue(extras?.get(Notification.EXTRA_BIG_TEXT))}")
            Log.d(TAG, "GMAIL_NOTIFICATION Notification.EXTRA_SUB_TEXT (${Notification.EXTRA_SUB_TEXT}): ${formatExtraValue(extras?.get(Notification.EXTRA_SUB_TEXT))}")
            Log.d(TAG, "GMAIL_NOTIFICATION Notification.EXTRA_SUMMARY_TEXT (${Notification.EXTRA_SUMMARY_TEXT}): ${formatExtraValue(extras?.get(Notification.EXTRA_SUMMARY_TEXT))}")
            Log.d(TAG, "GMAIL_NOTIFICATION Notification.EXTRA_TEXT_LINES (${Notification.EXTRA_TEXT_LINES}): ${formatExtraValue(extras?.get(Notification.EXTRA_TEXT_LINES))}")
            Log.d(TAG, "GMAIL_NOTIFICATION Notification.EXTRA_TEMPLATE (${Notification.EXTRA_TEMPLATE}): ${formatExtraValue(extras?.get(Notification.EXTRA_TEMPLATE))}")

            if (extras != null) {
                Log.d(TAG, "GMAIL_NOTIFICATION --- ALL Extras (${extras.keySet().size} keys) ---")
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    val formattedValue = formatExtraValue(value)
                    Log.d(TAG, "GMAIL_EXTRA_KEY: $key")
                    Log.d(TAG, "GMAIL_EXTRA_VALUE: $key = $formattedValue")
                }
            }
            Log.d(TAG, "GMAIL_NOTIFICATION ==========================================")
        }
    }

    private fun formatExtraValue(value: Any?): String {
        if (value == null) return "null"
        return try {
            when (value) {
                is CharSequence -> value.toString()
                is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { formatExtraValue(it) }
                is IntArray -> value.contentToString()
                is LongArray -> value.contentToString()
                is ByteArray -> value.contentToString()
                is FloatArray -> value.contentToString()
                is DoubleArray -> value.contentToString()
                is BooleanArray -> value.contentToString()
                is List<*> -> value.joinToString(prefix = "[", postfix = "]") { formatExtraValue(it) }
                is android.os.Bundle -> {
                    val keys = value.keySet()
                    val entries = keys.map { k -> "$k=${formatExtraValue(value.get(k))}" }
                    "Bundle{${entries.joinToString(", ")}}"
                }
                else -> {
                    if (value.javaClass.isArray) {
                        val length = java.lang.reflect.Array.getLength(value)
                        val list = mutableListOf<String>()
                        for (i in 0 until length) {
                            list.add(formatExtraValue(java.lang.reflect.Array.get(value, i)))
                        }
                        list.joinToString(prefix = "[", postfix = "]")
                    } else {
                        value.toString()
                    }
                }
            }
        } catch (e: Exception) {
            "Error formatting (${value.javaClass.simpleName}): ${e.message}"
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        try {
            val packageName = sbn.packageName ?: "Unknown"
            Log.d(TAG, "Notification removed: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification removal", e)
        }
    }
}
