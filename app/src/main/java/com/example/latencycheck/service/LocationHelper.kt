package com.example.latencycheck.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // Caller should handle permissions
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        var resumed = false
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!resumed) {
                    resumed = true
                    fusedLocationClient.removeLocationUpdates(this)
                    continuation.resume(locationResult.lastLocation)
                }
            }
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener {
            if (!resumed) {
                resumed = true
                continuation.resume(null)
            }
        }
        
        continuation.invokeOnCancellation {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    suspend fun getLocationName(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 5) { addresses ->
                        val name = buildLocationString(addresses)
                        continuation.resume(name)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 5)
                buildLocationString(addresses ?: emptyList())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildLocationString(addresses: List<android.location.Address>): String? {
        if (addresses.isEmpty()) return null

        // First, look for station name (駅 or Station)
        val stationAddress = addresses.firstOrNull { address ->
            address.featureName?.let { name ->
                name.contains("駅") || name.contains("Station", ignoreCase = true)
            } == true
        }

        // If found, return station name with locality
        stationAddress?.let { station ->
            val stationName = station.featureName
            val locality = station.locality
            return if (locality != null && stationName != null) {
                "$locality $stationName"
            } else {
                stationName
            }
        }

        // Otherwise, build full address from first address
        val address = addresses.firstOrNull() ?: return null
        val parts = mutableListOf<String>()
        address.locality?.let { parts.add(it) }
        address.subLocality?.let { parts.add(it) }
        address.thoroughfare?.let { parts.add(it) }
        address.subThoroughfare?.let { parts.add(it) }

        return if (parts.isNotEmpty()) {
            parts.joinToString(" ")
        } else {
            address.getAddressLine(0)
        }
    }
}
