package com.callscreen.app.feature.messageutils

import com.callscreen.app.repository.MessageRepository

data class MessageUtilsState(
    val autoDeduplicateMessages: Boolean = false,
    val deduplicationProgress: MessageRepository.DeduplicationProgress = MessageRepository.DeduplicationProgress.Idle,

    val autoDelete: Int = 0,
)
