package com.example.latencycheck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurement_records")
data class MeasurementRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latencyMs: Long,
    val networkType: String,
    val operatorAlphaShort: String?,
    val cellId: String?,
    val pci: Int?,
    val bandInfo: String,
    val earfcn: Int?,          // EARFCN for LTE or NRARFCN for NR
    val bandNumber: String?,   // Band number (e.g., "B1", "n78")
    val signalStrength: Int?,
    val bandwidth: String?,
    val neighborCells: String?,
    val timingAdvance: Int?,
    val isRegistered: Boolean = true,  // Whether this is the registered/serving cell
    val subscriptionId: Int = -1,      // For dual SIM support
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val latitude: Double,
    val longitude: Double,
    val locationName: String?
)
