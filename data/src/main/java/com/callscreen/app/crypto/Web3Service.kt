package com.callscreen.app.crypto

import com.callscreen.app.model.CryptoPaymentChallenge
import io.reactivex.Flowable

/**
 * Interface for blockchain payment monitoring.
 * Designed for WebSocket implementation now, but can be swapped
 * for a backend+FCM implementation later.
 */
interface Web3Service {

    data class PaymentEvent(
        val challengeId: String,
        val txHash: String,
        val status: CryptoPaymentChallenge.PaymentStatus,
        val confirmations: Int
    )

    /**
     * Start monitoring for a specific payment challenge.
     * Returns a Flowable that emits PaymentEvents as they occur.
     */
    fun monitorPayment(challenge: CryptoPaymentChallenge): Flowable<PaymentEvent>

    /**
     * Stop monitoring a specific challenge.
     */
    fun stopMonitoring(challengeId: String)

    /**
     * Stop all active monitoring.
     */
    fun stopAll()

    /**
     * Check if monitoring is active for a challenge.
     */
    fun isMonitoring(challengeId: String): Boolean
}
