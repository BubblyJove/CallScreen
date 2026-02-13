package com.callscreen.app.model

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey

open class PendingScreenedMessage : RealmObject() {

    enum class MessageType { SMS, MMS, CALL }
    enum class MessageStatus { HELD, DELIVERED, REJECTED }

    @PrimaryKey var id: Long = 0
    @Index var phoneNumber: String = ""
    var body: String = ""
    var timestamp: Long = 0
    var typeString: String = MessageType.SMS.name
    var statusString: String = MessageStatus.HELD.name

    var type: MessageType
        get() = MessageType.valueOf(typeString)
        set(value) { typeString = value.name }

    var status: MessageStatus
        get() = MessageStatus.valueOf(statusString)
        set(value) { statusString = value.name }
}
