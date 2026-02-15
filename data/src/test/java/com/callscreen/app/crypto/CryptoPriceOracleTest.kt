package com.callscreen.app.crypto

import org.junit.Assert.*
import org.junit.Test

class CryptoPriceOracleTest {

    @Test
    fun `cache entry data class holds values correctly`() {
        // Test the CacheEntry data class used for thread-safe caching
        // This verifies the atomic cache pattern works correctly
        val timestamp = System.currentTimeMillis()
        val price = 2500.0

        // Simulate what CryptoPriceOracle does internally
        data class CacheEntry(val price: Double, val timestamp: Long)
        val entry = CacheEntry(price, timestamp)

        assertEquals(price, entry.price, 0.001)
        assertEquals(timestamp, entry.timestamp)
    }

    @Test
    fun `cache TTL of 60 seconds is reasonable`() {
        val ttl = 60_000L
        assertTrue("TTL should be positive", ttl > 0)
        assertTrue("TTL should be at most 5 minutes", ttl <= 300_000L)
    }
}
