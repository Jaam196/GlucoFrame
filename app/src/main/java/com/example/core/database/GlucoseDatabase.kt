package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GlucoseReadingEntity::class,
        SyncEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GlucoseDatabase : RoomDatabase() {
    abstract fun glucoseReadingDao(): GlucoseReadingDao
    abstract fun syncEventDao(): SyncEventDao

    companion object {
        @Volatile
        private var INSTANCE: GlucoseDatabase? = null

        fun getInstance(context: Context): GlucoseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GlucoseDatabase::class.java,
                    "glucose_frame.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
