package com.example.core.database.treatments

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "treatments",
    indices = [Index(value = ["externalId"], unique = true)]
)
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val atMs: Long,
    val kind: String,
    val amount: Double,
    val note: String,
    val origin: String,
    val externalId: String
)

@Dao
interface TreatmentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TreatmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<TreatmentEntity>)

    @Query("SELECT * FROM treatments WHERE atMs >= :sinceMs ORDER BY atMs ASC")
    fun observeSince(sinceMs: Long): Flow<List<TreatmentEntity>>

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM treatments WHERE atMs < :beforeMs")
    suspend fun purgeBefore(beforeMs: Long)
}

/** Separate database so the glucose readings database is never touched or migrated. */
@Database(
    entities = [TreatmentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TreatmentsDatabase : RoomDatabase() {

    abstract fun treatmentDao(): TreatmentDao

    companion object {
        @Volatile
        private var INSTANCE: TreatmentsDatabase? = null

        fun getInstance(context: Context): TreatmentsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TreatmentsDatabase::class.java,
                    "treatments.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
