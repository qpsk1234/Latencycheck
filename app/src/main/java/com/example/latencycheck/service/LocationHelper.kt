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
                        // Prefer station names or landmarks
                        val stationAddress = addresses.firstOrNull { it.featureName?.contains("駅") == true || it.featureName?.contains("Station") == true }
                        val address = stationAddress ?: addresses.firstOrNull()
                        val name = address?.featureName ?: address?.locality ?: address?.subAdminArea ?: address?.adminArea
                        continuation.resume(name)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 5)
                val stationAddress = addresses?.firstOrNull { it.featureName?.contains("駅") == true || it.featureName?.contains("Station") == true }
                val address = stationAddress ?: addresses?.firstOrNull()
                address?.featureName ?: address?.locality ?: address?.subAdminArea ?: address?.adminArea
            }
        } catch (e: Exception) {
            null
        }
    }
}
