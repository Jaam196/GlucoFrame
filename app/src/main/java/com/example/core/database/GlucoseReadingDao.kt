package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseReadingDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReading(reading: GlucoseReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReadings(readings: List<GlucoseReadingEntity>): List<Long>

    @Query("SELECT * FROM glucose_reading ORDER BY measuredAtUtcMs DESC LIMIT 1")
    fun getLatestReading(): Flow<GlucoseReadingEntity?>

    @Query("SELECT * FROM glucose_reading WHERE sourceId = :sourceId ORDER BY measuredAtUtcMs DESC LIMIT 1")
    fun getLatestReading(sourceId: String): Flow<GlucoseReadingEntity?>

    @Query("SELECT * FROM glucose_reading ORDER BY measuredAtUtcMs DESC LIMIT 1")
    suspend fun getLatestReadingSync(): GlucoseReadingEntity?

    @Query("SELECT * FROM glucose_reading WHERE sourceId = :sourceId ORDER BY measuredAtUtcMs DESC LIMIT 1")
    suspend fun getLatestReadingSync(sourceId: String): GlucoseReadingEntity?

    @Query("SELECT * FROM glucose_reading WHERE measuredAtUtcMs < :beforeUtcMs ORDER BY measuredAtUtcMs DESC LIMIT 1")
    suspend fun getPreviousReading(beforeUtcMs: Long): GlucoseReadingEntity?

    @Query("SELECT * FROM glucose_reading WHERE sourceId = :sourceId AND measuredAtUtcMs < :beforeUtcMs ORDER BY measuredAtUtcMs DESC LIMIT 1")
    suspend fun getPreviousReading(sourceId: String, beforeUtcMs: Long): GlucoseReadingEntity?

    @Query("SELECT * FROM glucose_reading WHERE measuredAtUtcMs >= :startMs AND measuredAtUtcMs <= :endMs ORDER BY measuredAtUtcMs ASC")
    fun getReadingsBetween(startMs: Long, endMs: Long): Flow<List<GlucoseReadingEntity>>

    @Query("SELECT * FROM glucose_reading WHERE sourceId = :sourceId AND measuredAtUtcMs >= :startMs AND measuredAtUtcMs <= :endMs ORDER BY measuredAtUtcMs ASC")
    fun getReadingsBetween(sourceId: String, startMs: Long, endMs: Long): Flow<List<GlucoseReadingEntity>>

    @Query("SELECT * FROM glucose_reading WHERE measuredAtUtcMs >= :startMs AND measuredAtUtcMs <= :endMs ORDER BY measuredAtUtcMs ASC")
    suspend fun getReadingsBetweenSync(startMs: Long, endMs: Long): List<GlucoseReadingEntity>

    @Query("SELECT * FROM glucose_reading WHERE sourceId = :sourceId AND measuredAtUtcMs >= :startMs AND measuredAtUtcMs <= :endMs ORDER BY measuredAtUtcMs ASC")
    suspend fun getReadingsBetweenSync(sourceId: String, startMs: Long, endMs: Long): List<GlucoseReadingEntity>

    @Query("SELECT COUNT(*) FROM glucose_reading")
    suspend fun getReadingsCount(): Int

    @Query("DELETE FROM glucose_reading WHERE measuredAtUtcMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM glucose_reading")
    suspend fun clearAll()
}
