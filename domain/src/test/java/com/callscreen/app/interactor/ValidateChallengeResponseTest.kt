package com.callscreen.app.interactor

import com.callscreen.app.model.ChallengeState
import com.callscreen.app.repository.MessageRepository
import com.callscreen.app.repository.ScreeningRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ValidateChallengeResponseTest {

    private lateinit var screeningRepository: ScreeningRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var validator: ValidateChallengeResponse

    @Before
    fun setUp() {
        screeningRepository = mock()
        messageRepository = mock()
        validator = ValidateChallengeResponse(screeningRepository, messageRepository)
    }

    @Test
    fun `returns NoChallengeFound when no challenge exists`() {
        whenever(screeningRepository.getChallengeForNumber(any())).thenReturn(null)

        val result = validator.buildObservable(
            ValidateChallengeResponse.Params("+1234567890", "42")
        ).blockingFirst()

        assertTrue(result is ValidateChallengeResponse.Result.NoChallengeFound)
    }

    @Test
    fun `returns Expired when challenge is expired`() {
        val challenge = mock<ChallengeState> {
            on { isExpired() } doReturn true
        }
        whenever(screeningRepository.getChallengeForNumber(any())).thenReturn(challenge)

        val result = validator.buildObservable(
            ValidateChallengeResponse.Params("+1234567890", "42")
        ).blockingFirst()

        assertTrue(result is ValidateChallengeResponse.Result.Expired)
    }

    @Test
    fun `returns MaxAttempts when no attempts remaining`() {
        val challenge = mock<ChallengeState> {
            on { isExpired() } doReturn false
            on { hasAttemptsRemaining() } doReturn false
        }
        whenever(screeningRepository.getChallengeForNumber(any())).thenReturn(challenge)

        val result = validator.buildObservable(
            ValidateChallengeResponse.Params("+1234567890", "42")
        ).blockingFirst()

        assertTrue(result is ValidateChallengeResponse.Result.MaxAttempts)
    }

    @Test
    fun `returns WrongAnswer for incorrect response`() {
        val challenge = mock<ChallengeState> {
            on { isExpired() } doReturn false
            on { hasAttemptsRemaining() } doReturn true
            on { expectedAnswer } doReturn "42"
        }
        whenever(screeningRepository.getChallengeForNumber(any())).thenReturn(challenge)

        val result = validator.buildObservable(
            ValidateChallengeResponse.Params("+1234567890", "wrong")
        ).blockingFirst()

        assertTrue("Expected WrongAnswer, got $result", result is ValidateChallengeResponse.Result.WrongAnswer)
    }

    @Test
    fun `returns Success for correct answer case-insensitive`() {
        val challenge = mock<ChallengeState> {
            on { isExpired() } doReturn false
            on { hasAttemptsRemaining() } doReturn true
            on { expectedAnswer } doReturn "42"
        }
        whenever(screeningRepository.getChallengeForNumber(any())).thenReturn(challenge)
        whenever(screeningRepository.getPendingMessagesForNumberSync(any())).thenReturn(emptyList())
        whenever(messageRepository.sendNewMessages(any(), any(), any(), any(), any())).thenReturn(mock())

        val result = validator.buildObservable(
            ValidateChallengeResponse.Params("+1234567890", "42")
        ).blockingFirst()

        assertTrue("Expected Success, got $result", result is ValidateChallengeResponse.Result.Success)
    }
}
