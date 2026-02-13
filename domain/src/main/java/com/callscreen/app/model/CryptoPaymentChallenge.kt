package com.callscreen.app.model

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import java.util.UUID

open class CryptoPaymentChallenge : RealmObject() {

    enum class TokenType(val contractAddress: String?, val decimals: Int) {
        ETH(null, 18),
        USDC("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", 6),
        USDT("0xdac17f958d2ee523a2206206994597c13d831ec7", 6)
    }

    enum class PaymentStatus { PENDING, CONFIRMING, CONFIRMED, EXPIRED }

    @PrimaryKey var id: String = UUID.randomUUID().toString()
    @Index var phoneNumber: String = ""
    var walletAddress: String = ""
    var tokenTypeString: String = TokenType.ETH.name
    var exactAmount: String = "" // Stored as string to preserve decimal precision
    var usdCents: Int = 0
    var createdAt: Long = 0
    var expiresAt: Long = 0
    var statusString: String = PaymentStatus.PENDING.name
    var confirmations: Int = 0
    var txHash: String = ""

    var tokenType: TokenType
        get() = TokenType.valueOf(tokenTypeString)
        set(value) { tokenTypeString = value.name }

    var status: PaymentStatus
        get() = PaymentStatus.valueOf(statusString)
        set(value) { statusString = value.name }

    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt

    companion object {
        const val REQUIRED_CONFIRMATIONS = 12
        const val TTL_MS = 60 * 60 * 1000L // 1 hour
    }
}
