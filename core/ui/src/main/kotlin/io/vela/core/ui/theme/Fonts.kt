package io.vela.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.vela.core.ui.R

/** Bundled variable fonts (OFL). Theme JSON references them by key. */
object VelaFonts {
    private fun variable(res: Int, vararg weights: FontWeight): FontFamily = FontFamily(
        weights.map { w -> Font(res, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) },
    )

    private val weights = arrayOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

    val outfit: FontFamily by lazy { variable(R.font.outfit_variable, *weights) }
    val manrope: FontFamily by lazy { variable(R.font.manrope_variable, *weights) }

    fun family(key: String): FontFamily = when (key.lowercase()) {
        "outfit" -> outfit
        "manrope" -> manrope
        "serif" -> FontFamily.Serif
        "mono", "monospace" -> FontFamily.Monospace
        "system", "sans" -> FontFamily.SansSerif
        else -> outfit
    }
}
