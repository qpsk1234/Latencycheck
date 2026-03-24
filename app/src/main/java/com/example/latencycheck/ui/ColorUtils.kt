package com.example.latencycheck.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.json.JSONArray

object ColorUtils {
    data class LatencyLevel(val threshold: Long, val color: Int)

    fun parseConfig(json: String): List<LatencyLevel> {
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<LatencyLevel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val threshold = obj.getLong("threshold")
                val colorStr = obj.getString("color")
                list.add(LatencyLevel(threshold, Color.parseColor(colorStr)))
            }
            list.sortedBy { it.threshold }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLatencyColor(latency: Long, jsonConfig: String): Int {
        val levels = parseConfig(jsonConfig)
        if (levels.isEmpty()) return Color.GRAY
        
        for (level in levels) {
            if (latency <= level.threshold) {
                return level.color
            }
        }
        return levels.last().color
    }

    fun getSignalColor(rsrp: Int?): Int {
        if (rsrp == null) return Color.parseColor("#9E9E9E")
        return when {
            rsrp >= -80 -> Color.parseColor("#4CAF50")
            rsrp >= -90 -> Color.parseColor("#8BC34A")
            rsrp >= -100 -> Color.parseColor("#FFEB3B")
            rsrp >= -110 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
    }

    fun getNetworkTypeColor(type: String): Int {
        return when {
            type.contains("NSA", ignoreCase = true) -> Color.parseColor("#FF9800")  // NSA first
            type.contains("SA", ignoreCase = true) -> Color.parseColor("#4CAF50")   // Then SA
            type.contains("LTE", ignoreCase = true) -> Color.parseColor("#2196F3")
            else -> Color.parseColor("#9E9E9E")
        }
    }

    fun createCustomMarker(context: Context, networkType: String, color: Int): Drawable {
        val size = (36 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f * 0.8f
        
        // Drawing shapes - Check NSA first to avoid matching "SA" part of "NSA"
        when {
            networkType.contains("NSA", ignoreCase = true) -> {
                // Triangle for NSA
                val path = Path()
                path.moveTo(cx, cy - radius)
                path.lineTo(cx + radius, cy + radius)
                path.lineTo(cx - radius, cy + radius)
                path.close()
                canvas.drawPath(path, paint)
            }
            networkType.contains("SA", ignoreCase = true) -> {
                // Circle for SA
                canvas.drawCircle(cx, cy, radius, paint)
            }
            networkType.contains("LTE", ignoreCase = true) -> {
                // Square for LTE
                val side = radius * 1.5f
                canvas.drawRect(cx - side/2, cy - side/2, cx + side/2, cy + side/2, paint)
            }
            else -> {
                // Diamond for others
                val path = Path()
                path.moveTo(cx, cy - radius)
                path.lineTo(cx + radius, cy)
                path.lineTo(cx, cy + radius)
                path.lineTo(cx - radius, cy)
                path.close()
                canvas.drawPath(path, paint)
            }
        }

        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 4f

        // Drawing strokes - Check NSA first
        when {
            networkType.contains("NSA", ignoreCase = true) -> {
                val path = Path()
                path.moveTo(cx, cy - radius)
                path.lineTo(cx + radius, cy + radius)
                path.lineTo(cx - radius, cy + radius)
                path.close()
                canvas.drawPath(path, paint)
            }
            networkType.contains("SA", ignoreCase = true) -> canvas.drawCircle(cx, cy, radius, paint)
            networkType.contains("LTE", ignoreCase = true) -> {
                val side = radius * 1.5f
                canvas.drawRect(cx - side/2, cy - side/2, cx + side/2, cy + side/2, paint)
            }
            else -> {
                val path = Path()
                path.moveTo(cx, cy - radius)
                path.lineTo(cx + radius, cy)
                path.lineTo(cx, cy + radius)
                path.lineTo(cx - radius, cy)
                path.close()
                canvas.drawPath(path, paint)
            }
        }

        // Add text with larger size
        val textPaint = Paint().apply {
            this.color = Color.BLACK
            textSize = (14 * context.resources.displayMetrics.density)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        // Check NSA first to avoid matching "SA" in "NSA"
        val textToDisplay = when {
            networkType.contains("NSA", ignoreCase = true) -> "NSA"
            networkType.contains("SA", ignoreCase = true) -> "SA"
            networkType.contains("LTE", ignoreCase = true) -> "LTE"
            else -> ""
        }

        if (textToDisplay.isNotEmpty()) {
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(textToDisplay, 0, textToDisplay.length, textBounds)
            val textY = cy + textBounds.height() / 2
            canvas.drawText(textToDisplay, cx, textY, textPaint)
        }
        
        return BitmapDrawable(context.resources, bitmap)
    }
}
