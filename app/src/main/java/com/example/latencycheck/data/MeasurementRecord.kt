package com.example.latencycheck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurement_records")
data class MeasurementRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latencyMs: Long,
    val networkType: String,
    val bandInfo: String,
    val signalStrength: Int?,
    val bandwidth: Int?,
    val neighborCells: String?,
    val timingAdvance: Int?,
    val latitude: Double,
    val longitude: Double,
    val locationName: String?
)
