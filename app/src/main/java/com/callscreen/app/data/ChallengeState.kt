package com.callscreen.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "challenge_states")
data class ChallengeState(
    @PrimaryKey
    val phoneNumber: String,
    val challengeQuestion: String,
    val expectedAnswer: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + CHALLENGE_TTL_MS,
    val attempts: Int = 0,
    val type: ChallengeType = ChallengeType.MATH
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt

    companion object {
        const val CHALLENGE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val MAX_ATTEMPTS = 3
    }
}

enum class ChallengeType {
    MATH,       // e.g. "What is 4 + 3?"
    KEYWORD,    // e.g. "Reply with the word: CONNECT"
    CODE        // e.g. "Reply with code: 7294"
}
