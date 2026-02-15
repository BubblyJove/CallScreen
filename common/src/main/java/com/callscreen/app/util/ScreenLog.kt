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

    // Perf: ArrayDeque is O(1) for removeFirst vs ArrayList's O(n) shift
    private val entries = ArrayDeque<String>(MAX_ENTRIES + 1)

    // Perf: reuse Date instance to avoid allocation per log call
    private val reusableDate = Date()

    // Perf: pre-compiled date formatter (SimpleDateFormat is not thread-safe,
    // but we synchronize all access anyway)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // Perf: pre-allocated StringBuilder to avoid repeated allocation;
    // typical log line is ~80-120 chars
    private val lineBuilder = StringBuilder(128)

    /** Optional callback invoked (on the calling thread) whenever a new log line is added. */
    @Volatile
    var onChange: (() -> Unit)? = null

    fun d(tag: String, message: String) {
        addEntry('D', tag, message)
        Timber.tag(tag).d(message)
    }

    fun w(tag: String, message: String) {
        addEntry('W', tag, message)
        Timber.tag(tag).w(message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Build error message without shared lineBuilder to avoid race condition
        // (lineBuilder is also used inside addEntry's synchronized block)
        val fullMessage = if (throwable != null) {
            "$message: ${throwable.message}"
        } else {
            message
        }
        addEntry('E', tag, fullMessage)
        Timber.tag(tag).e(throwable, message)
    }

    // Perf: use Char instead of String for level to avoid String object creation
    private fun addEntry(level: Char, tag: String, message: String) {
        synchronized(this) {
            // Perf: reuse Date to avoid allocation
            reusableDate.time = System.currentTimeMillis()

            lineBuilder.clear()
            lineBuilder.append(dateFormat.format(reusableDate))
                .append(' ')
                .append(level)
                .append('/')
                .append(tag)
                .append(": ")
                .append(message)

            entries.addLast(lineBuilder.toString())

            // Perf: ArrayDeque.removeFirst() is O(1) amortized
            if (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
        onChange?.invoke()
    }

    @Synchronized
    fun getLog(): String {
        // Perf: pre-size StringBuilder based on estimated total length
        val estimatedSize = entries.size * 80
        val sb = StringBuilder(estimatedSize)
        val iter = entries.iterator()
        if (iter.hasNext()) {
            sb.append(iter.next())
            while (iter.hasNext()) {
                sb.append('\n')
                sb.append(iter.next())
            }
        }
        return sb.toString()
    }

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
