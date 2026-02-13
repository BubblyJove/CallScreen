package com.callscreen.app.util

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded in-memory cache for contact display names.
 * Evicts the oldest entry when capacity is reached (rule 3.1).
 */
object ContactCache {

    private const val MAX_CACHE_SIZE = 500
    private val cache = ConcurrentHashMap<String, String?>()

    fun getDisplayName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        cache[phoneNumber]?.let { return it }

        val name = PhoneNumberUtil.getContactName(context, phoneNumber)
        if (cache.size >= MAX_CACHE_SIZE) {
            // Evict first entry to keep within bounds
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
        cache[phoneNumber] = name
        return name
    }

    fun invalidate() {
        cache.clear()
    }
}
