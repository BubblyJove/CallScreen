package com.callscreen.app.challenge

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
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

        // Don't re-send if there's an active challenge
        val existing = challengeDao.getChallenge(normalized)
        if (existing != null && !existing.isExpired) return

        val challenge = generateChallenge()
        challengeDao.upsert(
            ChallengeState(
                phoneNumber = normalized,
                challengeQuestion = challenge.question,
                expectedAnswer = challenge.answer,
                type = challenge.type
            )
        )

        val template = if (isCall) {
            context.getString(R.string.default_call_challenge, challenge.question)
        } else {
            context.getString(R.string.default_sms_challenge, challenge.question)
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
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                Log.e(TAG, "SmsManager is null — cannot send SMS to $phoneNumber")
                return
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            Log.d(TAG, "SMS challenge sent to $phoneNumber")
        } catch (e: SecurityException) {
            Log.e(TAG, "SEND_SMS permission not granted — cannot send to $phoneNumber", e)
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
