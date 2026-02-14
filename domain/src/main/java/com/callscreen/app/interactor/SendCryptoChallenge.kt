package com.callscreen.app.interactor

import com.callscreen.app.model.ChallengeState
import com.callscreen.app.model.CryptoPaymentChallenge
import com.callscreen.app.repository.CryptoRepository
import com.callscreen.app.repository.MessageRepository
import com.callscreen.app.repository.ScreeningRepository
import io.reactivex.Flowable
import timber.log.Timber
import javax.inject.Inject

class SendCryptoChallenge @Inject constructor(
    private val cryptoRepository: CryptoRepository,
    private val screeningRepository: ScreeningRepository,
    private val messageRepository: MessageRepository
) : Interactor<SendCryptoChallenge.Params>() {

    data class Params(
        val phoneNumber: String,
        val ethPriceUsd: Double // Current ETH/USD price, fetched by caller
    )

    override fun buildObservable(params: Params): Flowable<CryptoPaymentChallenge> {
        return Flowable.fromCallable {
            val tokenType = cryptoRepository.getPreferredTokenType()
            val usdCents = cryptoRepository.getChallengePrice()
            val walletAddress = cryptoRepository.getWalletAddress(tokenType)

            require(walletAddress.isNotBlank()) { "Wallet address not configured for $tokenType" }

            // Check for existing active challenge
            val existing = cryptoRepository.getActivePaymentChallenge(params.phoneNumber)
            if (existing != null && !existing.isExpired()) {
                Timber.d("Reusing existing crypto challenge for ${params.phoneNumber}")
                sendChallengeMessage(params.phoneNumber, existing)
                return@fromCallable existing
            }

            // Generate exact amount with nonce for uniqueness
            val exactAmount = generateExactAmount(usdCents, params.ethPriceUsd, tokenType)

            val challenge = cryptoRepository.createPaymentChallenge(
                phoneNumber = params.phoneNumber,
                usdCents = usdCents,
                tokenType = tokenType,
                walletAddress = walletAddress,
                exactAmount = exactAmount
            )

            // Save ChallengeState so screening tabs and ValidateChallengeResponse can find it
            val now = System.currentTimeMillis()
            val challengeState = ChallengeState().apply {
                this.phoneNumber = params.phoneNumber
                this.challengeQuestion = "Send $exactAmount ${tokenType.name} to $walletAddress"
                this.expectedAnswer = exactAmount
                this.createdAt = now
                this.expiresAt = now + CryptoPaymentChallenge.TTL_MS
                this.attempts = 0
                this.type = ChallengeState.ChallengeType.CRYPTO
            }
            screeningRepository.saveChallengeState(challengeState)

            sendChallengeMessage(params.phoneNumber, challenge)

            Timber.d("Sent crypto challenge to ${params.phoneNumber}: $exactAmount ${tokenType.name} to $walletAddress")
            challenge
        }
    }

    private fun sendChallengeMessage(phoneNumber: String, challenge: CryptoPaymentChallenge) {
        val tokenName = challenge.tokenType.name
        val body = buildString {
            append("To verify your identity, send exactly ")
            append("${challenge.exactAmount} $tokenName ")
            append("to: ${challenge.walletAddress}\n\n")
            append("Amount must match EXACTLY (including all decimal places). ")
            append("Challenge expires in 1 hour. ")
            append("Payment will be confirmed after 12 block confirmations.")
        }

        messageRepository.sendNewMessages(
            subId = -1,
            toAddresses = listOf(phoneNumber),
            body = body,
            attachments = emptyList(),
            sendAsGroup = false
        )
    }

    companion object {
        /**
         * Generate exact payment amount with random nonce digits for uniqueness.
         *
         * For ETH: Convert USD cents to ETH, add 6-digit random nonce to last decimal places.
         * Example: $0.20 at $2500/ETH → 0.00008 ETH + nonce → "0.000080847231"
         *
         * For USDC/USDT: 1:1 with USD, add nonce digits.
         * Example: $0.20 → 0.20 + nonce → "0.200847"
         */
        fun generateExactAmount(usdCents: Int, ethPriceUsd: Double, tokenType: CryptoPaymentChallenge.TokenType): String {
            // Randomize within ±0.05 cents of the configured fee for uniqueness
            val jitterCents = (Math.random() - 0.5) * 0.001 // ±$0.0005
            val usdAmount = usdCents / 100.0 + jitterCents
            val nonce = (1000..9999).random()

            return when (tokenType) {
                CryptoPaymentChallenge.TokenType.ETH -> {
                    val ethAmount = usdAmount / ethPriceUsd
                    // Format with high precision, append nonce digits
                    val baseStr = String.format("%.8f", ethAmount)
                    "${baseStr}${nonce}"
                }
                CryptoPaymentChallenge.TokenType.USDC,
                CryptoPaymentChallenge.TokenType.USDT -> {
                    // Format with 4 decimal places, append nonce
                    val baseStr = String.format("%.4f", usdAmount)
                    "${baseStr}${nonce}"
                }
            }
        }
    }
}
