package com.callscreen.app.challenge

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.callscreen.app.R
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.ChallengeState
import com.callscreen.app.data.ChallengeType
import com.callscreen.app.data.MessageStatus
import com.callscreen.app.data.WhitelistSource
import com.callscreen.app.data.WhitelistedContact
import com.callscreen.app.sms.SmsSentReceiver
import com.callscreen.app.util.PhoneNumberUtil
import com.callscreen.app.util.ScreenLog
import com.callscreen.app.util.maskPhone
import kotlin.random.Random

class ChallengeManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val challengeDao = db.challengeDao()
    private val contactDao = db.contactDao()
    private val pendingDao = db.pendingMessageDao()

    suspend fun isNumberTrusted(phoneNumber: String): Boolean {
        val normalized = PhoneNumberUtil.normalize(phoneNumber)
        ScreenLog.d(TAG, "isNumberTrusted? raw=$phoneNumber normalized=$normalized")

        if (contactDao.isWhitelisted(normalized)) {
            ScreenLog.d(TAG, "$normalized is whitelisted in DB")
            return true
        }
        if (PhoneNumberUtil.isInDeviceContacts(context, phoneNumber)) {
            ScreenLog.d(TAG, "$phoneNumber found in device contacts — auto-whitelisting")
            contactDao.whitelist(
                WhitelistedContact(
                    phoneNumber = normalized,
                    displayName = PhoneNumberUtil.getContactName(context, phoneNumber),
                    source = WhitelistSource.CONTACTS
                )
            )
            return true
        }
        ScreenLog.d(TAG, "$normalized is NOT trusted")
        return false
    }

    suspend fun sendChallenge(phoneNumber: String, isCall: Boolean) {
        val normalized = PhoneNumberUtil.normalize(phoneNumber)
        ScreenLog.d(TAG, "sendChallenge called for $normalized (isCall=$isCall)")

        // Reuse the existing challenge question if one is active, but always
        // (re-)send the SMS — a previous send may have failed silently.
        val challenge: ChallengeState
        val existing = challengeDao.getChallenge(normalized)
        if (existing != null && !existing.isExpired) {
            ScreenLog.d(TAG, "Reusing existing challenge for $normalized (expires in ${(existing.expiresAt - System.currentTimeMillis()) / 1000}s)")
            challenge = existing
        } else {
            if (existing != null) {
                ScreenLog.d(TAG, "Old challenge expired — generating new one")
            }
            val new = generateChallenge()
            challenge = ChallengeState(
                phoneNumber = normalized,
                challengeQuestion = new.question,
                expectedAnswer = new.answer,
                type = new.type
            )
            challengeDao.upsert(challenge)
            ScreenLog.d(TAG, "Saved new challenge for $normalized: ${new.question} -> ${new.answer}")
        }

        val template = if (isCall) {
            context.getString(R.string.default_call_challenge, challenge.challengeQuestion)
        } else {
            context.getString(R.string.default_sms_challenge, challenge.challengeQuestion)
        }

        ScreenLog.d(TAG, "About to sendSms to $normalized")
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
        // 1. Permission check
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        ScreenLog.d(TAG, "SEND_SMS permission granted: $hasPerm")
        if (!hasPerm) {
            ScreenLog.e(TAG, "SEND_SMS not granted — aborting send to ${maskPhone(phoneNumber)}")
            return
        }

        try {
            // 2. Get SmsManager — try multiple strategies
            val smsManager = getSmsManager()
            if (smsManager == null) {
                ScreenLog.e(TAG, "Could not obtain SmsManager — aborting send to ${maskPhone(phoneNumber)}")
                return
            }

            // 3. Build sent-tracking PendingIntent
            val sentIntent = PendingIntent.getBroadcast(
                context,
                phoneNumber.hashCode(),
                Intent(SmsSentReceiver.ACTION).apply {
                    setPackage(context.packageName)
                    putExtra(SmsSentReceiver.EXTRA_PHONE, phoneNumber)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // 4. Send
            ScreenLog.d(TAG, "Calling sendTextMessage(to=$phoneNumber, msg=${message.take(80)}...)")
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            } else {
                val sentIntents = ArrayList(parts.map { sentIntent })
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            }
            ScreenLog.d(TAG, "sendTextMessage returned (async result via SmsSentReceiver)")

        } catch (e: SecurityException) {
            ScreenLog.e(TAG, "SecurityException sending to ${maskPhone(phoneNumber)}", e)
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Exception sending to ${maskPhone(phoneNumber)}", e)
        }
    }

    /**
     * Try several ways to get a working SmsManager.
     */
    @Suppress("DEPRECATION")
    private fun getSmsManager(): SmsManager? {
        // Strategy 1: SmsManager.getDefault() — works on all API levels
        try {
            val mgr = SmsManager.getDefault()
            ScreenLog.d(TAG, "SmsManager.getDefault() returned non-null (subId=${mgr.subscriptionId})")
            return mgr
        } catch (e: Exception) {
            ScreenLog.e(TAG, "SmsManager.getDefault() failed", e)
        }

        // Strategy 2: system service (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val mgr = context.getSystemService(SmsManager::class.java)
                if (mgr != null) {
                    ScreenLog.d(TAG, "getSystemService(SmsManager) returned non-null")
                    return mgr
                }
            } catch (e: Exception) {
                ScreenLog.e(TAG, "getSystemService(SmsManager) failed", e)
            }
        }

        // Strategy 3: explicit subscription
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subMgr = context.getSystemService(SubscriptionManager::class.java)
                val subId = subMgr?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
                if (subId != null) {
                    val mgr = SmsManager.getSmsManagerForSubscriptionId(subId)
                    ScreenLog.d(TAG, "getSmsManagerForSubscriptionId($subId) returned non-null")
                    return mgr
                } else {
                    ScreenLog.w(TAG, "No active SIM subscription found")
                }
            }
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Subscription-based SmsManager failed", e)
        }

        return null
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
