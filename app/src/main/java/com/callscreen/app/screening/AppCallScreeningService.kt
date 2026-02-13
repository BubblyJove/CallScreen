package com.callscreen.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import com.callscreen.app.challenge.ChallengeManager
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.data.MessageType
import com.callscreen.app.util.PhoneNumberUtil
import com.callscreen.app.util.ScreenLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AppCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        ScreenLog.d(TAG, "===== onScreenCall fired =====")

        val handle = callDetails.handle
        ScreenLog.d(TAG, "handle=$handle")

        val number = handle?.schemeSpecificPart
        ScreenLog.d(TAG, "schemeSpecificPart=$number")

        if (number.isNullOrBlank()) {
            ScreenLog.w(TAG, "No caller number — letting call through")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        ScreenLog.d(TAG, "Screening call from $number  (direction=${callDetails.callDirection})")

        runBlocking(Dispatchers.IO) {
            try {
                val challengeManager = ChallengeManager(applicationContext)

                if (challengeManager.isNumberTrusted(number)) {
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
                    ScreenLog.d(TAG, "respondToCall done (rejected)")

                    val db = AppDatabase.getInstance(applicationContext)
                    db.pendingMessageDao().insert(
                        PendingMessage(
                            phoneNumber = PhoneNumberUtil.normalize(number),
                            body = "[Screened call]",
                            type = MessageType.SMS,
                            isIncoming = true
                        )
                    )
                    ScreenLog.d(TAG, "Logged screened call in DB")

                    challengeManager.sendChallenge(number, isCall = true)
                    ScreenLog.d(TAG, "sendChallenge returned for $number")
                }
            } catch (e: Exception) {
                ScreenLog.e(TAG, "EXCEPTION in onScreenCall for $number", e)
            }
        }

        ScreenLog.d(TAG, "===== onScreenCall finished =====")
    }

    companion object {
        private const val TAG = "CallScreen"
    }
}
