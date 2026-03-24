package com.example.latencycheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.latencycheck.data.MeasurementRecord
import com.example.latencycheck.viewmodel.ArfcnDetail
import com.example.latencycheck.viewmodel.BandArfcnSummary
import com.example.latencycheck.viewmodel.CellIdDetailSummary
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
    var selectedBand by remember { mutableStateOf<String?>(null) }
    var selectedArfcn by remember { mutableStateOf<Int?>(null) }
    var selectedCellId by remember { mutableStateOf<String?>(null) }
    var expandedBands by remember { mutableStateOf<Set<String>>(emptySet()) }

    val records = when (uiState) {
        is com.example.latencycheck.viewmodel.UiState.Success -> {
            (uiState as com.example.latencycheck.viewmodel.UiState.Success).records
        }
        else -> emptyList()
    }

    val filteredRecords = remember(records, searchQuery) {
        if (searchQuery.isBlank()) {
            records
        } else {
            records.filter { record ->
                record.bandNumber?.contains(searchQuery, ignoreCase = true) == true ||
                record.earfcn?.toString()?.contains(searchQuery) == true ||
                record.cellId?.contains(searchQuery, ignoreCase = true) == true ||
                record.networkType.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val bandArfcnSummary = remember(filteredRecords) {
        viewModel.getBandArfcnSummary(filteredRecords)
    }

    val cellIdSummary = remember(selectedBand, selectedArfcn, filteredRecords) {
        if (selectedBand != null && selectedArfcn != null) {
            viewModel.getCellIdSummaryForBandArfcn(filteredRecords, selectedBand!!, selectedArfcn)
        } else emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when {
                        selectedCellId != null -> "Cell: $selectedCellId"
                        selectedBand != null -> "$selectedBand - ARFCN: $selectedArfcn"
                        else -> "Cell Summary (Table View)"
                    })
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectedCellId != null -> selectedCellId = null
                            selectedBand != null -> {
                                selectedBand = null
                                selectedArfcn = null
                            }
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
            when {
                selectedCellId == null && selectedBand == null -> {
                    // Main Table View - Band/ARFCN Summary
                    SearchBar(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        stats = Triple(
                            bandArfcnSummary.size,
                            bandArfcnSummary.sumOf { it.totalUniqueCells },
                            bandArfcnSummary.sumOf { it.totalRecords }
                        )
                    )

                    // Table Header
                    BandArfcnTableHeader()

                    // Expandable Band Groups
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        bandArfcnSummary.forEach { bandSummary ->
                            item {
                                BandGroupHeader(
                                    bandSummary = bandSummary,
                                    isExpanded = expandedBands.contains(bandSummary.bandNumber),
                                    onExpandToggle = {
                                        expandedBands = if (expandedBands.contains(bandSummary.bandNumber)) {
                                            expandedBands - bandSummary.bandNumber
                                        } else {
                                            expandedBands + bandSummary.bandNumber
                                        }
                                    }
                                )
                            }

                            if (expandedBands.contains(bandSummary.bandNumber)) {
                                items(bandSummary.arfcnDetails) { arfcnDetail ->
                                    ArfcnTableRow(
                                        arfcnDetail = arfcnDetail,
                                        onClick = {
                                            selectedBand = bandSummary.bandNumber
                                            selectedArfcn = arfcnDetail.arfcnValue
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                selectedBand != null && selectedCellId == null -> {
                    // Cell ID List for selected Band/ARFCN
                    CellIdTableHeader()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        items(cellIdSummary) { cellSummary ->
                            CellIdTableRow(
                                cellSummary = cellSummary,
                                onClick = { selectedCellId = cellSummary.cellId },
                                onMapClick = { onNavigateToMap(cellSummary.latestLatitude, cellSummary.latestLongitude) }
                            )
                        }
                    }
                }

                else -> {
                    // Cell Detail Records
                    val cellRecords = cellIdSummary.find { it.cellId == selectedCellId }?.records ?: emptyList()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cellRecords) { record ->
                            CellRecordDetailCard(record = record, onNavigateToMap = onNavigateToMap)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    stats: Triple<Int, Int, Int> // bands, cells, records
) {
    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search Band, ARFCN, CellID...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { })
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatChip("Bands", stats.first.toString())
            StatChip("Unique Cells", stats.second.toString())
            StatChip("Total Records", stats.third.toString())
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BandArfcnTableHeader() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Band", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text("ARFCN", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text("Type", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text("Cells", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text("Records", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text("Reg/Unreg", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text("SIM", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BandGroupHeader(
    bandSummary: BandArfcnSummary,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandToggle)
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isExpanded) "▼" else "▶",
                modifier = Modifier.width(24.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                bandSummary.bandNumber,
                modifier = Modifier.width(80.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                bandSummary.networkType,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${bandSummary.totalUniqueCells} cells, ${bandSummary.totalRecords} records",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArfcnTableRow(
    arfcnDetail: ArfcnDetail,
    onClick: () -> Unit
) {
    val regUnregText = buildString {
        if (arfcnDetail.registeredCount > 0) append("R:${arfcnDetail.registeredCount}")
        if (arfcnDetail.unregisteredCount > 0) {
            if (this.isNotEmpty()) append("/")
            append("U:${arfcnDetail.unregisteredCount}")
        }
    }

    val simText = buildString {
        if (arfcnDetail.sim1Count > 0) append("1:${arfcnDetail.sim1Count}")
        if (arfcnDetail.sim2Count > 0) {
            if (this.isNotEmpty()) append("/")
            append("2:${arfcnDetail.sim2Count}")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 36.dp, vertical = 8.dp), // Indented for ARFCN rows
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            arfcnDetail.arfcnDisplay.replace("EARFCN: ", "").replace("NRARFCN: ", ""),
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            arfcnDetail.networkType,
            modifier = Modifier.width(70.dp),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            arfcnDetail.uniqueCellCount.toString(),
            modifier = Modifier.width(50.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            arfcnDetail.recordCount.toString(),
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            regUnregText.ifEmpty { "-" },
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            simText.ifEmpty { "-" },
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CellIdTableHeader() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cell ID", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text("PCI", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text("Records", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text("Avg RSRP", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text("Reg/Unreg", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text("SIM", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CellIdTableRow(
    cellSummary: CellIdDetailSummary,
    onClick: () -> Unit,
    onMapClick: () -> Unit
) {
    val regUnregText = buildString {
        if (cellSummary.registeredCount > 0) append("R:${cellSummary.registeredCount}")
        if (cellSummary.unregisteredCount > 0) {
            if (this.isNotEmpty()) append("/")
            append("U:${cellSummary.unregisteredCount}")
        }
    }

    val simText = buildString {
        if (cellSummary.sim1Count > 0) append("1:${cellSummary.sim1Count}")
        if (cellSummary.sim2Count > 0) {
            if (this.isNotEmpty()) append("/")
            append("2:${cellSummary.sim2Count}")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            cellSummary.cellId,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            cellSummary.pci?.toString() ?: "-",
            modifier = Modifier.width(50.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            cellSummary.recordCount.toString(),
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            cellSummary.avgRsrp?.let { "$it dBm" } ?: "-",
            modifier = Modifier.width(70.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            regUnregText.ifEmpty { "-" },
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            simText.ifEmpty { "-" },
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
        IconButton(
            onClick = onMapClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Map", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CellRecordDetailCard(
    record: MeasurementRecord,
    onNavigateToMap: (Double, Double) -> Unit
) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        dateFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Latency: ${record.latencyMs}ms",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Registration Status
                    Surface(
                        color = if (record.isRegistered)
                            Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else
                            Color(0xFFFF9800).copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            if (record.isRegistered) "Registered" else "Unregistered",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (record.isRegistered) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    // SIM Indicator
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.SimCard, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                "SIM${record.subscriptionId + 1}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Band: ${record.bandNumber ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    Text("ARFCN: ${record.earfcn ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    Text("PCI: ${record.pci ?: "-"}", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Signal: ${record.signalStrength ?: "-"} dBm", style = MaterialTheme.typography.bodySmall)
                    Text("RSRP: ${record.rsrp ?: "-"} dBm", style = MaterialTheme.typography.bodySmall)
                    Text("RSRQ: ${record.rsrq ?: "-"} dB", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = { onNavigateToMap(record.latitude, record.longitude) },
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("View on Map", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
