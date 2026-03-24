package com.example.latencycheck.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MeasurementRecord::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
}
