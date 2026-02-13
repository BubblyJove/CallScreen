package com.callscreen.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMessageDao {

    @Query("SELECT * FROM pending_messages WHERE status = 'HELD' ORDER BY timestamp DESC")
    fun getHeldMessages(): Flow<List<PendingMessage>>

    @Query("SELECT * FROM pending_messages WHERE status = 'DELIVERED' ORDER BY timestamp DESC")
    fun getDeliveredMessages(): Flow<List<PendingMessage>>

    @Query("SELECT * FROM pending_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<PendingMessage>>

    @Query("SELECT * FROM pending_messages WHERE phoneNumber = :number AND status = 'HELD'")
    suspend fun getHeldForNumber(number: String): List<PendingMessage>

    @Insert
    suspend fun insert(message: PendingMessage): Long

    @Update
    suspend fun update(message: PendingMessage)

    @Query("UPDATE pending_messages SET status = 'DELIVERED' WHERE phoneNumber = :number AND status = 'HELD'")
    suspend fun deliverAllForNumber(number: String)

    @Query("UPDATE pending_messages SET status = 'REJECTED' WHERE status = 'HELD' AND timestamp < :before")
    suspend fun rejectOlderThan(before: Long)

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_messages WHERE status = 'HELD'")
    fun heldCount(): Flow<Int>
}
