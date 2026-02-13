package com.callscreen.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callscreen.app.data.AppDatabase
import com.callscreen.app.data.MessageStatus
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.data.WhitelistSource
import com.callscreen.app.data.WhitelistedContact
import com.callscreen.app.util.PhoneNumberUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

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

    fun approveMessage(message: PendingMessage) {
        viewModelScope.launch {
            // Deliver the message and whitelist the sender
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
        viewModelScope.launch {
            db.pendingMessageDao().update(message.copy(status = MessageStatus.REJECTED))
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            db.pendingMessageDao().deleteById(id)
        }
    }

    fun addToWhitelist(phoneNumber: String) {
        viewModelScope.launch {
            db.contactDao().whitelist(
                WhitelistedContact(
                    phoneNumber = PhoneNumberUtil.normalize(phoneNumber),
                    source = WhitelistSource.MANUAL
                )
            )
        }
    }

    fun removeFromWhitelist(phoneNumber: String) {
        viewModelScope.launch {
            db.contactDao().removeByNumber(PhoneNumberUtil.normalize(phoneNumber))
        }
    }
}
