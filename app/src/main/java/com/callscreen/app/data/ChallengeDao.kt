package com.callscreen.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChallengeDao {

    @Query("SELECT * FROM challenge_states WHERE phoneNumber = :number")
    suspend fun getChallenge(number: String): ChallengeState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(challenge: ChallengeState)

    @Update
    suspend fun update(challenge: ChallengeState)

    @Query("DELETE FROM challenge_states WHERE phoneNumber = :number")
    suspend fun delete(number: String)

    @Query("DELETE FROM challenge_states WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())
}
