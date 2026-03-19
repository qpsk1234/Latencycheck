import android.graphics.Color
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
}
