package com.callscreen.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    if (diff < TimeUnit.MINUTES.toMillis(1)) return "Just now"
    if (diff < TimeUnit.HOURS.toMillis(1)) return "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
    if (diff < TimeUnit.DAYS.toMillis(1)) return "${TimeUnit.MILLISECONDS.toHours(diff)}h"

    val cal = Calendar.getInstance()
    val nowDay = cal.get(Calendar.DAY_OF_YEAR)
    val nowYear = cal.get(Calendar.YEAR)

    cal.timeInMillis = timestamp
    val msgDay = cal.get(Calendar.DAY_OF_YEAR)
    val msgYear = cal.get(Calendar.YEAR)

    if (nowYear == msgYear && nowDay - msgDay == 1) return "Yesterday"
    if (nowYear == msgYear && nowDay - msgDay < 7) {
        return SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
    }
    return if (nowYear == msgYear) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
