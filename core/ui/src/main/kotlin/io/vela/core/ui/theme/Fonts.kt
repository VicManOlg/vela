package io.vela.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.vela.core.ui.R

/** Bundled variable fonts (OFL, licences in /licenses). Theme JSON references them by key. */
object VelaFonts {
    private fun variable(res: Int, vararg weights: FontWeight, width: Float? = null): FontFamily = FontFamily(
        weights.map { w ->
            val axes = listOfNotNull(FontVariation.weight(w.weight), width?.let(FontVariation::width))
            Font(res, weight = w, variationSettings = FontVariation.Settings(*axes.toTypedArray()))
        },
    )

    private val weights = arrayOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

    val outfit: FontFamily by lazy { variable(R.font.outfit_variable, *weights) }
    val manrope: FontFamily by lazy { variable(R.font.manrope_variable, *weights) }
    /** Archivo semi-expanded: wide, solid titles like the label on a cartridge box, still fitting long system names. */
    val archivo: FontFamily by lazy { variable(R.font.archivo_variable, *weights, width = 112f) }
    /** Archivo at its widest, for short display text. */
    val archivoExpanded: FontFamily by lazy { variable(R.font.archivo_variable, *weights, width = 125f) }
    /** Designed by the Braille Institute to keep look-alike letters apart at a distance. */
    val atkinson: FontFamily by lazy { variable(R.font.atkinson_variable, *weights) }

    fun family(key: String): FontFamily = when (key.lowercase()) {
        "outfit" -> outfit
        "manrope" -> manrope
        "archivo" -> archivo
        "archivo-expanded" -> archivoExpanded
        "atkinson" -> atkinson
        "serif" -> FontFamily.Serif
        "mono", "monospace" -> FontFamily.Monospace
        "system", "sans" -> FontFamily.SansSerif
        else -> outfit
    }
}
