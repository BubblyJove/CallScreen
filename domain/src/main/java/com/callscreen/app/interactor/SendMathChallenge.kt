package com.callscreen.app.interactor

import com.callscreen.app.model.Attachment
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

    // Perf: pre-allocate the single-element list wrapper since sendNewMessages requires List
    // and we always send to exactly one recipient
    private fun singletonList(value: String): List<String> = listOf(value)

    // Perf: cache empty list — avoid allocating a new emptyList() per invocation
    companion object {
        private val EMPTY_ATTACHMENTS = emptyList<Attachment>()
    }

    override fun buildObservable(params: Params): Flowable<*> {
        return Flowable.fromCallable {
            // Reuse existing challenge if not expired
            val existing = screeningRepository.getChallengeForNumber(params.phoneNumber)
            if (existing != null && !existing.isExpired() && existing.hasAttemptsRemaining()) {
                Timber.d("Reusing existing challenge for %s", params.phoneNumber)
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
            // Perf: use StringBuilder to avoid multiple string template allocations
            val question = StringBuilder(16).append("What is ").append(a).append(" + ").append(b).append('?').toString()
            val answer = (a + b).toString()

            // Perf: capture currentTimeMillis once to avoid two system calls
            val now = System.currentTimeMillis()

            val challenge = ChallengeState().apply {
                this.phoneNumber = params.phoneNumber
                this.challengeQuestion = question
                this.expectedAnswer = answer
                this.createdAt = now
                this.expiresAt = now + ChallengeState.TTL_MS
                this.attempts = 0
                this.type = ChallengeState.ChallengeType.MATH
            }

            screeningRepository.saveChallengeState(challenge)
            sendChallengeMessage(params.phoneNumber, question)

            Timber.d("Sent math challenge to %s: %s", params.phoneNumber, question)
            challenge
        }
    }

    private fun sendChallengeMessage(phoneNumber: String, question: String) {
        // Perf: use StringBuilder for message body construction
        val body = StringBuilder(80)
            .append("To verify you're not a robocaller, please reply with the answer: ")
            .append(question)
            .toString()
        messageRepository.sendNewMessages(
            subId = -1,
            toAddresses = singletonList(phoneNumber),
            body = body,
            attachments = EMPTY_ATTACHMENTS,
            sendAsGroup = false
        )
    }
}
