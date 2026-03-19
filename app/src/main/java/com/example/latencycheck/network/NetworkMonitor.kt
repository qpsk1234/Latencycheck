package com.example.latencycheck.network

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun measureLatency(url: String): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .head() // Using HEAD to minimize payload, or switch to GET if exact payload download latency is needed
            .build()
        
        val startTime = SystemClock.elapsedRealtime()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext -1L // Error occurred (e.g. 404, 500)
                }
            }
            val endTime = SystemClock.elapsedRealtime()
            (endTime - startTime)
        } catch (e: IOException) {
            -1L // Connection failed
        } catch (e: Exception) {
            -1L
        }
    }
}
