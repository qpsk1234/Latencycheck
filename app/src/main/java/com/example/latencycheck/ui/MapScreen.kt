package com.example.latencycheck.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.latencycheck.viewmodel.MainViewModel
import com.example.latencycheck.viewmodel.UiState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val colorConfigJson by viewModel.colorConfigJson.collectAsState()
    
    // osmdroid configuration
    Configuration.getInstance().userAgentValue = context.packageName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map View") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
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
                            marker.title = "${record.latencyMs}ms (${record.networkType})"
                            marker.snippet = record.locationName ?: "Unknown"

                            // Set color based on latency (10 stages)
                            val color = ColorUtils.getLatencyColor(record.latencyMs, colorConfigJson)
                            val icon = marker.icon.constantState?.newDrawable()?.mutate()
                            if (icon != null) {
                                icon.setTint(color)
                                marker.icon = icon
                            }

                            view.overlays.add(marker)
                        }
                        
                        // Center on latest record if available
                        records.firstOrNull()?.let {
                            view.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                        }
                        view.invalidate()
                    }
                }
            )
            
            DisposableEffect(Unit) {
                onDispose {
                    mapView.onDetach()
                }
            }
        }
    }
}
