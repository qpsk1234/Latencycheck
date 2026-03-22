package com.example.latencycheck.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.latencycheck.service.NetworkInfoHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    networkInfoHelper: NetworkInfoHelper,
    onNavigateBack: () -> Unit
) {
    var debugInfo by remember { mutableStateOf(networkInfoHelper.getDebugInfo()) }
    var autoRefresh by remember { mutableStateOf(true) }

    // Auto-refresh every 1 second
    LaunchedEffect(autoRefresh) {
        if (autoRefresh) {
            while (isActive) {
                debugInfo = networkInfoHelper.getDebugInfo()
                delay(1000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text("Auto Refresh", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoRefresh,
                            onCheckedChange = { autoRefresh = it }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Telephony Debug Information",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Network Type Section
            item {
                DebugSection(title = "Network Type") {
                    DebugItem("Network Type (legacy)", debugInfo.networkType)
                    DebugItem("Data Network Type", debugInfo.dataNetworkType)
                    DebugItem("Voice Network Type", debugInfo.voiceNetworkType)
                    DebugItem("Derived NR Type", debugInfo.overrideNetworkType)
                }
            }

            // Operator Section
            item {
                DebugSection(title = "Operator Information") {
                    DebugItem("Operator Name", debugInfo.operatorName)
                    DebugItem("Operator Alpha Short", debugInfo.operatorAlphaShort)
                    DebugItem("Operator Alpha Long", debugInfo.operatorAlphaLong)
                    DebugItem("Operator Numeric", debugInfo.operatorNumeric)
                    DebugItem("SIM Operator", debugInfo.simOperatorName)
                }
            }

            // Device/Subscription Section
            item {
                DebugSection(title = "Device Information") {
                    DebugItem("Device ID", debugInfo.deviceId?.let { "${it.takeLast(4)}" } ?: "N/A")
                    DebugItem("Subscriber ID", debugInfo.subscriberId?.let { "${it.takeLast(4)}" } ?: "N/A")
                }
            }

            // Data Connection Section
            item {
                DebugSection(title = "Data Connection") {
                    DebugItem("Data State", debugInfo.dataState)
                    DebugItem("Data Activity", debugInfo.dataActivity)
                    DebugItem("Call State", debugInfo.callState)
                }
            }

            // Physical Channel Config
            item {
                DebugSection(title = "Physical Channel Config") {
                    Text(
                        debugInfo.physicalChannelConfig ?: "N/A",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Cell Info Section
            item {
                DebugSection(title = "Cell Information (${debugInfo.cellInfoList.size} cells)") {
                    debugInfo.cellInfoList.forEach { cellInfo ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = cellInfo,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            // Signal Strength Raw
            item {
                DebugSection(title = "Signal Strength (Raw)") {
                    Text(
                        debugInfo.signalStrengthRaw ?: "N/A",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }

            // Service State Raw
            item {
                DebugSection(title = "Service State (Raw)") {
                    Text(
                        debugInfo.serviceStateRaw ?: "N/A",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }

            // Telephony Properties
            item {
                DebugSection(title = "Telephony Properties (${debugInfo.allTelephonyProperties.size} items)") {
                    debugInfo.allTelephonyProperties.entries.sortedBy { it.key }.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "$key:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { debugInfo = networkInfoHelper.getDebugInfo() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manual Refresh")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun DebugItem(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value ?: "null",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.6f)
        )
    }
}
