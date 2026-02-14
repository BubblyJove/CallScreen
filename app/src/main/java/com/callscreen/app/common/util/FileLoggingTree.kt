/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.callscreen.app.common.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.callscreen.app.util.FileUtils
import com.callscreen.app.util.Preferences
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Based off Vipin Kumar's FileLoggingTree: https://medium.com/@vicky7230/file-logging-with-timber-4e63a1b86a66
 */
@Singleton
class FileLoggingTree @Inject constructor(
    private val prefs: Preferences,
    private val context: Context
) : Timber.DebugTree() {
    companion object {
        val TAG: String? = FileLoggingTree::class.simpleName

        // Perf: pre-allocate priority char lookup to avoid when-expression per log call
        private val PRIORITY_CHARS = charArrayOf(
            '?', '?', 'V', 'D', 'I', 'W', 'E', 'A'
        )
    }

    // Perf: cache SimpleDateFormat instances — they are expensive to construct
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Perf: reuse Date and StringBuilder to avoid allocation per log call
    // (safe because all access is synchronized)
    private val reusableDate = Date()
    private val logBuilder = StringBuilder(256)

    private var logFileUri: Uri? = null

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!prefs.logging.get()) return

        Schedulers.io().scheduleDirect {
            synchronized(this) {    // one thread can access file at a time
                val now = System.currentTimeMillis()
                reusableDate.time = now

                // Perf: use StringBuilder instead of string template interpolation
                logBuilder.clear()
                logBuilder.append(timestampFormat.format(reusableDate))
                    .append(' ')
                    // Perf: array lookup instead of when-expression
                    .append(if (priority in PRIORITY_CHARS.indices) PRIORITY_CHARS[priority] else '?')
                    .append('/')
                    .append(tag)
                    .append(": ")
                    .append(message)

                if (t != null) {
                    logBuilder.append(Log.getStackTraceString(t))
                }
                logBuilder.append('\n')

                // if uri of log file not yet determined, get one now
                if (logFileUri == null) {
                    val filename = "Quik-log-${dateFormat.format(reusableDate)}.log"

                    val (uri, e) = FileUtils.create(
                        FileUtils.Location.Downloads,
                        context,
                        filename,
                        "text/plain"
                    )
                    if (e is Exception)
                        Log.e(TAG, "Error opening log file", e)
                    else
                        logFileUri = uri
                }

                logFileUri?.let {
                    // Perf: convert StringBuilder to bytes directly
                    val bytes = logBuilder.toString().toByteArray()
                    val e = FileUtils.append(context, it, bytes)
                    if (e is FileNotFoundException)
                        Log.e(TAG, "Log file went away", e)
                    else if (e is Exception)
                        Log.e(TAG, "Error while logging into file", e)
                }
            }
        }
    }
}
