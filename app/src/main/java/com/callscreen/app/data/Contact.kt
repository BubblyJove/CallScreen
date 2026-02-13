package com.callscreen.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelisted_contacts")
data class WhitelistedContact(
    @PrimaryKey
    val phoneNumber: String,
    val displayName: String? = null,
    val whitelistedAt: Long = System.currentTimeMillis(),
    val source: WhitelistSource = WhitelistSource.CHALLENGE_PASSED
)

enum class WhitelistSource {
    CONTACTS,           // imported from phone contacts
    CHALLENGE_PASSED,   // passed SMS challenge
    MANUAL              // manually added by user
}
