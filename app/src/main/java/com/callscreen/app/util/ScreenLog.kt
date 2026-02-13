package com.callscreen.app.util

import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory debug log visible in the Debug Activity.
 * Thread-safe ring buffer with a max of 500 entries.
 */
object ScreenLog {

    private const val MAX_ENTRIES = 500
    private val entries = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun d(tag: String, message: String) {
        add("D", tag, message)
        Timber.tag(tag).d(message)
    }

    @Synchronized
    fun w(tag: String, message: String) {
        add("W", tag, message)
        Timber.tag(tag).w(message)
    }

    @Synchronized
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        add("E", tag, "$message${throwable?.let { ": ${it.message}" } ?: ""}")
        Timber.tag(tag).e(throwable, message)
    }

    @Synchronized
    private fun add(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        entries.add("$timestamp $level/$tag: $message")
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    @Synchronized
    fun getLog(): String = entries.joinToString("\n")

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
