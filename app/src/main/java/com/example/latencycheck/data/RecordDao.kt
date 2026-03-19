package com.example.latencycheck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert
    suspend fun insertRecord(record: MeasurementRecord)

    @Query("SELECT * FROM measurement_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<MeasurementRecord>>
    
    @Query("DELETE FROM measurement_records")
    suspend fun clearAll()
}
