package com.callscreen.app.interactor

import com.callscreen.app.model.CryptoPaymentChallenge
import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.repository.CryptoRepository
import com.callscreen.app.repository.ScreeningRepository
import io.reactivex.Flowable
import timber.log.Timber
import javax.inject.Inject

class MonitorCryptoPayment @Inject constructor(
    private val cryptoRepository: CryptoRepository,
    private val screeningRepository: ScreeningRepository
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

            Timber.d("Starting payment monitor for challenge ${params.challengeId}")

            // Emit current status
            emitter.onNext(PaymentStatus(challenge.status, challenge.confirmations, challenge.txHash))

            // The actual WebSocket monitoring is handled by AlchemyWebSocketService
            // This interactor is called by the service when payment events occur
            // For now, this provides the status polling mechanism

            emitter.setCancellable {
                Timber.d("Payment monitor cancelled for ${params.challengeId}")
            }
        }, io.reactivex.BackpressureStrategy.LATEST)
    }

    /**
     * Called when a matching transaction is detected by the WebSocket service.
     */
    fun onPaymentDetected(challengeId: String, txHash: String) {
        Timber.d("Payment detected for challenge $challengeId: $txHash")
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
        Timber.d("Confirmation update for $challengeId: $confirmations/${ CryptoPaymentChallenge.REQUIRED_CONFIRMATIONS}")

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
                screeningRepository.whitelistContact(
                    challenge.phoneNumber,
                    challenge.phoneNumber,
                    WhitelistedContact.WhitelistSource.CRYPTO_PAID
                )
                screeningRepository.deliverPendingMessages(challenge.phoneNumber)
                Timber.d("Contact whitelisted via crypto payment: ${challenge.phoneNumber}")
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
}
