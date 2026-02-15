package com.callscreen.app.interactor

import com.callscreen.app.model.CryptoPaymentChallenge
import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.repository.CryptoRepository
import com.callscreen.app.repository.MessageRepository
import com.callscreen.app.repository.ScreeningRepository
import com.callscreen.app.util.ScreenLog
import io.reactivex.Flowable
import javax.inject.Inject

class MonitorCryptoPayment @Inject constructor(
    private val cryptoRepository: CryptoRepository,
    private val screeningRepository: ScreeningRepository,
    private val messageRepository: MessageRepository
) : Interactor<MonitorCryptoPayment.Params>() {

    data class Params(val challengeId: String)

    data class PaymentStatus(
        val status: CryptoPaymentChallenge.PaymentStatus,
        val confirmations: Int = 0,
        val txHash: String = ""
    )

    override fun buildObservable(params: Params): Flowable<PaymentStatus> {
        return Flowable.create({ emitter ->
            val challenge = cryptoRepository.getPaymentChallengeById(params.challengeId)
            if (challenge == null) {
                emitter.onError(IllegalStateException("Challenge not found: ${params.challengeId}"))
                return@create
            }

            ScreenLog.d(TAG, "Starting payment monitor for challenge ${params.challengeId}")

            // Emit current status
            emitter.onNext(PaymentStatus(challenge.status, challenge.confirmations, challenge.txHash))

            // The actual WebSocket monitoring is handled by AlchemyWebSocketService
            // This interactor is called by the service when payment events occur

            emitter.setCancellable {
                ScreenLog.d(TAG, "Payment monitor cancelled for ${params.challengeId}")
            }
        }, io.reactivex.BackpressureStrategy.LATEST)
    }

    /**
     * Called when a matching transaction is detected by the WebSocket service.
     */
    fun onPaymentDetected(challengeId: String, txHash: String) {
        ScreenLog.d(TAG, "Payment detected for challenge $challengeId: $txHash")
        cryptoRepository.updateChallengeStatus(
            id = challengeId,
            status = CryptoPaymentChallenge.PaymentStatus.CONFIRMING,
            confirmations = 0,
            txHash = txHash
        )
    }

    /**
     * Called when confirmation count updates.
     */
    fun onConfirmationUpdate(challengeId: String, confirmations: Int, txHash: String) {
        ScreenLog.d(TAG, "Confirmation update for $challengeId: $confirmations/${CryptoPaymentChallenge.REQUIRED_CONFIRMATIONS}")

        if (confirmations >= CryptoPaymentChallenge.REQUIRED_CONFIRMATIONS) {
            // Payment fully confirmed
            cryptoRepository.updateChallengeStatus(
                id = challengeId,
                status = CryptoPaymentChallenge.PaymentStatus.CONFIRMED,
                confirmations = confirmations,
                txHash = txHash
            )

            // Whitelist the caller
            val challenge = cryptoRepository.getPaymentChallengeById(challengeId)
            if (challenge != null) {
                ScreenLog.d(TAG, "Whitelisting ${challenge.phoneNumber} via crypto payment")

                screeningRepository.whitelistContact(
                    challenge.phoneNumber,
                    challenge.phoneNumber,
                    WhitelistedContact.WhitelistSource.CRYPTO_PAID
                )

                // Insert held messages into Quik DB so they appear in conversation history
                val pending = screeningRepository.getPendingMessagesForNumberSync(challenge.phoneNumber)
                ScreenLog.d(TAG, "Inserting ${pending.size} held messages for ${challenge.phoneNumber}")
                pending.forEach { msg ->
                    messageRepository.insertReceivedSms(-1, msg.phoneNumber, msg.body, msg.timestamp)
                }

                // Mark pending messages as delivered
                screeningRepository.deliverPendingMessages(challenge.phoneNumber)

                // Clean up challenge state
                screeningRepository.deleteChallengeState(challenge.phoneNumber)

                // Send confirmation SMS
                try {
                    messageRepository.sendNewMessages(
                        subId = -1,
                        toAddresses = listOf(challenge.phoneNumber),
                        body = "Your payment has been confirmed and your identity verified. Your messages will now be delivered normally.",
                        attachments = emptyList(),
                        sendAsGroup = false
                    )
                    ScreenLog.d(TAG, "Confirmation SMS sent to ${challenge.phoneNumber}")
                } catch (e: Exception) {
                    ScreenLog.e(TAG, "Failed to send confirmation SMS to ${challenge.phoneNumber}", e)
                }

                ScreenLog.d(TAG, "Contact whitelisted via crypto payment: ${challenge.phoneNumber}")
            }
        } else {
            cryptoRepository.updateChallengeStatus(
                id = challengeId,
                status = CryptoPaymentChallenge.PaymentStatus.CONFIRMING,
                confirmations = confirmations,
                txHash = txHash
            )
        }
    }

    companion object {
        private const val TAG = "CryptoPayment"
    }
}
