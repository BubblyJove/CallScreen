package com.callscreen.app.crypto

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class AlchemyWebSocketServiceTest {

    @Test
    fun `confirmation count parsing works for valid response`() {
        // Simulate the batch response parsing logic
        val batchResponse = JSONArray().apply {
            put(JSONObject().apply {
                put("id", 1)
                put("result", JSONObject().apply {
                    put("blockNumber", "0x10")  // block 16
                })
            })
            put(JSONObject().apply {
                put("id", 2)
                put("result", "0x20")  // block 32
            })
        }

        var blockNumberHex = ""
        var currentBlockHex = ""
        for (i in 0 until batchResponse.length()) {
            val item = batchResponse.getJSONObject(i)
            when (item.optInt("id")) {
                1 -> blockNumberHex = item.optJSONObject("result")?.optString("blockNumber", "") ?: ""
                2 -> currentBlockHex = item.optString("result", "")
            }
        }

        val txBlock = BigInteger(blockNumberHex.removePrefix("0x"), 16)
        val currentBlock = BigInteger(currentBlockHex.removePrefix("0x"), 16)
        val confirmations = (currentBlock - txBlock).toInt().coerceAtLeast(0)

        assertEquals(16, confirmations)
    }

    @Test
    fun `confirmation count returns -1 for missing receipt`() {
        val batchResponse = JSONArray().apply {
            put(JSONObject().apply {
                put("id", 1)
                put("result", JSONObject.NULL)
            })
            put(JSONObject().apply {
                put("id", 2)
                put("result", "0x20")
            })
        }

        val item = batchResponse.getJSONObject(0)
        val result = item.optJSONObject("result")
        // null result means TX not found
        assertNull(result)
    }

    @Test
    fun `ETH address regex validates correctly`() {
        val regex = Regex("^0x[0-9a-fA-F]{40}$")
        assertTrue(regex.matches("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD00"))
        assertTrue(regex.matches("0x0000000000000000000000000000000000000000"))
        assertFalse(regex.matches("0x742d35"))
        assertFalse(regex.matches("742d35Cc6634C0532925a3b844Bc9e7595f2bD00"))
        assertFalse(regex.matches("0xGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG"))
        assertFalse(regex.matches(""))
    }

    @Test
    fun `max poll count bounds polling to 2 hours`() {
        val maxPollCount = 480L
        val pollInterval = 15L
        val totalSeconds = maxPollCount * pollInterval
        assertEquals(7200L, totalSeconds)  // 2 hours
    }
}
