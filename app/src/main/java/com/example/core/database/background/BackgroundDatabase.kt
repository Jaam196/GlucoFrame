package com.example.core.database.background

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BackgroundImageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BackgroundDatabase : RoomDatabase() {

    abstract fun backgroundImageDao(): BackgroundImageDao

    companion object {
        private const val DB_NAME = "backgrounds.db"

        @Volatile
        private var INSTANCE: BackgroundDatabase? = null

        fun getInstance(context: Context): BackgroundDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BackgroundDatabase::class.java,
                    DB_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
