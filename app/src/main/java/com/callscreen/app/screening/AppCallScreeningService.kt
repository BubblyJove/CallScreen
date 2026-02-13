package com.callscreen.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.callscreen.app.challenge.ChallengeManager
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.data.MessageType
import com.callscreen.app.util.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val number = handle?.schemeSpecificPart

        if (number.isNullOrBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // Trust check must complete before we respond, so block briefly.
        // isNumberTrusted is fast (local DB + contacts lookup).
        val challengeManager = ChallengeManager(applicationContext)
        val trusted = runBlocking(Dispatchers.IO) {
            challengeManager.isNumberTrusted(number)
        }

        if (trusted) {
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

            // Fire-and-forget: log + send SMS challenge on background thread.
            // Using applicationContext so it survives service destruction.
            val appContext = applicationContext
            scope.launch {
                try {
                    val db = AppDatabase.getInstance(appContext)
                    db.pendingMessageDao().insert(
                        PendingMessage(
                            phoneNumber = PhoneNumberUtil.normalize(number),
                            body = "[Screened call]",
                            type = MessageType.SMS,
                            isIncoming = true
                        )
                    )
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
