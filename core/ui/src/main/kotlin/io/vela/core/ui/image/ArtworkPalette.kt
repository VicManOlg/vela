package io.vela.core.ui.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Dominant, saturated colour of a piece of artwork, made usable as an accent on the interface:
 * hue from the most vivid area of the image, saturation and brightness clamped so text and
 * glows stay readable. Decodes at ~48px and remembers the last few hundred answers.
 */
object ArtworkPalette {

    private val cache = object : LinkedHashMap<String, Long?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long?>?): Boolean = size > 300
    }

    /** ARGB colour, or null when the file is missing or has no vivid area (grey box art, logos on black…). */
    suspend fun dominant(path: String): Long? = withContext(Dispatchers.Default) {
        synchronized(cache) { if (cache.containsKey(path)) return@withContext cache[path] }
        val result = runCatching { compute(path) }.getOrNull()
        synchronized(cache) { cache[path] = result }
        result
    }

    private fun compute(path: String): Long? {
        if (!File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        val sample = max(1, min(bounds.outWidth, bounds.outHeight) / 48)
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: return null
        try {
            val weight = FloatArray(BINS)
            val red = FloatArray(BINS)
            val green = FloatArray(BINS)
            val blue = FloatArray(BINS)
            val hsv = FloatArray(3)
            for (y in 0 until bitmap.height step 2) {
                for (x in 0 until bitmap.width step 2) {
                    val c = bitmap.getPixel(x, y)
                    if (c ushr 24 < 128) continue
                    Color.colorToHSV(c, hsv)
                    val s = hsv[1]
                    val v = hsv[2]
                    if (s < 0.28f || v < 0.22f) continue
                    val w = s * s * v
                    val bin = (hsv[0] / (360f / BINS)).toInt().coerceIn(0, BINS - 1)
                    weight[bin] += w
                    red[bin] += Color.red(c) * w
                    green[bin] += Color.green(c) * w
                    blue[bin] += Color.blue(c) * w
                }
            }
            val best = weight.indices.maxByOrNull { weight[it] } ?: return null
            if (weight[best] < MIN_WEIGHT) return null
            val average = Color.rgb((red[best] / weight[best]).toInt(), (green[best] / weight[best]).toInt(), (blue[best] / weight[best]).toInt())
            Color.colorToHSV(average, hsv)
            hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
            hsv[2] = hsv[2].coerceIn(0.72f, 1f)
            return 0xFF000000L or (Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL)
        } finally {
            bitmap.recycle()
        }
    }

    private const val BINS = 24
    private const val MIN_WEIGHT = 6f
}
