package com.example.latencycheck.data

object CellSummary {
    data class ArfcnSummary(
        val arfcnValue: String,  // Band info (e.g., "B1", "n78", "EARFCN: 1234")
        val band: String,        // Band info
        val networkType: String, // LTE/5G SA/5G NSA
        val uniqueCellCount: Int,
        val recordCount: Int,
        val latestTimestamp: Long,
        val records: List<MeasurementRecord>
    )

    data class CellIdSummary(
        val cellId: String,
        val pci: Int?,
        val arfcnValue: String,
        val band: String,
        val networkType: String,
        val recordCount: Int,
        val avgRsrp: Int?,
        val latestLatitude: Double,
        val latestLongitude: Double,
        val latestTimestamp: Long,
        val records: List<MeasurementRecord>
    )
}