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
    val bandwidth: String?,
    val neighborCells: String?,
    val timingAdvance: Int?,
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val latitude: Double,
    val longitude: Double,
    val locationName: String?
)
