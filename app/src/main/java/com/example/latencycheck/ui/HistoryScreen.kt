package com.example.latencycheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val context = LocalContext.current
    var selectedRecord by remember { mutableStateOf<MeasurementRecord?>(null) }
    
    Configuration.getInstance().userAgentValue = context.packageName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History Explorer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Top Pane: Map (50%)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                marker.title = "${record.latencyMs}ms"
                                marker.snippet = record.locationName ?: "Unknown"
                                
                                // Set color based on latency (10 stages)
                                val color = ColorUtils.getLatencyColor(record.latencyMs, colorConfigJson)
                                val icon = marker.icon.constantState?.newDrawable()?.mutate()
                                if (icon != null) {
                                    icon.setTint(color)
                                    marker.icon = icon
                                }

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

            Divider(color = MaterialTheme.colorScheme.outline, thickness = 2.dp)

            // Bottom Pane: Table/List (50%)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState) {
                    is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is UiState.Success -> {
                        val records = (uiState as UiState.Success).records
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                HeaderRow()
                            }
                            items(records) { record ->
                                RecordRow(
                                    record = record,
                                    isSelected = selectedRecord?.id == record.id,
                                    colorConfigJson = colorConfigJson,
                                    onClick = { selectedRecord = record }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 1.dp)
                            }
                        }
                    }
                    is UiState.Error -> Text("Error loading data", Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

// ... HeaderRow stays the same ...
@Composable
fun HeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Time", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Lat", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Net", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Band", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Sig/NB", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Location", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun RecordRow(record: MeasurementRecord, isSelected: Boolean, colorConfigJson: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp)),
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${record.latencyMs}ms",
                modifier = Modifier.weight(0.8f),
                color = Color(ColorUtils.getLatencyColor(record.latencyMs, colorConfigJson)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = record.networkType,
                modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = record.bandInfo,
                modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = "${record.signalStrength ?: "?"} dBm",
                    style = MaterialTheme.typography.labelSmall
                )
                if (!record.bandwidth.isNullOrBlank()) {
                    Text(
                        text = record.bandwidth,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 3
                    )
                }
            }
            Text(
                text = record.locationName ?: "Unknown",
                modifier = Modifier.weight(1.5f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}
