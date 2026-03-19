package com.example.latencycheck.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.latencycheck.data.MeasurementRecord
import com.example.latencycheck.viewmodel.MainViewModel
import com.example.latencycheck.viewmodel.UiState
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val colorConfigJson by viewModel.colorConfigJson.collectAsState()
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromCsv(context, it) { msg -> /* Show snackbar if needed */ } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/comma-separated-values", "text/csv")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import CSV")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (uiState) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                is UiState.Error -> Text("Error loading data", color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    val records = (uiState as UiState.Success).records
                    if (records.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No data available. Record some data or import CSV.")
                        }
                    } else {
                        SummaryContent(records, colorConfigJson)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryContent(records: List<MeasurementRecord>, colorConfigJson: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Station Summary
        item {
            SectionHeader("Station Statistics (Locations with '駅')")
            StationStatsCard(records)
        }

        // 2. Latency Chart
        item {
            SectionHeader("Latency Timeline (ms)")
            LatencyTimelineCard(records, colorConfigJson)
        }

        // 3. Band Timeline (NR/LTE color-coded background or points)
        item {
            SectionHeader("Network Type Timeline")
            NetworkTypeTimelineCard(records)
        }

        // 4. Map Distribution
        item {
            SectionHeader("Measurement Map")
            SummaryMapCard(records, colorConfigJson)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun StationStatsCard(records: List<MeasurementRecord>) {
    val stationRecords = records.filter { it.locationName?.contains("駅") == true }
    val stats = stationRecords.groupBy { it.locationName ?: "Unknown" }
        .map { (name, group) ->
            val avgLatency = group.map { it.latencyMs }.average()
            val nrCount = group.count { it.networkType.contains("5G") }
            val nrRate = (nrCount.toFloat() / group.size * 100).toInt()
            val avgSignal = group.mapNotNull { it.signalStrength }.let { if (it.isEmpty()) null else it.average().toInt() }
            StationStat(name, avgLatency.toInt(), nrRate, avgSignal)
        }.sortedByDescending { it.avgLatency }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Station", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                Text("Lat.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("5G%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Sig.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
            stats.take(10).forEach { stat ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stat.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text("${stat.avgLatency}ms", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("${stat.nrRate}%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("${stat.avgSignal ?: "N/A"}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

data class StationStat(val name: String, val avgLatency: Int, val nrRate: Int, val avgSignal: Int?)

@Composable
fun LatencyTimelineCard(records: List<MeasurementRecord>, colorConfigJson: String) {
    Card(modifier = Modifier.fillMaxWidth().height(250.dp)) {
        val displayRecords = records.take(100).reversed()
        val entries = displayRecords.mapIndexed { index, record ->
            entryOf(index.toFloat(), record.latencyMs.toFloat())
        }
        val model = entryModelOf(entries)
        
        Chart(
            chart = lineChart(),
            model = model,
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun NetworkTypeTimelineCard(records: List<MeasurementRecord>) {
    Card(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        val displayRecords = records.take(200).reversed()
        Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            displayRecords.forEach { record ->
                val color = if (record.networkType.contains("5G")) Color(0xFF4CAF50) else Color(0xFF2196F3)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(color)
                        .padding(horizontal = 0.5.dp)
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        LegendItem("5G (NR)", Color(0xFF4CAF50))
        Spacer(modifier = Modifier.width(16.dp))
        LegendItem("4G (LTE)", Color(0xFF2196F3))
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SummaryMapCard(records: List<MeasurementRecord>, colorConfigJson: String) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = context.packageName
    
    val mapView = remember { MapView(context) }
    
    Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        AndroidView(
            factory = {
                mapView.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.0)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.overlays.clear()
                records.take(500).forEach { record ->
                    val marker = Marker(view)
                    marker.position = GeoPoint(record.latitude, record.longitude)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    val color = ColorUtils.getLatencyColor(record.latencyMs, colorConfigJson)
                    val icon = marker.icon.constantState?.newDrawable()?.mutate()
                    icon?.setTint(color)
                    marker.icon = icon
                    view.overlays.add(marker)
                }
                records.firstOrNull()?.let {
                    view.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                }
                view.invalidate()
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }
}
