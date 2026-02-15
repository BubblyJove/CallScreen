package com.callscreen.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.callscreen.app.challenge.ChallengeManager
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.util.PhoneNumberUtil
import com.callscreen.app.util.ScreenLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Receives SMS_DELIVER broadcasts as the default SMS app.
 *
 * Uses [goAsync] to extend the BroadcastReceiver deadline beyond the
 * default 10 seconds. The coroutine scope is tied to the PendingResult
 * lifecycle — [PendingResult.finish] is always called in the finally block.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MS) {
                    processMessages(context, messages)
                }
            } catch (e: Exception) {
                ScreenLog.e(TAG, "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processMessages(
        context: Context,
        messages: Array<android.telephony.SmsMessage>
    ) {
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        for ((sender, parts) in grouped) {
            if (sender.isBlank()) continue
            val fullBody = parts.joinToString("") { it.messageBody ?: "" }
            val normalized = PhoneNumberUtil.normalize(sender)
            handleIncomingSms(context, normalized, fullBody)
        }
    }

    private suspend fun handleIncomingSms(
        context: Context,
        sender: String,
        body: String
    ) {
        val challengeManager = ChallengeManager(context)
        val db = AppDatabase.getInstance(context)

        val challenge = db.challengeDao().getChallenge(sender)
        if (challenge != null) {
            val passed = challengeManager.handleChallengeResponse(sender, body)
            if (passed) {
                writeSmsToProvider(context, sender, body)
                return
            }
        }

        if (challengeManager.isNumberTrusted(sender)) {
            writeSmsToProvider(context, sender, body)
        } else {
            db.pendingMessageDao().insert(
                PendingMessage(
                    phoneNumber = sender,
                    body = body,
                    isIncoming = true
                )
            )
            challengeManager.sendChallenge(sender, isCall = false)
        }
    }

    private fun writeSmsToProvider(
        context: Context,
        sender: String,
        body: String
    ) {
        try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Failed to write SMS to provider", e)
        }
    }

    companion object {
        private const val TAG = "SmsDeliverReceiver"
        private const val RECEIVER_TIMEOUT_MS = 25_000L
    }
}
