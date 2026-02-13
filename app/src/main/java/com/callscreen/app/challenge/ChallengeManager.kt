package com.callscreen.app.challenge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.callscreen.app.R
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.ChallengeState
import com.callscreen.app.data.ChallengeType
import com.callscreen.app.data.MessageStatus
import com.callscreen.app.data.WhitelistSource
import com.callscreen.app.data.WhitelistedContact
import com.callscreen.app.util.PhoneNumberUtil
import kotlin.random.Random

class ChallengeManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val challengeDao = db.challengeDao()
    private val contactDao = db.contactDao()
    private val pendingDao = db.pendingMessageDao()

    suspend fun isNumberTrusted(phoneNumber: String): Boolean {
        val normalized = PhoneNumberUtil.normalize(phoneNumber)
        if (contactDao.isWhitelisted(normalized)) return true
        if (PhoneNumberUtil.isInDeviceContacts(context, phoneNumber)) {
            contactDao.whitelist(
                WhitelistedContact(
                    phoneNumber = normalized,
                    displayName = PhoneNumberUtil.getContactName(context, phoneNumber),
                    source = WhitelistSource.CONTACTS
                )
            )
            return true
        }
        return false
    }

    suspend fun sendChallenge(phoneNumber: String, isCall: Boolean) {
        val normalized = PhoneNumberUtil.normalize(phoneNumber)
        Log.d(TAG, "sendChallenge called for $normalized (isCall=$isCall)")

        // Reuse the existing challenge question if one is active, but always
        // (re-)send the SMS — a previous send may have failed silently.
        val challenge: ChallengeState
        val existing = challengeDao.getChallenge(normalized)
        if (existing != null && !existing.isExpired) {
            Log.d(TAG, "Reusing existing challenge for $normalized")
            challenge = existing
        } else {
            val new = generateChallenge()
            challenge = ChallengeState(
                phoneNumber = normalized,
                challengeQuestion = new.question,
                expectedAnswer = new.answer,
                type = new.type
            )
            challengeDao.upsert(challenge)
            Log.d(TAG, "Created new challenge for $normalized")
        }

        val template = if (isCall) {
            context.getString(R.string.default_call_challenge, challenge.challengeQuestion)
        } else {
            context.getString(R.string.default_sms_challenge, challenge.challengeQuestion)
        }

        sendSms(normalized, template)
    }

    suspend fun handleChallengeResponse(phoneNumber: String, responseBody: String): Boolean {
        val normalized = PhoneNumberUtil.normalize(phoneNumber)
        val challenge = challengeDao.getChallenge(normalized) ?: return false

        if (challenge.isExpired) {
            challengeDao.delete(normalized)
            return false
        }

        if (challenge.attempts >= ChallengeState.MAX_ATTEMPTS) {
            challengeDao.delete(normalized)
            return false
        }

        val answer = responseBody.trim().lowercase()
        val expected = challenge.expectedAnswer.lowercase()

        if (answer == expected || responseBody.trim() == challenge.expectedAnswer) {
            // Challenge passed
            contactDao.whitelist(
                WhitelistedContact(
                    phoneNumber = normalized,
                    source = WhitelistSource.CHALLENGE_PASSED
                )
            )
            pendingDao.deliverAllForNumber(normalized)
            challengeDao.delete(normalized)

            sendSms(normalized, context.getString(R.string.challenge_success))
            return true
        }

        // Wrong answer, increment attempts
        challengeDao.update(challenge.copy(attempts = challenge.attempts + 1))
        return false
    }

    private fun generateChallenge(): Challenge {
        val a = Random.nextInt(1, 20)
        val b = Random.nextInt(1, 20)
        return Challenge(
            question = "What is $a + $b?",
            answer = (a + b).toString(),
            type = ChallengeType.MATH
        )
    }

    @Suppress("DEPRECATION")
    private fun sendSms(phoneNumber: String, message: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "SEND_SMS permission not granted — cannot send to $phoneNumber")
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            Log.d(TAG, "Sending SMS to $phoneNumber: ${message.take(60)}...")

            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            Log.d(TAG, "sendTextMessage returned OK for $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phoneNumber", e)
        }
    }

    companion object {
        private const val TAG = "ChallengeManager"
    }

    private data class Challenge(
        val question: String,
        val answer: String,
        val type: ChallengeType
    )
}
