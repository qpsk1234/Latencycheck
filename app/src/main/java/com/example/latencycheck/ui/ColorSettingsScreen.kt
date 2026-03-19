package com.example.latencycheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.latencycheck.viewmodel.MainViewModel
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSettingsScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val currentConfigJson by viewModel.colorConfigJson.collectAsState()
    
    // Local state for editing
    var levels by remember(currentConfigJson) {
        mutableStateOf(ColorUtils.parseConfig(currentConfigJson))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Thresholds") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val array = JSONArray()
                        levels.forEach { level ->
                            val obj = JSONObject()
                            obj.put("threshold", level.threshold)
                            obj.put("color", String.format("#%06X", (0xFFFFFF and level.color)))
                            array.put(obj)
                        }
                        viewModel.setColorConfigJson(array.toString())
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            Text(
                "Configure 10 levels of latency thresholds (ms) and their colors.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(levels) { index, level ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(level.color))
                                .padding(4.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        OutlinedTextField(
                            value = level.threshold.toString(),
                            onValueChange = { newVal ->
                                val longVal = newVal.toLongOrNull() ?: 0L
                                val newList = levels.toMutableList()
                                newList[index] = level.copy(threshold = longVal)
                                levels = newList
                            },
                            label = { Text("Level ${index + 1} Threshold (ms)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Simple color hex editor
                        OutlinedTextField(
                            value = String.format("#%06X", (0xFFFFFF and level.color)),
                            onValueChange = { newVal ->
                                try {
                                    val colorInt = android.graphics.Color.parseColor(newVal)
                                    val newList = levels.toMutableList()
                                    newList[index] = level.copy(color = colorInt)
                                    levels = newList
                                } catch (e: Exception) {}
                            },
                            label = { Text("Hex") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}
