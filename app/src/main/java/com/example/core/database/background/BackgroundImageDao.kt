package com.example.core.database.background

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BackgroundImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: BackgroundImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BackgroundImageEntity>): List<Long>

    @Query("SELECT * FROM background_images WHERE uriString = :uriString LIMIT 1")
    suspend fun getByUri(uriString: String): BackgroundImageEntity?

    @Query("SELECT * FROM background_images ORDER BY isFavorite DESC, displayName ASC")
    fun getAllFlow(): Flow<List<BackgroundImageEntity>>

    @Query("SELECT * FROM background_images ORDER BY isFavorite DESC, displayName ASC")
    suspend fun getAllSync(): List<BackgroundImageEntity>

    @Query("SELECT * FROM background_images WHERE sourceType = :sourceType ORDER BY isFavorite DESC, displayName ASC")
    fun getBySourceTypeFlow(sourceType: String): Flow<List<BackgroundImageEntity>>

    @Query("SELECT * FROM background_images WHERE sourceType = :sourceType ORDER BY isFavorite DESC, displayName ASC")
    suspend fun getBySourceTypeSync(sourceType: String): List<BackgroundImageEntity>

    @Query("SELECT * FROM background_images WHERE sourceType = :sourceType AND category = :category ORDER BY isFavorite DESC, displayName ASC")
    fun getByCategoryFlow(sourceType: String, category: String): Flow<List<BackgroundImageEntity>>

    @Query("UPDATE background_images SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE background_images SET isDisabled = :isDisabled WHERE id = :id")
    suspend fun updateDisabled(id: Long, isDisabled: Boolean)

    @Query("DELETE FROM background_images WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)

    @Query("DELETE FROM background_images WHERE sourceType = :sourceType AND uriString NOT IN (:validUris)")
    suspend fun deleteOrphaned(sourceType: String, validUris: List<String>)

    @Query("SELECT COUNT(*) FROM background_images")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM background_images WHERE analyzedAtUtcMs > 0")
    suspend fun getAnalyzedCount(): Int
}
