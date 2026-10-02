package io.vela.core.catalog

import com.google.common.truth.Truth.assertThat
import io.vela.core.model.HomeLayout
import io.vela.core.model.LibraryLayout
import io.vela.core.model.LibraryView
import org.junit.Test

/** Every bundled theme decodes, and the structure keys it names exist in the app. */
class ThemeLayoutsTest {

    private val themes = ThemeCatalog().themes

    @Test
    fun `fourteen bundled themes with unique ids and names`() {
        assertThat(themes).hasSize(14)
        assertThat(themes.map { it.id }.toSet()).hasSize(14)
        assertThat(themes.map { it.name }.toSet()).hasSize(14)
    }

    @Test
    fun `layout keys named by themes map to real layouts`() {
        val homeKeys = HomeLayout.entries.filter { it != HomeLayout.THEME }.map { it.name.lowercase() }
        val libraryKeys = LibraryLayout.entries.filter { it != LibraryLayout.THEME }.map { it.name.lowercase() }
        val viewKeys = LibraryView.entries.filter { it != LibraryView.THEME }.map { it.name.lowercase() }
        themes.forEach { t ->
            t.layout.homeLayout?.let { assertThat(homeKeys).contains(it) }
            t.layout.libraryLayout?.let { assertThat(libraryKeys).contains(it) }
            t.layout.libraryView?.let { assertThat(viewKeys).contains(it) }
            assertThat(listOf("focused", "always", "never")).contains(t.layout.cardLabels)
            assertThat(listOf("artwork", "hero", "stage", "platform", "static")).contains(t.background.mode)
            assertThat(listOf("systematic", "flatui", "monochrome", "none")).contains(t.platformIcons.set)
        }
    }

    @Test
    fun `the new themes exercise every new systems and games view`() {
        val libraryLayouts = themes.mapNotNull { it.layout.libraryLayout }.toSet()
        val libraryViews = themes.mapNotNull { it.layout.libraryView }.toSet()
        assertThat(libraryLayouts).containsAtLeast("wheel", "mosaic", "columns", "book")
        assertThat(libraryViews).containsAtLeast("hero", "wall", "details", "book")
        assertThat(themes.mapNotNull { it.layout.homeLayout }.toSet()).containsAtLeast("tiles", "strip", "dashboard", "spotlight", "carousel")
    }

    @Test
    fun `fromKey is case-insensitive and falls back`() {
        assertThat(LibraryLayout.fromKey("Wheel")).isEqualTo(LibraryLayout.WHEEL)
        assertThat(LibraryLayout.fromKey(null)).isEqualTo(LibraryLayout.STAGE)
        assertThat(LibraryLayout.fromKey("theme")).isEqualTo(LibraryLayout.STAGE)
        assertThat(LibraryView.fromKey("details")).isEqualTo(LibraryView.DETAILS)
        assertThat(LibraryView.fromKey("nope")).isEqualTo(LibraryView.GRID)
    }
}
