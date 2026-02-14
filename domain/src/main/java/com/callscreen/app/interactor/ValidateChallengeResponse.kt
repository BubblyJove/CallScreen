package com.callscreen.app.interactor

import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.repository.ScreeningRepository
import io.reactivex.Flowable
import timber.log.Timber
import javax.inject.Inject

class ValidateChallengeResponse @Inject constructor(
    private val screeningRepository: ScreeningRepository
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
                // Perf: use %s format to avoid string concat when logging is stripped
                Timber.d("No challenge found for %s", params.phoneNumber)
                return@fromCallable Result.NoChallengeFound
            }

            if (challenge.isExpired()) {
                Timber.d("Challenge expired for %s", params.phoneNumber)
                screeningRepository.deleteChallengeState(params.phoneNumber)
                return@fromCallable Result.Expired
            }

            if (!challenge.hasAttemptsRemaining()) {
                Timber.d("Max attempts reached for %s", params.phoneNumber)
                return@fromCallable Result.MaxAttempts
            }

            // Increment attempts
            screeningRepository.incrementAttempts(params.phoneNumber)

            // Check answer (trim whitespace, case-insensitive)
            val answer = params.responseBody.trim()
            val expected = challenge.expectedAnswer.trim()

            if (answer.equals(expected, ignoreCase = true)) {
                Timber.d("Challenge passed for %s", params.phoneNumber)

                // Whitelist contact
                screeningRepository.whitelistContact(
                    params.phoneNumber,
                    params.phoneNumber, // displayName defaults to phone number
                    WhitelistedContact.WhitelistSource.CHALLENGE_PASSED
                )

                // Deliver all pending messages
                screeningRepository.deliverPendingMessages(params.phoneNumber)

                // Clean up challenge
                screeningRepository.deleteChallengeState(params.phoneNumber)

                return@fromCallable Result.Success
            }

            Timber.d("Wrong answer from %s: got '%s', expected '%s'", params.phoneNumber, answer, expected)
            Result.WrongAnswer
        }
    }
}
