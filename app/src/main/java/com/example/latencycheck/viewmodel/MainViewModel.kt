package com.example.latencycheck.viewmodel

import android.content.Context
import android.net.Uri
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.latencycheck.data.AppPreferences
import com.example.latencycheck.data.CellSummary
import com.example.latencycheck.data.MeasurementRecord
import com.example.latencycheck.data.RecordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val targetUrl: StateFlow<String> = appPreferences.targetUrl.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "https://www.google.com"
    )

    val intervalSeconds: StateFlow<Long> = appPreferences.intervalSeconds.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        5L
    )

    val isRunning: StateFlow<Boolean> = appPreferences.isRunning.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val maxLatencyThreshold: StateFlow<Long> = appPreferences.maxLatencyThreshold.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        1000L
    )

    val uiState: StateFlow<UiState> = recordDao.getAllRecords()
        .map<List<MeasurementRecord>, UiState> { records -> UiState.Success(records) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    val colorConfigJson: StateFlow<String> = appPreferences.colorConfigJson.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppPreferences.DEFAULT_COLOR_CONFIG
    )

    val debugEnabled: StateFlow<Boolean> = appPreferences.debugEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val displayColumns: StateFlow<Set<String>> = appPreferences.displayColumns.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppPreferences.DEFAULT_DISPLAY_COLUMNS
    )

    val mapColorMode: StateFlow<String> = appPreferences.mapColorMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "latency"
    )

    fun setTargetUrl(url: String) {
        viewModelScope.launch { appPreferences.setTargetUrl(url) }
    }

    fun setIntervalSeconds(seconds: Long) {
        viewModelScope.launch { appPreferences.setIntervalSeconds(seconds) }
    }

    fun setMaxLatencyThreshold(threshold: Long) {
        viewModelScope.launch { appPreferences.setMaxLatencyThreshold(threshold) }
    }

    fun setColorConfigJson(json: String) {
        viewModelScope.launch { appPreferences.setColorConfigJson(json) }
    }

    fun setDebugEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setDebugEnabled(enabled) }
    }

    fun setDisplayColumns(columns: Set<String>) {
        viewModelScope.launch { appPreferences.setDisplayColumns(columns) }
    }

    fun setMapColorMode(mode: String) {
        viewModelScope.launch { appPreferences.setMapColorMode(mode) }
    }

    fun toggleService(context: Context) {
        val nextState = !isRunning.value
        val intent = Intent(context, com.example.latencycheck.service.MeasureService::class.java).apply {
            action = if (nextState) "ACTION_START" else "ACTION_STOP"
        }
        if (nextState) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
        // preferences are updated by the service itself on start/stop, but we can set it here for immediate UI feedback
        viewModelScope.launch { appPreferences.setIsRunning(nextState) }
    }

    fun clearHistory() {
        viewModelScope.launch { recordDao.clearAll() }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun exportToCsv(context: Context, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val records = recordDao.getAllRecords().first()
            val fileName = "latency_records_${System.currentTimeMillis()}.csv"
            
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write("Time,LatencyMs,Network,Operator,CellID,PCI,Band,SignalRSRP,BW,Neighbors,TA,RSSI,RSRP,RSRQ,SINR,Latitude,Longitude,LocationName\n".toByteArray())
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    records.forEach { record ->
                        val fields = listOf(
                            sdf.format(Date(record.timestamp)),
                            record.latencyMs.toString(),
                            escapeCsv(record.networkType),
                            escapeCsv(record.operatorAlphaShort ?: ""),
                            escapeCsv(record.cellId ?: ""),
                            record.pci?.toString() ?: "",
                            escapeCsv(record.bandInfo),
                            record.signalStrength?.toString() ?: "",
                            record.bandwidth?.toString() ?: "",
                            escapeCsv(record.neighborCells ?: ""),
                            record.timingAdvance?.toString() ?: "",
                            record.rssi?.toString() ?: "",
                            record.rsrp?.toString() ?: "",
                            record.rsrq?.toString() ?: "",
                            record.sinr?.toString() ?: "",
                            record.latitude.toString(),
                            record.longitude.toString(),
                            escapeCsv(record.locationName ?: "")
                        )
                        outputStream.write("${fields.joinToString(",")}\n".toByteArray())
                    }
                }
                onComplete("Saved to Downloads/$fileName")
            } ?: onComplete("Failed to create file")
        }
    }

    fun importFromCsv(context: Context, uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val resolver = context.contentResolver
                resolver.openInputStream(uri)?.use { inputStream ->
                    val reader = inputStream.bufferedReader()
                    val header = reader.readLine() // Skip header
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    val records = mutableListOf<MeasurementRecord>()
                    
                    reader.forEachLine { line ->
                        val parts = parseCsvLine(line)
                        if (parts.size >= 18) {
                            try {
                                val timestamp = sdf.parse(parts[0])?.time ?: System.currentTimeMillis()
                                val latencyMs = parts[1].toLongOrNull() ?: 0L
                                val networkType = parts[2]
                                val operatorAlphaShort = parts[3].takeIf { it.isNotEmpty() }
                                val cellId = parts[4].takeIf { it.isNotEmpty() }
                                val pci = parts[5].toIntOrNull()
                                val bandInfo = parts[6]
                                val signalStrength = parts[7].toIntOrNull()
                                val bandwidth = parts[8]
                                val neighborsRaw = parts[9]
                                val timingAdvance = parts[10].toIntOrNull()
                                val rssi = parts[11].toIntOrNull()
                                val rsrp = parts[12].toIntOrNull()
                                val rsrq = parts[13].toIntOrNull()
                                val sinr = parts[14].toIntOrNull()
                                val latitude = parts[15].toDoubleOrNull() ?: 0.0
                                val longitude = parts[16].toDoubleOrNull() ?: 0.0
                                val locationName = parts[17]

                                records.add(MeasurementRecord(
                                    timestamp = timestamp,
                                    latencyMs = latencyMs,
                                    networkType = networkType,
                                    operatorAlphaShort = operatorAlphaShort,
                                    cellId = cellId,
                                    pci = pci,
                                    bandInfo = bandInfo,
                                    signalStrength = signalStrength,
                                    bandwidth = bandwidth,
                                    neighborCells = neighborsRaw,
                                    timingAdvance = timingAdvance,
                                    rssi = rssi,
                                    rsrp = rsrp,
                                    rsrq = rsrq,
                                    sinr = sinr,
                                    latitude = latitude,
                                    longitude = longitude,
                                    locationName = locationName
                                ))
                            } catch (e: Exception) {
                                // Skip malformed row
                            }
                        }
                    }
                    if (records.isNotEmpty()) {
                        records.forEach { recordDao.insertRecord(it) }
                        onComplete("Imported ${records.size} records")
                    } else {
                        onComplete("No valid records found in CSV")
                    }
                }
            } catch (e: Exception) {
                onComplete("Import failed: ${e.message}")
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    // Cell Summary aggregation functions
    fun getArfcnSummary(records: List<MeasurementRecord>): List<CellSummary.ArfcnSummary> {
        return records
            .filter { it.cellId != null }
            .groupBy { it.bandInfo }
            .map { (bandInfo, bandRecords) ->
                val networkType = bandRecords.firstOrNull()?.networkType ?: "Unknown"
                val uniqueCells = bandRecords.map { it.cellId }.distinct().size
                CellSummary.ArfcnSummary(
                    arfcnValue = bandInfo,
                    band = bandInfo,
                    networkType = networkType,
                    uniqueCellCount = uniqueCells,
                    recordCount = bandRecords.size,
                    latestTimestamp = bandRecords.maxOfOrNull { it.timestamp } ?: 0L,
                    records = bandRecords.sortedByDescending { it.timestamp }
                )
            }
            .sortedByDescending { it.recordCount }
    }

    fun getCellIdSummaryForArfcn(records: List<MeasurementRecord>, arfcnValue: String): List<CellSummary.CellIdSummary> {
        return records
            .filter { it.bandInfo == arfcnValue && it.cellId != null }
            .groupBy { it.cellId!! }
            .map { (cellId, cellRecords) ->
                val firstRecord = cellRecords.firstOrNull()
                val latestRecord = cellRecords.maxByOrNull { it.timestamp }
                CellSummary.CellIdSummary(
                    cellId = cellId,
                    pci = firstRecord?.pci,
                    arfcnValue = arfcnValue,
                    band = arfcnValue,
                    networkType = firstRecord?.networkType ?: "Unknown",
                    recordCount = cellRecords.size,
                    avgRsrp = cellRecords.mapNotNull { it.rsrp }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                    latestLatitude = latestRecord?.latitude ?: 0.0,
                    latestLongitude = latestRecord?.longitude ?: 0.0,
                    latestTimestamp = latestRecord?.timestamp ?: 0L,
                    records = cellRecords.sortedByDescending { it.timestamp }
                )
            }
            .sortedByDescending { it.recordCount }
    }

    fun searchRecordsByBandOrCellId(searchQuery: String): kotlinx.coroutines.flow.Flow<List<CellSummary.ArfcnSummary>> {
        return recordDao.searchByBandOrCellId(searchQuery)
            .map { records -> getArfcnSummary(records) }
    }
}

sealed interface UiState {
    object Loading : UiState
    data class Success(val records: List<MeasurementRecord>) : UiState
    data class Error(val exception: Throwable) : UiState
}
