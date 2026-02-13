package com.callscreen.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import com.callscreen.app.challenge.ChallengeManager
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.data.MessageType
import com.callscreen.app.util.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val number = handle?.schemeSpecificPart

        if (number.isNullOrBlank()) {
            // No number available, let it through
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        scope.launch {
            val challengeManager = ChallengeManager(applicationContext)

            if (challengeManager.isNumberTrusted(number)) {
                // Known number — let it ring
                respondToCall(callDetails, CallResponse.Builder().build())
            } else {
                // Unknown number — silence and reject, then send challenge
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(true)
                    .setSkipNotification(false)  // still show missed call notification
                    .build()

                respondToCall(callDetails, response)

                // Log the screened call
                val db = AppDatabase.getInstance(applicationContext)
                db.pendingMessageDao().insert(
                    PendingMessage(
                        phoneNumber = PhoneNumberUtil.normalize(number),
                        body = "[Screened call]",
                        type = MessageType.SMS,
                        isIncoming = true
                    )
                )

                // Send SMS challenge to caller
                challengeManager.sendChallenge(number, isCall = true)
            }
        }
    }
}
