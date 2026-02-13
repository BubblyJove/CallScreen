package com.callscreen.app.interactor

import android.content.Context
import android.provider.ContactsContract
import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.repository.ScreeningRepository
import io.reactivex.Flowable
import javax.inject.Inject

class CheckNumberTrusted @Inject constructor(
    private val context: Context,
    private val screeningRepository: ScreeningRepository
) : Interactor<CheckNumberTrusted.Params>() {

    data class Params(val phoneNumber: String)

    override fun buildObservable(params: Params): Flowable<Boolean> {
        return Flowable.fromCallable {
            // Check whitelist DB first
            if (screeningRepository.isWhitelisted(params.phoneNumber)) {
                return@fromCallable true
            }

            // Check device contacts
            if (isInDeviceContacts(params.phoneNumber)) {
                // Auto-whitelist with source=CONTACTS
                val displayName = getContactName(params.phoneNumber) ?: params.phoneNumber
                screeningRepository.whitelistContact(
                    params.phoneNumber,
                    displayName,
                    WhitelistedContact.WhitelistSource.CONTACTS
                )
                return@fromCallable true
            }

            false
        }
    }

    private fun isInDeviceContacts(phoneNumber: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneNumber)
            .build()

        return try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
                it.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun getContactName(phoneNumber: String): String? {
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
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
