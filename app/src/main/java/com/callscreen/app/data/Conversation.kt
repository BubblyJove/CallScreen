package com.callscreen.app.data

/**
 * Conversation summary for the conversation list.
 * Populated from the system MMS-SMS content provider, following Quik's
 * content://mms-sms/conversations pattern.
 */
data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String?,
    val snippet: String,
    val timestamp: Long,
    val unreadCount: Int
)

/**
 * Individual message within a thread.
 * Populated from content://mms-sms/complete-conversations (Quik's pattern)
 * or content://sms for SMS-only threads.
 */
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val type: Int,
    val read: Boolean,
    val status: Int,
    val isMms: Boolean = false,
    val mmsSubject: String? = null
) {
    /** Inbox type = 1 for SMS, MESSAGE_BOX_INBOX = 1 for MMS */
    val isIncoming: Boolean get() = type == 1
    /** Sent type = 2 for SMS, MESSAGE_BOX_SENT = 2 for MMS */
    val isOutgoing: Boolean get() = type == 2
}
