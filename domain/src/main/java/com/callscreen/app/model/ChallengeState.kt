package com.callscreen.app.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class ChallengeState : RealmObject() {

    enum class ChallengeType { MATH, CRYPTO }

    @PrimaryKey var phoneNumber: String = ""
    var challengeQuestion: String = ""
    var expectedAnswer: String = ""
    var createdAt: Long = 0
    var expiresAt: Long = 0
    var attempts: Int = 0
    var typeString: String = ChallengeType.MATH.name

    var type: ChallengeType
        get() = try { ChallengeType.valueOf(typeString) } catch (e: IllegalArgumentException) { ChallengeType.MATH }
        set(value) { typeString = value.name }

    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    fun hasAttemptsRemaining(): Boolean = attempts < MAX_ATTEMPTS

    companion object {
        const val MAX_ATTEMPTS = 3
        const val TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}
