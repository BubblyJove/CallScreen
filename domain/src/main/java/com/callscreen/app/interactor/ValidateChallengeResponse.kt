package com.callscreen.app.interactor

import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.repository.MessageRepository
import com.callscreen.app.repository.ScreeningRepository
import com.callscreen.app.util.ScreenLog
import io.reactivex.Flowable
import javax.inject.Inject

class ValidateChallengeResponse @Inject constructor(
    private val screeningRepository: ScreeningRepository,
    private val messageRepository: MessageRepository
) : Interactor<ValidateChallengeResponse.Params>() {

    data class Params(val phoneNumber: String, val responseBody: String)

    sealed class Result {
        object Success : Result()
        object WrongAnswer : Result()
        object Expired : Result()
        object MaxAttempts : Result()
        object NoChallengeFound : Result()
    }

    override fun buildObservable(params: Params): Flowable<Result> {
        return Flowable.fromCallable {
            val challenge = screeningRepository.getChallengeForNumber(params.phoneNumber)

            if (challenge == null) {
                ScreenLog.d(TAG, "No challenge found for ${params.phoneNumber}")
                return@fromCallable Result.NoChallengeFound
            }

            if (challenge.isExpired()) {
                ScreenLog.d(TAG, "Challenge expired for ${params.phoneNumber}")
                screeningRepository.deleteChallengeState(params.phoneNumber)
                return@fromCallable Result.Expired
            }

            if (!challenge.hasAttemptsRemaining()) {
                ScreenLog.d(TAG, "Max attempts reached for ${params.phoneNumber}")
                return@fromCallable Result.MaxAttempts
            }

            // Increment attempts
            screeningRepository.incrementAttempts(params.phoneNumber)

            // Check answer (trim whitespace, case-insensitive)
            val answer = params.responseBody.trim()
            val expected = challenge.expectedAnswer.trim()

            if (answer.equals(expected, ignoreCase = true)) {
                ScreenLog.d(TAG, "Challenge PASSED for ${params.phoneNumber}")

                // Whitelist contact
                screeningRepository.whitelistContact(
                    params.phoneNumber,
                    params.phoneNumber, // displayName defaults to phone number
                    WhitelistedContact.WhitelistSource.CHALLENGE_PASSED
                )

                // Insert held messages into Quik DB so they appear in conversation history
                val pending = screeningRepository.getPendingMessagesForNumberSync(params.phoneNumber)
                ScreenLog.d(TAG, "Inserting ${pending.size} held messages for ${params.phoneNumber}")
                pending.forEach { msg ->
                    messageRepository.insertReceivedSms(-1, msg.phoneNumber, msg.body, msg.timestamp)
                }

                // Mark pending messages as delivered
                screeningRepository.deliverPendingMessages(params.phoneNumber)

                // Clean up challenge
                screeningRepository.deleteChallengeState(params.phoneNumber)

                // Send confirmation SMS
                try {
                    messageRepository.sendNewMessages(
                        subId = -1,
                        toAddresses = listOf(params.phoneNumber),
                        body = "Your identity has been verified. Your messages will now be delivered normally.",
                        attachments = emptyList(),
                        sendAsGroup = false
                    )
                    ScreenLog.d(TAG, "Confirmation SMS sent to ${params.phoneNumber}")
                } catch (e: Exception) {
                    ScreenLog.e(TAG, "Failed to send confirmation SMS to ${params.phoneNumber}", e)
                }

                return@fromCallable Result.Success
            }

            ScreenLog.d(TAG, "Wrong answer from ${params.phoneNumber}: got '$answer', expected '$expected'")
            Result.WrongAnswer
        }
    }

    companion object {
        private const val TAG = "Validate"
    }
}
