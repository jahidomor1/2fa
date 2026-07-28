package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TwoFactorKey::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun twoFactorKeyDao(): TwoFactorKeyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "two_factor_generate_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
