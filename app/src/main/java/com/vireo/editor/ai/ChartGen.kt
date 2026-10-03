package com.vireo.editor.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Text-to-chart: turn numbers (or an AI's analysis) into a branded chart image
 * that can be dropped straight onto the timeline as a B-roll card.
 *
 * Rendering is a plain HTTPS GET against QuickChart - no API key, no sign-up -
 * and the chart is themed to match the Vireo palette so it looks native to the
 * app rather than like a stock spreadsheet export.
 *
 * Typical use: ask the AI for retention or views data, hand the JSON here, and
 * burn the result in as an overlay.
 */
class ChartGen {

    enum class ChartType(val id: String, val label: String) {
        BAR("bar", "Bar"),
        LINE("line", "Line"),
        PIE("pie", "Pie"),
        DOUGHNUT("doughnut", "Doughnut"),
        RADAR("radar", "Radar"),
        POLAR("polarArea", "Polar"),
        HORIZONTAL_BAR("horizontalBar", "Horizontal bar")
    }

    data class Series(val label: String, val values: List<Float>)

    /** Render a chart and return it as a bitmap ready for the timeline. */
    suspend fun render(
        type: ChartType,
        labels: List<String>,
        series: List<Series>,
        title: String = "",
        width: Int = 1280,
        height: Int = 720,
        transparent: Boolean = true
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (labels.isEmpty() || series.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Chart needs labels and data"))
        }

        val config = buildConfig(type, labels, series, title)
        val url = StringBuilder(BASE).apply {
            append("?w=").append(width.coerceIn(200, 2000))
            append("&h=").append(height.coerceIn(200, 2000))
            append("&bkg=").append(if (transparent) "transparent" else "%230A0A0C")
            append("&v=4")
            append("&c=").append(URLEncoder.encode(config, "UTF-8"))
        }.toString()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 45_000
                setRequestProperty("User-Agent", "Vireo/1.0 (Android)")
                setRequestProperty("Accept", "image/png")
            }
            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Chart service failed (HTTP ${connection.responseCode})")
                )
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(IllegalStateException("Chart data unreadable"))
            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Could not reach the chart service", e))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parse a loose "Label: value" block - the shape an LLM returns most
     * reliably - into chart inputs. Blank or malformed lines are skipped.
     */
    fun parse(text: String): Pair<List<String>, List<Float>> {
        val labels = mutableListOf<String>()
        val values = mutableListOf<Float>()
        text.lineSequence().forEach { line ->
            val parts = line.split(":", "-", "\t", "|").map { it.trim() }
            if (parts.size >= 2) {
                val value = parts.last().replace(Regex("[^0-9.\\-]"), "").toFloatOrNull()
                val label = parts.first().removePrefix("*").removePrefix("•").trim()
                if (value != null && label.isNotBlank()) {
                    labels += label.take(18)
                    values += value
                }
            }
        }
        return labels to values
    }

    private fun buildConfig(
        type: ChartType,
        labels: List<String>,
        series: List<Series>,
        title: String
    ): String {
        val datasets = JSONArray()
        series.forEachIndexed { index, s ->
            val isCircular = type == ChartType.PIE || type == ChartType.DOUGHNUT ||
                type == ChartType.POLAR

            val dataset = JSONObject().apply {
                put("label", s.label)
                put("data", JSONArray(s.values.map { it.toDouble() }))
                put("borderWidth", if (type == ChartType.LINE) 3 else 0)
                put("borderRadius", 8)
                put("tension", 0.35)
                put("fill", type == ChartType.LINE)

                if (isCircular) {
                    // One colour per slice.
                    put("backgroundColor", JSONArray(labels.indices.map { PALETTE[it % PALETTE.size] }))
                } else {
                    val colour = PALETTE[index % PALETTE.size]
                    put("backgroundColor", if (type == ChartType.LINE) "${colour}55" else colour)
                    put("borderColor", colour)
                    put("pointBackgroundColor", colour)
                    put("pointRadius", 4)
                }
            }
            datasets.put(dataset)
        }

        val scaleStyle = JSONObject().apply {
            put("grid", JSONObject().put("color", "#2A2A33"))
            put("ticks", JSONObject().put("color", "#8E94A8").put("font", JSONObject().put("size", 14)))
        }

        val options = JSONObject().apply {
            put("plugins", JSONObject().apply {
                put("legend", JSONObject().apply {
                    put("display", series.size > 1 || type == ChartType.PIE || type == ChartType.DOUGHNUT)
                    put("labels", JSONObject().put("color", "#E9E9F0")
                        .put("font", JSONObject().put("size", 15)))
                })
                if (title.isNotBlank()) {
                    put("title", JSONObject()
                        .put("display", true)
                        .put("text", title)
                        .put("color", "#E9E9F0")
                        .put("font", JSONObject().put("size", 22).put("weight", "bold")))
                }
            })
            // Circular charts have no cartesian axes to style.
            if (type != ChartType.PIE && type != ChartType.DOUGHNUT && type != ChartType.POLAR) {
                put("scales", JSONObject().put("x", scaleStyle).put("y", scaleStyle))
            }
        }

        return JSONObject().apply {
            put("type", type.id)
            put("data", JSONObject().put("labels", JSONArray(labels)).put("datasets", datasets))
            put("options", options)
        }.toString()
    }

    private companion object {
        const val BASE = "https://quickchart.io/chart"

        /** Vireo brand palette, so charts match the app. */
        val PALETTE = listOf(
            "#8B5CF6", "#22D3EE", "#F59E0B", "#EF4444",
            "#34D399", "#F472B6", "#60A5FA", "#A78BFA"
        )
    }
}
