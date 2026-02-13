package com.callscreen.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import com.callscreen.app.challenge.ChallengeManager
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.MessageType
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.util.PhoneNumberUtil
import com.callscreen.app.util.ScreenLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Screens incoming calls. Trusted callers ring through; unknown callers are
 * rejected and sent an SMS challenge.
 *
 * The telecom framework calls [onScreenCall] on the main thread and expects
 * [respondToCall] to be called promptly. We respond immediately with the
 * screening decision, then perform async work (DB insert + challenge SMS)
 * in a lifecycle-aware coroutine scope.
 */
class AppCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        ScreenLog.d(TAG, "onScreenCall fired")

        val handle = callDetails.handle
        val number = handle?.schemeSpecificPart

        if (number.isNullOrBlank()) {
            ScreenLog.w(TAG, "No caller number — letting call through")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        ScreenLog.d(TAG, "Screening call from $number")

        // Respond immediately: check whitelist synchronously via cache,
        // then do async DB/SMS work after responding.
        serviceScope.launch {
            try {
                val challengeManager = ChallengeManager(applicationContext)

                val trusted = withTimeout(TRUST_CHECK_TIMEOUT_MS) {
                    challengeManager.isNumberTrusted(number)
                }

                if (trusted) {
                    ScreenLog.d(TAG, "Trusted — allowing call from $number")
                    respondToCall(callDetails, CallResponse.Builder().build())
                } else {
                    ScreenLog.d(TAG, "Untrusted — rejecting call from $number")
                    val response = CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSilenceCall(true)
                        .setSkipNotification(false)
                        .build()
                    respondToCall(callDetails, response)

                    val normalized = PhoneNumberUtil.normalize(number)
                    val db = AppDatabase.getInstance(applicationContext)
                    db.pendingMessageDao().insert(
                        PendingMessage(
                            phoneNumber = normalized,
                            body = "[Screened call]",
                            type = MessageType.SMS,
                            isIncoming = true
                        )
                    )

                    withTimeout(SEND_CHALLENGE_TIMEOUT_MS) {
                        challengeManager.sendChallenge(number, isCall = true)
                    }
                    ScreenLog.d(TAG, "Challenge sent for $number")
                }
            } catch (e: Exception) {
                ScreenLog.e(TAG, "Error screening call from $number", e)
                // Fail-open: allow call if screening fails
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "CallScreen"
        private const val TRUST_CHECK_TIMEOUT_MS = 5_000L
        private const val SEND_CHALLENGE_TIMEOUT_MS = 10_000L
    }
}
