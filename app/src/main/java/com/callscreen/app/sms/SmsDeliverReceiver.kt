package com.callscreen.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.callscreen.app.challenge.ChallengeManager
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.util.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsDeliverReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group message parts by sender
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        for ((sender, parts) in grouped) {
            if (sender.isBlank()) continue

            val fullBody = parts.joinToString("") { it.messageBody ?: "" }
            val normalized = PhoneNumberUtil.normalize(sender)

            scope.launch {
                handleIncomingSms(context, normalized, fullBody)
            }
        }
    }

    private suspend fun handleIncomingSms(context: Context, sender: String, body: String) {
        val challengeManager = ChallengeManager(context)
        val db = AppDatabase.getInstance(context)

        // Check if this is a challenge response from a pending number
        val challenge = db.challengeDao().getChallenge(sender)
        if (challenge != null) {
            val passed = challengeManager.handleChallengeResponse(sender, body)
            if (passed) {
                // Challenge passed — message already delivered by ChallengeManager
                // Write the SMS to the system SMS provider so it appears in the inbox
                writeSmsToProvider(context, sender, body)
                return
            }
            // Wrong answer — store the attempt but don't deliver
        }

        if (challengeManager.isNumberTrusted(sender)) {
            // Trusted sender — deliver directly
            writeSmsToProvider(context, sender, body)
        } else {
            // Unknown sender — hold message and send challenge
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

    private fun writeSmsToProvider(context: Context, sender: String, body: String) {
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
            android.util.Log.e("SmsDeliverReceiver", "Failed to write SMS to provider", e)
        }
    }
}
