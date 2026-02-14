package com.callscreen.app.util

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils as AndroidPhoneUtils
import com.callscreen.app.CallScreenApp
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil.PhoneNumberFormat

object PhoneNumberUtil {

    private const val DEFAULT_REGION = "US"
    private const val MAX_DISPLAY_NAME_LENGTH = 100

    /** Pre-compiled regex for stripping non-phone characters (rule 3.2). */
    private val STRIP_REGEX = Regex("[^+\\d]")

    fun normalize(number: String): String {
        val util = CallScreenApp.phoneNumberUtil ?: return fallbackNormalize(number)
        return try {
            val parsed = util.parse(number, DEFAULT_REGION)
            util.format(parsed, PhoneNumberFormat.E164)
        } catch (_: NumberParseException) {
            fallbackNormalize(number)
        }
    }

    private fun fallbackNormalize(number: String): String {
        return AndroidPhoneUtils.normalizeNumber(number)
            ?: number.replace(STRIP_REGEX, "")
    }

    fun isInDeviceContacts(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneNumber)
            .build()

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to look up contact for %s", phoneNumber)
            false
        }
    }

    fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneNumber)
            .build()

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            ContactsContract.PhoneLookup.DISPLAY_NAME
                        )
                    )?.take(MAX_DISPLAY_NAME_LENGTH)
                } else null
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to get contact name for %s", phoneNumber)
            null
        }
    }
}
