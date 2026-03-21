package com.example.latencycheck.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.latencycheck.data.MeasurementRecord
import com.example.latencycheck.service.MeasureService
import com.example.latencycheck.viewmodel.MainViewModel
import com.example.latencycheck.viewmodel.UiState
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSummary: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val colorConfigJson by viewModel.colorConfigJson.collectAsState()
    
    // Permission handling (simplified set for demo)
    var permissionsGranted by remember { mutableStateOf(false) }
    val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Latency Check") },
                actions = {
                    IconButton(onClick = onNavigateToMap) {
                        Icon(Icons.Filled.Place, contentDescription = "Map")
                    }
                    IconButton(onClick = onNavigateToSummary) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Summary")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.List, contentDescription = "History")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Control Panel (Premium Look)
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isRunning) "Service is Running" else "Service is Stopped",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                if (isRunning) "Monitoring live..." else "Ready to monitor",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = {
                                if (!permissionsGranted) {
                                    permissionLauncher.launch(requiredPermissions.toTypedArray())
                                } else {
                                    viewModel.toggleService(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isRunning) "STOP" else "START")
                        }
                    }
                }
            }
            
            // Real-time Chart
            if (uiState is UiState.Success) {
                val records = (uiState as UiState.Success).records.take(1000).reversed()
                if (records.isNotEmpty()) {
                    Text(
                        text = "Latency Trend (Last 1000)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        val entries = records.mapIndexed { index, record ->
                            entryOf(index.toFloat(), record.latencyMs.toFloat())
                        }
                        val model = entryModelOf(entries)
                        val levels = ColorUtils.parseConfig(colorConfigJson)
                        
                        Chart(
                            chart = lineChart(
                                lines = listOf(
                                    com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(
                                        lineColor = MaterialTheme.colorScheme.primary.toArgb(),
                                        point = com.patrykandpatrick.vico.core.component.shape.ShapeComponent(
                                            shape = com.patrykandpatrick.vico.core.component.shape.Shapes.pillShape,
                                            color = levels.firstOrNull()?.color ?: MaterialTheme.colorScheme.primary.toArgb()
                                        ),
                                        pointSizeDp = 4f
                                    )
                                )
                            ),
                            model = model,
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(),
                            modifier = Modifier.padding(8.dp),
                            isZoomEnabled = true
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Latest Result Summary (Removed from here as it's redundant with compact list top)
            
            Text(
                text = "History",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // List of Records
            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is UiState.Success -> {
                        val records = (uiState as UiState.Success).records
                        if (records.isEmpty()) {
                            Text("No records found.", modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn {
                                items(records, key = { it.id }) { record ->
                                    RecordItem(record, colorConfigJson)
                                }
                            }
                        }
                    }
                    is UiState.Error -> {
                        Text("Error loading data", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

@Composable
fun RecordItem(record: MeasurementRecord, colorConfigJson: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.locationName ?: "Unknown Location",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                Text(
                    text = "Net: ${record.networkType} (${record.bandInfo}) Signal: ${record.signalStrength ?: "?"} dBm",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "${record.latencyMs}ms",
                style = MaterialTheme.typography.titleMedium,
                color = Color(ColorUtils.getLatencyColor(record.latencyMs, colorConfigJson))
            )
        }
    }
}
