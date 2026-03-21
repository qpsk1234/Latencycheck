package com.example.latencycheck.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
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
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberEndAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import kotlin.math.abs
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
        uri?.let { viewModel.importFromCsv(context, it) { msg -> 
             android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        } }
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
                    IconButton(onClick = { 
                        viewModel.exportToCsv(context) { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/comma-separated-values", "text/csv")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import CSV")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(8.dp)
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
        // 0. General Stats
        item {
            SectionHeader("Overall Statistics")
            GeneralStatsCard(records)
        }

        // 1. Station Summary
        item {
            SectionHeader("Point of Interest Statistics")
            StationStatsCard(records)
        }

        // 2. Latency & Signal Chart
        item {
            SectionHeader("Latency & Signal Trend")
            LatencySignalChartCard(records, colorConfigJson)
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
        modifier = Modifier.padding(8.dp)
    )
}

@Composable
fun StationStatsCard(records: List<MeasurementRecord>) {
    val stationKeywords = listOf("駅", "Station", "空港", "Airport", "IC", "SA", "PA", "Jct")
    val stationRecords = records.filter { record ->
        stationKeywords.any { keyword -> record.locationName?.contains(keyword, ignoreCase = true) == true }
    }
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
            Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Text("Location", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                Text("Lat.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("5G%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Sig.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
            if (stats.isEmpty()) {
                Text("No matching locations found.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
            } else {
                stats.take(20).forEach { stat ->
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        Text(stat.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text("${stat.avgLatency}ms", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${stat.nrRate}%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${stat.avgSignal ?: "N/A"}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

data class StationStat(val name: String, val avgLatency: Int, val nrRate: Int, val avgSignal: Int?)

@Composable
fun GeneralStatsCard(records: List<MeasurementRecord>) {
    val avgLatency = records.map { it.latencyMs }.average().toInt()
    val maxLatency = records.maxOf { it.latencyMs }
    val minLatency = records.minOf { it.latencyMs }
    val rate5g = (records.count { it.networkType.contains("5G") }.toFloat() / records.size * 100).toInt()
    
    val jitters = records.zipWithNext { a, b -> abs(a.latencyMs - b.latencyMs) }
    val avgJitter = if (jitters.isEmpty()) 0 else jitters.average().toInt()

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatItem("Avg Latency", "${avgLatency}ms", MaterialTheme.colorScheme.primary)
            StatItem("Max Latency", "${maxLatency}ms", MaterialTheme.colorScheme.error)
            StatItem("Avg Jitter", "${avgJitter}ms", MaterialTheme.colorScheme.secondary)
            StatItem("5G Rate", "${rate5g}%", Color(0xFF4CAF50))
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Text(label, style = MaterialTheme.typography.labelSmall)
        androidx.compose.material3.Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun LatencySignalChartCard(records: List<MeasurementRecord>, colorConfigJson: String) {
    val displayRecords = records.take(100).reversed()
    if (displayRecords.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val latencyNREntries = mutableListOf<FloatEntry>()
            val latencyLTEEntries = mutableListOf<FloatEntry>()
            val signalEntries = mutableListOf<FloatEntry>()
            
            displayRecords.forEachIndexed { index, record ->
                val isNR = record.networkType.contains("5G")
                val latency = record.latencyMs.toFloat()
                val signal = record.signalStrength?.toFloat() ?: -140f

                if (isNR) {
                    latencyNREntries.add(entryOf(index.toFloat(), latency))
                } else {
                    latencyLTEEntries.add(entryOf(index.toFloat(), latency))
                }
                signalEntries.add(entryOf(index.toFloat(), signal))
            }

            val nrColor = Color(0xFF4CAF50).toArgb()
            val lteColor = Color(0xFF2196F3).toArgb()
            val signalColor = Color(0xFFFF9800).toArgb()

            Chart(
                chart = lineChart(
                    lines = listOf(
                        LineChart.LineSpec(lineColor = nrColor),
                        LineChart.LineSpec(lineColor = lteColor),
                        LineChart.LineSpec(lineColor = signalColor, lineThicknessDp = 1f)
                    )
                ),
                model = entryModelOf(latencyNREntries, latencyLTEEntries, signalEntries),
                startAxis = rememberStartAxis(title = "Value"),
                bottomAxis = rememberBottomAxis(),
                modifier = Modifier.fillMaxSize()
            )
        }
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
                        .padding(1.dp)
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
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
