package com.callscreen.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages")
data class PendingMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.SMS,
    val status: MessageStatus = MessageStatus.HELD,
    val isIncoming: Boolean = true
)

enum class MessageType {
    SMS, MMS
}

enum class MessageStatus {
    HELD,       // waiting for challenge response
    DELIVERED,  // challenge passed, message shown to user
    REJECTED    // challenge expired or failed
}
