package com.callscreen.app.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.Conversation
import com.callscreen.app.data.MessageStatus
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.data.SmsMessage
import com.callscreen.app.data.SmsRepository
import com.callscreen.app.data.WhitelistSource
import com.callscreen.app.data.WhitelistedContact
import com.callscreen.app.util.PhoneNumberUtil
import com.callscreen.app.util.ScreenLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val smsRepository = SmsRepository(application)

    val heldMessages = db.pendingMessageDao().getHeldMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val deliveredMessages = db.pendingMessageDao().getDeliveredMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val whitelistedContacts = db.contactDao().getAllWhitelisted()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val heldCount = db.pendingMessageDao().heldCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val whitelistCount = db.contactDao().count()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentThread = MutableStateFlow<List<SmsMessage>>(emptyList())
    val currentThread: StateFlow<List<SmsMessage>> = _currentThread.asStateFlow()

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            loadConversations()
        }
    }

    init {
        loadConversations()
        try {
            application.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI, true, smsObserver
            )
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Failed to register SMS content observer", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().contentResolver
                .unregisterContentObserver(smsObserver)
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Failed to unregister SMS content observer", e)
        }
    }

    fun loadConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            _conversations.value = smsRepository.getConversations()
        }
    }

    fun openThread(threadId: Long) {
        if (threadId <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            _currentThread.value = smsRepository.getMessagesForThread(threadId)
            smsRepository.markThreadAsRead(threadId)
        }
    }

    /**
     * Send a reply using Klinker's Transaction from the Quik android-smsmms module.
     */
    fun sendReply(address: String, body: String) {
        if (address.isBlank() || body.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            smsRepository.sendMessage(address, body)
        }
    }

    fun approveMessage(message: PendingMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pendingMessageDao().update(message.copy(status = MessageStatus.DELIVERED))
            db.contactDao().whitelist(
                WhitelistedContact(
                    phoneNumber = message.phoneNumber,
                    source = WhitelistSource.MANUAL
                )
            )
        }
    }

    fun rejectMessage(message: PendingMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pendingMessageDao().update(message.copy(status = MessageStatus.REJECTED))
        }
    }

    fun deleteMessage(id: Long) {
        if (id <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            db.pendingMessageDao().deleteById(id)
        }
    }

    fun addToWhitelist(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.contactDao().whitelist(
                WhitelistedContact(
                    phoneNumber = PhoneNumberUtil.normalize(phoneNumber),
                    source = WhitelistSource.MANUAL
                )
            )
        }
    }

    fun removeFromWhitelist(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.contactDao().removeByNumber(PhoneNumberUtil.normalize(phoneNumber))
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
