package com.callscreen.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM whitelisted_contacts ORDER BY whitelistedAt DESC")
    fun getAllWhitelisted(): Flow<List<WhitelistedContact>>

    @Query("SELECT EXISTS(SELECT 1 FROM whitelisted_contacts WHERE phoneNumber = :number)")
    suspend fun isWhitelisted(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun whitelist(contact: WhitelistedContact)

    @Delete
    suspend fun remove(contact: WhitelistedContact)

    @Query("DELETE FROM whitelisted_contacts WHERE phoneNumber = :number")
    suspend fun removeByNumber(number: String)

    @Query("SELECT COUNT(*) FROM whitelisted_contacts")
    fun count(): Flow<Int>
}
