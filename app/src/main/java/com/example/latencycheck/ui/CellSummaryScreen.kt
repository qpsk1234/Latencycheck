package com.example.latencycheck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.latencycheck.data.CellSummary
import com.example.latencycheck.data.MeasurementRecord
import com.example.latencycheck.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellSummaryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMap: (lat: Double, lon: Double) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedArfcn by remember { mutableStateOf<String?>(null) }
    var selectedCellId by remember { mutableStateOf<String?>(null) }

    // Get records from UI state
    val records = when (uiState) {
        is com.example.latencycheck.viewmodel.UiState.Success -> {
            (uiState as com.example.latencycheck.viewmodel.UiState.Success).records
        }
        else -> emptyList()
    }

    // Filter records based on search
    val filteredRecords = remember(records, searchQuery) {
        if (searchQuery.isBlank()) {
            records
        } else {
            records.filter { record ->
                record.bandInfo.contains(searchQuery, ignoreCase = true) ||
                record.cellId?.contains(searchQuery, ignoreCase = true) == true ||
                record.networkType.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Get ARFCN summary
    val arfcnSummary = remember(filteredRecords) {
        viewModel.getArfcnSummary(filteredRecords)
    }

    // Get CellID summary for selected ARFCN
    val cellIdSummary = remember(selectedArfcn, filteredRecords) {
        selectedArfcn?.let { arfcn ->
            viewModel.getCellIdSummaryForArfcn(filteredRecords, arfcn)
        } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedCellId != null) "Cell Detail" else if (selectedArfcn != null) "Cells: $selectedArfcn" else "Cell Summary") },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectedCellId != null -> selectedCellId = null
                            selectedArfcn != null -> selectedArfcn = null
                            else -> onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Search bar
            if (selectedArfcn == null && selectedCellId == null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search Band, CellID, Network Type...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { })
                )

                // Summary stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard("ARFCN/Bands", arfcnSummary.size.toString())
                    StatCard("Total Cells", arfcnSummary.sumOf { it.uniqueCellCount }.toString())
                    StatCard("Records", arfcnSummary.sumOf { it.recordCount }.toString())
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ARFCN List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(arfcnSummary) { summary ->
                        ArfcnSummaryCard(
                            summary = summary,
                            onClick = { selectedArfcn = summary.arfcnValue }
                        )
                    }
                }
            } else if (selectedArfcn != null && selectedCellId == null) {
                // Cell ID List for selected ARFCN
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cellIdSummary) { summary ->
                        CellIdSummaryCard(
                            summary = summary,
                            onClick = { selectedCellId = summary.cellId },
                            onMapClick = { onNavigateToMap(summary.latestLatitude, summary.latestLongitude) }
                        )
                    }
                }
            } else {
                // Selected Cell Detail - Show records for this cell
                val cellRecords = cellIdSummary.find { it.cellId == selectedCellId }?.records ?: emptyList()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cellRecords) { record ->
                        CellRecordCard(
                            record = record,
                            onMapClick = { onNavigateToMap(record.latitude, record.longitude) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        modifier = Modifier.padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ArfcnSummaryCard(summary: CellSummary.ArfcnSummary, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.band,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary.networkType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoItem("Unique Cells", summary.uniqueCellCount.toString())
                InfoItem("Records", summary.recordCount.toString())
                InfoItem("Latest", dateFormat.format(Date(summary.latestTimestamp)))
            }
        }
    }
}

@Composable
private fun CellIdSummaryCard(
    summary: CellSummary.CellIdSummary,
    onClick: () -> Unit,
    onMapClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cell ID: ${summary.cellId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    summary.pci?.let {
                        Text(
                            text = "PCI: $it",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = onMapClick) {
                    Icon(Icons.Default.LocationOn, contentDescription = "View on Map")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoItem("Records", summary.recordCount.toString())
                summary.avgRsrp?.let { InfoItem("Avg RSRP", "$it dBm") }
                InfoItem("Latest", dateFormat.format(Date(summary.latestTimestamp)))
            }
        }
    }
}

@Composable
private fun CellRecordCard(record: MeasurementRecord, onMapClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Latency: ${record.latencyMs}ms | Signal: ${record.signalStrength ?: "N/A"} dBm",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onMapClick) {
                    Icon(Icons.Default.LocationOn, contentDescription = "View on Map")
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
