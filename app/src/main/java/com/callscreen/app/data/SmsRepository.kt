package com.callscreen.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.callscreen.app.util.ContactCache
import com.callscreen.app.util.ScreenLog
import com.klinker.android.send_message.Settings as KlinkerSettings
import com.klinker.android.send_message.Message as KlinkerMessage
import com.klinker.android.send_message.Transaction

/**
 * Repository for SMS/MMS conversations using system content providers.
 *
 * Conversation listing follows Quik's pattern of querying system SMS
 * content providers for thread metadata and snippets.
 *
 * Message sending uses Klinker's android-smsmms Transaction class
 * from the Quik submodule for reliable SMS/MMS delivery.
 */
class SmsRepository(private val context: Context) {

    companion object {
        private const val TAG = "SmsRepository"
        private const val MAX_CONVERSATIONS = 500
        private const val MAX_MESSAGES_PER_THREAD = 1000
        private const val MAX_CURSOR_ROWS = 10_000
    }

    fun getConversations(): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        try {
            loadConversations(conversations)
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Failed to load conversations", e)
        }
        return conversations
    }

    /**
     * Single-pass cursor scan: build conversations and count unreads simultaneously.
     * Bounded by [MAX_CURSOR_ROWS] to prevent unbounded memory use (rule 1.2, 3.1).
     */
    private fun loadConversations(out: MutableList<Conversation>) {
        val smsUri = Uri.parse("content://sms")
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )

        val cursor: Cursor = context.contentResolver.query(
            smsUri, projection, null, null,
            "${Telephony.Sms.DATE} DESC"
        ) ?: return

        cursor.use { c ->
            val threadIdIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
            val readIdx = c.getColumnIndex(Telephony.Sms.READ)

            if (threadIdIdx < 0 || addressIdx < 0 || readIdx < 0) return

            // First pass: collect thread metadata + unread counts (bounded)
            data class ThreadInfo(
                val address: String,
                val snippet: String,
                val timestamp: Long,
                var unreadCount: Int = 0
            )

            val threadMap = LinkedHashMap<Long, ThreadInfo>()
            var rowCount = 0

            while (c.moveToNext() && rowCount < MAX_CURSOR_ROWS) {
                rowCount++
                val threadId = c.getLong(threadIdIdx)
                val isUnread = c.getInt(readIdx) == 0

                val existing = threadMap[threadId]
                if (existing != null) {
                    if (isUnread) existing.unreadCount++
                } else if (threadMap.size < MAX_CONVERSATIONS) {
                    val address = c.getString(addressIdx) ?: continue
                    threadMap[threadId] = ThreadInfo(
                        address = address,
                        snippet = c.getString(bodyIdx) ?: "",
                        timestamp = c.getLong(dateIdx),
                        unreadCount = if (isUnread) 1 else 0
                    )
                }
            }

            for ((threadId, info) in threadMap) {
                out.add(
                    Conversation(
                        threadId = threadId,
                        address = info.address,
                        displayName = ContactCache.getDisplayName(context, info.address),
                        snippet = info.snippet,
                        timestamp = info.timestamp,
                        unreadCount = info.unreadCount
                    )
                )
            }
        }
    }

    fun getMessagesForThread(threadId: Long): List<SmsMessage> {
        if (threadId <= 0) return emptyList()

        val messages = mutableListOf<SmsMessage>()
        try {
            val uri = Uri.parse("content://sms")
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.STATUS
            )
            context.contentResolver.query(
                uri, projection,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < MAX_MESSAGES_PER_THREAD) {
                    messages.add(cursor.toSmsMessage())
                    count++
                }
            }
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Failed to load thread $threadId", e)
        }
        return messages
    }

    fun writeSentMessage(address: String, body: String) {
        if (address.isBlank()) return
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Failed to write sent message", e)
        }
    }

    fun markThreadAsRead(threadId: Long) {
        if (threadId <= 0) return
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.update(
                Uri.parse("content://sms"),
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Failed to mark thread $threadId as read", e)
        }
    }

    /**
     * Send an SMS via Klinker's Transaction from Quik's android-smsmms module.
     * Provides reliable delivery with proper sent/delivered tracking.
     */
    fun sendMessage(address: String, body: String) {
        if (address.isBlank() || body.isBlank()) return
        try {
            val settings = KlinkerSettings()
            settings.useSystemSending = true

            val transaction = Transaction(context, settings)
            val message = KlinkerMessage(body, address)
            transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)

            ScreenLog.d(TAG, "Sent message via Klinker Transaction to $address")
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Klinker send failed to $address", e)
        }
    }

    private fun Cursor.toSmsMessage(): SmsMessage {
        return SmsMessage(
            id = getLong(getColumnIndexOrThrow(Telephony.Sms._ID)),
            threadId = getLong(getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)),
            address = getString(getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "",
            body = getString(getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
            timestamp = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE)),
            type = getInt(getColumnIndexOrThrow(Telephony.Sms.TYPE)),
            read = getInt(getColumnIndexOrThrow(Telephony.Sms.READ)) == 1,
            status = getInt(getColumnIndexOrThrow(Telephony.Sms.STATUS))
        )
    }
}
