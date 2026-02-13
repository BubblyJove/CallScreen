package com.callscreen.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.callscreen.app.challenge.ChallengeManager
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.data.MessageType
import com.callscreen.app.util.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AppCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val number = handle?.schemeSpecificPart

        if (number.isNullOrBlank()) {
            Log.w(TAG, "No caller number available — letting call through")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        Log.d(TAG, "Screening call from $number")

        // Run everything synchronously on IO so the work finishes before the
        // system destroys this service.  The total wall time is <200 ms
        // (local DB queries + one SmsManager.sendTextMessage call).
        runBlocking(Dispatchers.IO) {
            val challengeManager = ChallengeManager(applicationContext)

            if (challengeManager.isNumberTrusted(number)) {
                Log.d(TAG, "Trusted number $number — allowing call")
                respondToCall(callDetails, CallResponse.Builder().build())
            } else {
                Log.d(TAG, "Unknown number $number — rejecting and sending challenge")
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(true)
                    .setSkipNotification(false)
                    .build()

                respondToCall(callDetails, response)

                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    db.pendingMessageDao().insert(
                        PendingMessage(
                            phoneNumber = PhoneNumberUtil.normalize(number),
                            body = "[Screened call]",
                            type = MessageType.SMS,
                            isIncoming = true
                        )
                    )
                    Log.d(TAG, "Logged screened call from $number")

                    challengeManager.sendChallenge(number, isCall = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to log/challenge $number", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CallScreenService"
    }
}
