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

    /** Optional callback invoked (on the calling thread) whenever a new log line is added. */
    var onChange: (() -> Unit)? = null

    fun d(tag: String, message: String) {
        addEntry("D", tag, message)
        Timber.tag(tag).d(message)
    }

    fun w(tag: String, message: String) {
        addEntry("W", tag, message)
        Timber.tag(tag).w(message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        addEntry("E", tag, "$message${throwable?.let { ": ${it.message}" } ?: ""}")
        Timber.tag(tag).e(throwable, message)
    }

    private fun addEntry(level: String, tag: String, message: String) {
        synchronized(this) {
            val timestamp = dateFormat.format(Date())
            entries.add("$timestamp $level/$tag: $message")
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
        }
        onChange?.invoke()
    }

    @Synchronized
    fun getLog(): String = entries.joinToString("\n")

    /** Alias for [getLog] used by the Compose debug screen. */
    @Synchronized
    fun getAll(): String = getLog()

    fun clear() {
        synchronized(this) {
            entries.clear()
        }
        onChange?.invoke()
    }
}
