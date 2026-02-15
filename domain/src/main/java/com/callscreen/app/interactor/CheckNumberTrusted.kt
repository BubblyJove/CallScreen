package com.callscreen.app.interactor

import android.content.Context
import android.provider.ContactsContract
import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.repository.ScreeningRepository
import com.callscreen.app.util.ScreenLog
import com.callscreen.app.util.maskPhone
import io.reactivex.Flowable
import javax.inject.Inject

class CheckNumberTrusted @Inject constructor(
    private val context: Context,
    private val screeningRepository: ScreeningRepository
) : Interactor<CheckNumberTrusted.Params>() {

    data class Params(val phoneNumber: String)

    companion object {
        private const val TAG = "CheckTrusted"
        private val NAME_PROJECTION = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
    }

    override fun buildObservable(params: Params): Flowable<Boolean> {
        return Flowable.fromCallable {
            // Check whitelist DB first
            if (screeningRepository.isWhitelisted(params.phoneNumber)) {
                return@fromCallable true
            }

            // Perf: single content resolver query replaces the previous two-query pattern
            // (isInDeviceContacts + getContactName). Gets name directly if contact exists.
            val contactName = getContactNameIfExists(params.phoneNumber)
            if (contactName != null) {
                // Auto-whitelist with source=CONTACTS
                screeningRepository.whitelistContact(
                    params.phoneNumber,
                    contactName,
                    WhitelistedContact.WhitelistSource.CONTACTS
                )
                return@fromCallable true
            }

            false
        }
    }

    /**
     * Perf: single query to check existence and get display name simultaneously.
     * Returns display name if found, null otherwise.
     */
    private fun getContactNameIfExists(phoneNumber: String): String? {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneNumber)
            .build()

        return try {
            context.contentResolver.query(uri, NAME_PROJECTION, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // Perf: use column index 0 directly — we know the projection has exactly one column
                    cursor.getString(0) ?: phoneNumber
                } else null
            }
        } catch (e: Exception) {
            ScreenLog.w(TAG, "Failed to check device contacts for ${maskPhone(phoneNumber)}")
            null
        }
    }
}
