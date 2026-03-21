package com.example.latencycheck.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.latencycheck.data.MeasurementRecord
import com.example.latencycheck.viewmodel.MainViewModel
import com.example.latencycheck.viewmodel.UiState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val colorConfigJson by viewModel.colorConfigJson.collectAsState()
    val displayColumns by viewModel.displayColumns.collectAsState()
    val mapColorMode by viewModel.mapColorMode.collectAsState()
    
    val context = LocalContext.current
    var selectedRecord by remember { mutableStateOf<MeasurementRecord?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    Configuration.getInstance().userAgentValue = context.packageName

    if (showSettingsDialog) {
        HistorySettingsDialog(
            currentColumns = displayColumns,
            currentMapMode = mapColorMode,
            onDismiss = { showSettingsDialog = false },
            onSave = { columns, mode ->
                viewModel.setDisplayColumns(columns)
                viewModel.setMapColorMode(mode)
                showSettingsDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History Explorer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Display Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Top Pane: Map (50%)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val mapView = remember { MapView(context) }
                AndroidView(
                    factory = {
                        mapView.apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (uiState is UiState.Success) {
                            val records = (uiState as UiState.Success).records
                            view.overlays.clear()
                            records.forEach { record ->
                                val marker = Marker(view)
                                marker.position = GeoPoint(record.latitude, record.longitude)
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                marker.title = "${record.latencyMs}ms"
                                marker.snippet = "${record.locationName ?: "Unknown"}\nType: ${record.networkType}"
                                
                                val color = if (mapColorMode == "signal") {
                                    ColorUtils.getSignalColor(record.rsrp ?: record.signalStrength)
                                } else {
                                    ColorUtils.getLatencyColor(record.latencyMs, colorConfigJson)
                                }

                                marker.icon = ColorUtils.createCustomMarker(context, record.networkType, color)

                                marker.setOnMarkerClickListener { m, _ ->
                                    selectedRecord = record
                                    m.showInfoWindow()
                                    true
                                }
                                view.overlays.add(marker)
                            }
                            
                            selectedRecord?.let {
                                view.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                            } ?: records.firstOrNull()?.let {
                                view.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                            }
                            view.invalidate()
                        }
                    }
                )
            }

            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

            // Bottom Pane: Table/List (50%)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState) {
                    is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is UiState.Success -> {
                        val records = (uiState as UiState.Success).records
                        val horizontalScrollState = rememberScrollState()
                        
                        Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                            LazyColumn(modifier = Modifier.width(800.dp).fillMaxHeight()) {
                                item {
                                    HeaderRow(displayColumns)
                                }
                                items(records) { record ->
                                    RecordRow(
                                        record = record,
                                        isSelected = selectedRecord?.id == record.id,
                                        displayColumns = displayColumns,
                                        colorConfigJson = colorConfigJson,
                                        onClick = { selectedRecord = record }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 1.dp)
                                }
                            }
                        }
                    }
                    is UiState.Error -> Text("Error loading data", Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
fun HistorySettingsDialog(
    currentColumns: Set<String>,
    currentMapMode: String,
    onDismiss: () -> Unit,
    onSave: (Set<String>, String) -> Unit
) {
    var selectedColumns by remember { mutableStateOf(currentColumns) }
    var selectedMapMode by remember { mutableStateOf(currentMapMode) }
    
    val allColumns = listOf("Time", "Latency", "Type", "Band", "Signal", "Location", "RSSI", "RSRP", "RSRQ", "SNR", "Bandwidth")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Display Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Map Icon Color Mode", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedMapMode == "latency", onClick = { selectedMapMode = "latency" })
                    Text("Latency")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = selectedMapMode == "signal", onClick = { selectedMapMode = "signal" })
                    Text("Signal Strength")
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text("List Columns", style = MaterialTheme.typography.titleMedium)
                allColumns.forEach { col ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedColumns = if (selectedColumns.contains(col)) {
                                    selectedColumns - col
                                } else {
                                    selectedColumns + col
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedColumns.contains(col),
                            onCheckedChange = { checked ->
                                selectedColumns = if (checked) selectedColumns + col else selectedColumns - col
                            }
                        )
                        Text(col)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(selectedColumns, selectedMapMode) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun HeaderRow(displayColumns: Set<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if ("Time" in displayColumns) Text("Time", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("Latency" in displayColumns) Text("Lat", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("Type" in displayColumns) Text("Net", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("Band" in displayColumns) Text("Band", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("Signal" in displayColumns) Text("Sig", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("Bandwidth" in displayColumns) Text("BW", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("RSSI" in displayColumns) Text("RSSI", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("RSRP" in displayColumns) Text("RSRP", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("RSRQ" in displayColumns) Text("RSRQ", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("SNR" in displayColumns) Text("SNR", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        if ("Location" in displayColumns) Text("Location", modifier = Modifier.width(150.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun RecordRow(record: MeasurementRecord, isSelected: Boolean, displayColumns: Set<String>, colorConfigJson: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if ("Time" in displayColumns) {
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp)),
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if ("Latency" in displayColumns) {
                Text(
                    text = "${record.latencyMs}ms",
                    modifier = Modifier.width(60.dp),
                    color = Color(ColorUtils.getLatencyColor(record.latencyMs, colorConfigJson)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if ("Type" in displayColumns) {
                Text(
                    text = record.networkType,
                    modifier = Modifier.width(60.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if ("Band" in displayColumns) {
                Text(
                    text = record.bandInfo,
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            if ("Signal" in displayColumns) {
                Text(
                    text = "${record.signalStrength ?: "?"} dBm",
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if ("Bandwidth" in displayColumns) {
                Text(
                    text = record.bandwidth ?: "",
                    modifier = Modifier.width(60.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if ("RSSI" in displayColumns) {
                Text(
                    text = record.rssi?.toString() ?: "",
                    modifier = Modifier.width(50.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if ("RSRP" in displayColumns) {
                Text(
                    text = record.rsrp?.toString() ?: "",
                    modifier = Modifier.width(50.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if ("RSRQ" in displayColumns) {
                Text(
                    text = record.rsrq?.toString() ?: "",
                    modifier = Modifier.width(50.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if ("SNR" in displayColumns) {
                Text(
                    text = record.sinr?.toString() ?: "",
                    modifier = Modifier.width(50.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if ("Location" in displayColumns) {
                Text(
                    text = record.locationName ?: "Unknown",
                    modifier = Modifier.width(150.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}


