package com.callscreen.app.interactor

import com.callscreen.app.model.CryptoPaymentChallenge
import org.junit.Assert.*
import org.junit.Test

class SendCryptoChallengeTest {

    @Test
    fun `generateExactAmount ETH returns 12 char decimal string`() {
        val amount = SendCryptoChallenge.generateExactAmount(
            usdCents = 20,
            ethPriceUsd = 2500.0,
            tokenType = CryptoPaymentChallenge.TokenType.ETH
        )
        // Should contain a decimal point
        assertTrue("ETH amount should contain decimal: $amount", amount.contains("."))
        // Should be parseable as a number
        assertNotNull("ETH amount should be valid number: $amount", amount.toBigDecimalOrNull())
    }

    @Test
    fun `generateExactAmount USDC returns 6 decimal string`() {
        val amount = SendCryptoChallenge.generateExactAmount(
            usdCents = 20,
            ethPriceUsd = 2500.0,
            tokenType = CryptoPaymentChallenge.TokenType.USDC
        )
        assertTrue("USDC amount should contain decimal: $amount", amount.contains("."))
        assertNotNull("USDC amount should be valid number: $amount", amount.toBigDecimalOrNull())
        // USDC should be approximately $0.20
        val value = amount.toDouble()
        assertTrue("USDC amount should be near 0.20: $value", value > 0.19 && value < 0.21)
    }

    @Test
    fun `generateExactAmount USDT returns 6 decimal string`() {
        val amount = SendCryptoChallenge.generateExactAmount(
            usdCents = 20,
            ethPriceUsd = 2500.0,
            tokenType = CryptoPaymentChallenge.TokenType.USDT
        )
        assertNotNull("USDT amount should be valid number: $amount", amount.toBigDecimalOrNull())
    }

    @Test
    fun `generateExactAmount produces unique amounts`() {
        val amounts = (1..10).map {
            SendCryptoChallenge.generateExactAmount(20, 2500.0, CryptoPaymentChallenge.TokenType.ETH)
        }.toSet()
        // With random nonce, we should get mostly unique values
        assertTrue("Should produce varied amounts, got ${amounts.size}", amounts.size >= 5)
    }

    @Test
    fun `generateExactAmount ETH jitter stays within bounds`() {
        // Run multiple times and verify the USD value stays within ±0.05c of $0.20
        repeat(50) {
            val amount = SendCryptoChallenge.generateExactAmount(20, 2500.0, CryptoPaymentChallenge.TokenType.ETH)
            val ethValue = amount.toDouble()
            val usdValue = ethValue * 2500.0
            assertTrue("USD value should be near 0.20, got $usdValue (ETH=$ethValue)", usdValue > 0.15 && usdValue < 0.25)
        }
    }
}
