package com.callscreen.app.interactor

import com.callscreen.app.model.ChallengeState
import com.callscreen.app.repository.MessageRepository
import com.callscreen.app.repository.ScreeningRepository
import io.reactivex.Flowable
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

class SendMathChallenge @Inject constructor(
    private val screeningRepository: ScreeningRepository,
    private val messageRepository: MessageRepository
) : Interactor<SendMathChallenge.Params>() {

    data class Params(val phoneNumber: String)

    override fun buildObservable(params: Params): Flowable<*> {
        return Flowable.fromCallable {
            // Reuse existing challenge if not expired
            val existing = screeningRepository.getChallengeForNumber(params.phoneNumber)
            if (existing != null && !existing.isExpired() && existing.hasAttemptsRemaining()) {
                Timber.d("Reusing existing challenge for ${params.phoneNumber}")
                sendChallengeMessage(params.phoneNumber, existing.challengeQuestion)
                return@fromCallable existing
            }

            // Clean up expired challenge
            if (existing != null) {
                screeningRepository.deleteChallengeState(params.phoneNumber)
            }

            // Generate new math challenge
            val a = Random.nextInt(1, 20)
            val b = Random.nextInt(1, 20)
            val question = "What is $a + $b?"
            val answer = (a + b).toString()

            val challenge = ChallengeState().apply {
                this.phoneNumber = params.phoneNumber
                this.challengeQuestion = question
                this.expectedAnswer = answer
                this.createdAt = System.currentTimeMillis()
                this.expiresAt = System.currentTimeMillis() + ChallengeState.TTL_MS
                this.attempts = 0
                this.type = ChallengeState.ChallengeType.MATH
            }

            screeningRepository.saveChallengeState(challenge)
            sendChallengeMessage(params.phoneNumber, question)

            Timber.d("Sent math challenge to ${params.phoneNumber}: $question")
            challenge
        }
    }

    private fun sendChallengeMessage(phoneNumber: String, question: String) {
        val body = "To verify you're not a robocaller, please reply with the answer: $question"
        messageRepository.sendNewMessages(
            subId = -1,
            toAddresses = listOf(phoneNumber),
            body = body,
            attachments = emptyList(),
            sendAsGroup = false
        )
    }
}
