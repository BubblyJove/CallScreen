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
import android.text.format.DateFormat
import com.callscreen.app.common.util.extensions.isSameDay
import com.callscreen.app.common.util.extensions.isSameWeek
import com.callscreen.app.common.util.extensions.isSameYear
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DateFormatter @Inject constructor(val context: Context) {

    // Perf: pre-compile the whitespace+AM/PM regex once
    companion object {
        private val AM_PM_REGEX = "\\s+a".toRegex()
    }

    // Perf: cache SimpleDateFormat instances by pattern string.
    // getFormatter() is called on every message render in conversation lists,
    // and SimpleDateFormat construction + getBestDateTimePattern are expensive.
    private val formatterCache = HashMap<String, SimpleDateFormat>(12)

    /**
     * Formats the [pattern] correctly for the current locale, and replaces 12 hour format with
     * 24 hour format if necessary
     */
    private fun getFormatter(pattern: String): SimpleDateFormat {
        return formatterCache.getOrPut(pattern) {
            var formattedPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), pattern)

            if (DateFormat.is24HourFormat(context)) {
                formattedPattern = formattedPattern
                        .replace("h", "HH")
                        .replace("K", "HH")
                        .replace(AM_PM_REGEX, "")
            }

            SimpleDateFormat(formattedPattern, Locale.getDefault())
        }
    }

    fun getDetailedTimestamp(date: Long): String {
        return getFormatter("M/d/y, h:mm:ss a").format(date)
    }

    fun getTimestamp(date: Long): String {
        return getFormatter("h:mm a").format(date)
    }

    fun getMessageTimestamp(date: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance()
        then.timeInMillis = date

        return when {
            now.isSameDay(then) -> getFormatter("h:mm a")
            now.isSameWeek(then) -> getFormatter("E h:mm a")
            now.isSameYear(then) -> getFormatter("MMM d, h:mm a")
            else -> getFormatter("MMM d yyyy, h:mm a")
        }.format(date)
    }

    fun getConversationTimestamp(date: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance()
        then.timeInMillis = date

        return when {
            now.isSameDay(then) -> getFormatter("h:mm a")
            now.isSameWeek(then) -> getFormatter("E")
            now.isSameYear(then) -> getFormatter("MMM d")
            else -> getFormatter("MM/d/yy")
        }.format(date)
    }

    fun getScheduledTimestamp(date: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance()
        then.timeInMillis = date

        return when {
            now.isSameDay(then) -> getFormatter("h:mm a")
            now.isSameYear(then) -> getFormatter("MMM d h:mm a")
            else -> getFormatter("MMM d yyyy h:mm a")
        }.format(date)
    }

}
