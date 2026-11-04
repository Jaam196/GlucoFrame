package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SyncEventEntity): Long

    @Query("SELECT * FROM sync_event ORDER BY atUtcMs DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 200): Flow<List<SyncEventEntity>>

    @Query("SELECT * FROM sync_event ORDER BY atUtcMs DESC LIMIT :limit")
    suspend fun getRecentEventsSync(limit: Int = 200): List<SyncEventEntity>

    @Query("DELETE FROM sync_event WHERE id NOT IN (SELECT id FROM sync_event ORDER BY atUtcMs DESC LIMIT :keepCount)")
    suspend fun trimOlderEvents(keepCount: Int = 500)

    @Query("DELETE FROM sync_event")
    suspend fun clearAll()
}
