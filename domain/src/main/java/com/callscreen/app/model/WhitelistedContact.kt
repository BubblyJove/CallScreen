package com.callscreen.app.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class WhitelistedContact : RealmObject() {

    enum class WhitelistSource { CONTACTS, CHALLENGE_PASSED, MANUAL, CRYPTO_PAID }

    @PrimaryKey var phoneNumber: String = ""
    var displayName: String = ""
    var whitelistedAt: Long = 0
    var sourceString: String = WhitelistSource.MANUAL.name

    var source: WhitelistSource
        get() = WhitelistSource.valueOf(sourceString)
        set(value) { sourceString = value.name }
}
