package com.callscreen.app.crypto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoPriceOracle @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {

    companion object {
        private const val COINGECKO_URL =
            "https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=usd"
        private const val CACHE_TTL_MS = 60_000L // 1 minute
    }

    @JsonClass(generateAdapter = true)
    data class PriceResponse(
        @Json(name = "ethereum") val ethereum: EthPrice?
    )

    @JsonClass(generateAdapter = true)
    data class EthPrice(
        @Json(name = "usd") val usd: Double?
    )

    private var cachedPrice: Double? = null
    private var cacheTimestamp: Long = 0

    /**
     * Fetch current ETH/USD price. Uses a 1-minute cache.
     * Returns null on failure.
     */
    fun getEthPriceUsd(): Double? {
        val now = System.currentTimeMillis()
        cachedPrice?.let { price ->
            if (now - cacheTimestamp < CACHE_TTL_MS) {
                return price
            }
        }

        return try {
            val request = Request.Builder()
                .url(COINGECKO_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("CoinGecko API returned ${response.code}")
                return cachedPrice // Return stale cache on failure
            }

            val body = response.body?.string() ?: return cachedPrice
            val adapter = moshi.adapter(PriceResponse::class.java)
            val priceResponse = adapter.fromJson(body)
            val price = priceResponse?.ethereum?.usd

            if (price != null && price > 0) {
                cachedPrice = price
                cacheTimestamp = now
                Timber.d("ETH price updated: $$price")
            }

            price ?: cachedPrice
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch ETH price")
            cachedPrice // Return stale cache on error
        }
    }
}
