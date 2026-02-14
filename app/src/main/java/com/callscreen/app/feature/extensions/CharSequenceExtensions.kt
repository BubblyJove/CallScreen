package com.callscreen.app.feature.extensions

import android.text.Spannable
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiCompat.REPLACE_STRATEGY_ALL
import androidx.emoji2.text.EmojiSpan

// Perf: pre-compile regex pattern — this function may be called per-message in a list
private val WHITESPACE_REGEX = Regex("[\\s\n\r]")

fun CharSequence.isEmojiOnly(considerWhitespace: Boolean = false): Boolean {
    val cs =
        if (considerWhitespace) this
        else this.replace(WHITESPACE_REGEX, "")

    if (cs.isEmpty())
        return false

    return when (val spannable = EmojiCompat.get().process(
        cs,
        0,
        (cs.length - 1),
        Int.MAX_VALUE,
        REPLACE_STRATEGY_ALL
    )) {
        is Spannable -> {
            // Perf: use sumOf with manual span length to avoid fold lambda boxing
            val spans = spannable.getSpans(0, (spannable.length - 1), EmojiSpan::class.java)
            var totalSpanned = 0
            for (span in spans) {
                totalSpanned += spannable.getSpanEnd(span) - spannable.getSpanStart(span)
            }
            totalSpanned == cs.length
        }
        else -> false
    }
}
