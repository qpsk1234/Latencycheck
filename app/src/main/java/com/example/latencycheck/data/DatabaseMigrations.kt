package com.example.latencycheck.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    // Migration from version 4 to 5: Add new columns for detailed cell info
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new columns to measurement_records table
            database.execSQL("ALTER TABLE measurement_records ADD COLUMN earfcn INTEGER")
            database.execSQL("ALTER TABLE measurement_records ADD COLUMN bandNumber TEXT")
            database.execSQL("ALTER TABLE measurement_records ADD COLUMN isRegistered INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE measurement_records ADD COLUMN subscriptionId INTEGER NOT NULL DEFAULT -1")
        }
    }
}