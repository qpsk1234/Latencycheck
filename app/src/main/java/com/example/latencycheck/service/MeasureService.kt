package com.example.latencycheck.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.latencycheck.R
import com.example.latencycheck.data.AppPreferences
import com.example.latencycheck.data.MeasurementRecord
import com.example.latencycheck.data.RecordDao
import com.example.latencycheck.network.NetworkMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class MeasureService : Service() {

    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var locationHelper: LocationHelper
    @Inject lateinit var networkInfoHelper: NetworkInfoHelper
    @Inject lateinit var recordDao: RecordDao
    @Inject lateinit var appPreferences: AppPreferences

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var measureJob: Job? = null
    
    companion object {
        const val CHANNEL_ID = "MeasureServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_START) {
            Log.d("MeasureService", "Service starting (intent=${intent?.action ?: "null/restart"})")
            startServiceOperations()
        } else if (intent.action == ACTION_STOP) {
            Log.d("MeasureService", "Service stopping")
            stopSelf()
        }
        return START_STICKY
    }

    private fun startServiceOperations() {
        // Acquire WakeLock if not already held
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "LatencyCheck:MeasureWakeLock")
        }
        
        wakeLock?.let {
            if (!it.isHeld) it.acquire()
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startMeasuringLoop()
    }

    private fun startMeasuringLoop() {
        measureJob?.cancel()
        measureJob = scope.launch {
            appPreferences.setIsRunning(true)
            while (isActive) {
                try {
                    val targetUrl = appPreferences.targetUrl.first()
                    val intervalSeconds = appPreferences.intervalSeconds.first()

                    // Perform measurement
                    performMeasurementAndSave(targetUrl)

                    // Delay for the specific interval
                    delay(intervalSeconds * 1000L)
                } catch (e: Exception) {
                    Log.e("MeasureService", "Error in measure loop", e)
                    delay(60000L) // Delay 1 min on error before retrying
                }
            }
        }
    }

    private suspend fun performMeasurementAndSave(url: String) {
        val latencyMs = networkMonitor.measureLatency(url)
        
        // Add timeout for location retrieval to prevent hanging
        val location = withTimeoutOrNull(10000L) {
            locationHelper.getCurrentLocation()
        }
        
        if (location == null) {
             Log.w("MeasureService", "Location retrieval timed out or failed")
        }

        val networkInfo = networkInfoHelper.getCurrentNetworkInfo()
        val locationName = location?.let { locationHelper.getLocationName(it.latitude, it.longitude) }
        
        val record = MeasurementRecord(
            timestamp = System.currentTimeMillis(),
            latencyMs = latencyMs,
            networkType = networkInfo.type,
            operatorAlphaShort = networkInfo.operatorAlphaShort,
            cellId = networkInfo.cellId,
            pci = networkInfo.pci,
            bandInfo = networkInfo.band,
            earfcn = null,              // TODO: Extract from getAllCellDataList()
            bandNumber = null,          // TODO: Extract from bandInfo
            signalStrength = networkInfo.signalStrength,
            bandwidth = networkInfo.bandwidth,
            neighborCells = null,
            timingAdvance = networkInfo.timingAdvance,
            isRegistered = true,
            subscriptionId = -1,
            rssi = networkInfo.rssi,
            rsrp = networkInfo.rsrp,
            rsrq = networkInfo.rsrq,
            sinr = networkInfo.sinr,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            locationName = locationName
        )
        
        recordDao.insertRecord(record)
        Log.d("MeasureService", "Recorded: $record")
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        scope.launch { appPreferences.setIsRunning(false) }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Latency Check Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Latency Check Active")
            .setContentText("Monitoring network latency periodically.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }
}
