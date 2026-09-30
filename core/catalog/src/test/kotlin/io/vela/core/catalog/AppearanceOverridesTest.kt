package io.vela.core.catalog

import com.google.common.truth.Truth.assertThat
import io.vela.core.common.VelaJson
import io.vela.core.model.AppSettings
import io.vela.core.model.AppearanceOverrides
import io.vela.core.model.CardLabels
import io.vela.core.model.applying
import org.junit.Test

/** The user's Appearance tweaks layered over bundled themes. */
class AppearanceOverridesTest {

    private val themes = ThemeCatalog()

    @Test
    fun `no overrides returns the theme untouched`() {
        val night = themes.byId("vela-night")
        assertThat(night.applying(AppearanceOverrides())).isSameInstanceAs(night)
        assertThat(AppearanceOverrides().isEmpty).isTrue()
    }

    @Test
    fun `overrides replace only what they name and survive a theme switch`() {
        val o = AppearanceOverrides(accent = "#FFFF9A3D", cardRadius = 0f, cardLabels = CardLabels.ALWAYS, fontScale = 1.2f, backgroundColor = "#FF000000", platformIconSet = "monochrome")
        val night = themes.byId("vela-night").applying(o)
        val joy = themes.byId("vela-joy").applying(o)

        assertThat(night.colors.accent).isEqualTo("#FFFF9A3D")
        assertThat(joy.colors.accent).isEqualTo("#FFFF9A3D")
        assertThat(night.colors.accentSecondary).isEqualTo(themes.byId("vela-night").colors.accentSecondary)
        assertThat(joy.colors.accentSecondary).isEqualTo(themes.byId("vela-joy").colors.accentSecondary)
        assertThat(night.shapes.cardRadius).isEqualTo(0f)
        assertThat(night.shapes.tileRadius).isEqualTo(0f)
        assertThat(night.layout.cardLabels).isEqualTo("always")
        assertThat(night.typography.bodySize).isWithin(0.001f).of(themes.byId("vela-night").typography.bodySize * 1.2f)
        assertThat(night.colors.background).isEqualTo("#FF000000")
        assertThat(night.background.staticColor).isEqualTo("#FF000000")
        assertThat(joy.background.staticColor).isEqualTo("#FF000000")
        assertThat(night.platformIcons.set).isEqualTo("monochrome")
        assertThat(night.platformIcons.tint).isTrue()
        // Structure the user did not touch stays the theme's.
        assertThat(joy.layout.homeLayout).isEqualTo("tiles")
        assertThat(joy.layout.showTabs).isFalse()
    }

    @Test
    fun `settings json without appearance decodes to no overrides and unknown keys are ignored`() {
        val legacy = VelaJson.decodeFromString(AppSettings.serializer(), """{"themeId":"vela-ember","gridColumns":7}""")
        assertThat(legacy.appearance.isEmpty).isTrue()
        assertThat(legacy.gridColumns).isEqualTo(7)

        val future = VelaJson.decodeFromString(AppSettings.serializer(), """{"appearance":{"accent":"#FF3D7BFF","somethingNew":42,"cardLabels":"UNKNOWN_VALUE"}}""")
        assertThat(future.appearance.accent).isEqualTo("#FF3D7BFF")
        assertThat(future.appearance.cardLabels).isNull()

        val roundTrip = VelaJson.decodeFromString(AppSettings.serializer(), VelaJson.encodeToString(AppSettings.serializer(), AppSettings(appearance = AppearanceOverrides(backgroundDim = 0.4f))))
        assertThat(roundTrip.appearance.backgroundDim).isEqualTo(0.4f)
    }
}
