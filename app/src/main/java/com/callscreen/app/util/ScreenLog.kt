package com.callscreen.app.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory log buffer visible in the app's Debug tab.
 * Also forwards to logcat. Thread-safe via synchronized blocks (rule 4.1).
 */
object ScreenLog {

    private const val MAX_ENTRIES = 300
    private val entries = mutableListOf<String>()
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Listener notified on every new entry (UI recomposition trigger). */
    @Volatile
    var onChange: (() -> Unit)? = null

    fun d(tag: String, msg: String) {
        append("D", tag, msg)
        Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        append("W", tag, msg)
        Log.w(tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val full = if (t != null) "$msg — ${t::class.simpleName}: ${t.message}" else msg
        append("E", tag, full)
        Log.e(tag, msg, t)
    }

    fun getAll(): String = synchronized(entries) {
        entries.joinToString("\n")
    }

    fun clear() = synchronized(entries) {
        entries.clear()
        onChange?.invoke()
    }

    private fun append(level: String, tag: String, msg: String) {
        val ts = sdf.format(Date())
        synchronized(entries) {
            entries.add("$ts $level/$tag: $msg")
            if (entries.size > MAX_ENTRIES) entries.removeAt(0)
        }
        onChange?.invoke()
    }
}
