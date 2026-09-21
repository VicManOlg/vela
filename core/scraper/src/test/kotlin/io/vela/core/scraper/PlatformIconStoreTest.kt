package io.vela.core.scraper

import com.google.common.truth.Truth.assertThat
import io.vela.core.model.ThemePlatformIcons
import io.vela.core.scraper.store.PlatformIconStore
import org.junit.Test

class PlatformIconStoreTest {

    @Test
    fun `fills the default template with an encoded set and name`() {
        val url = PlatformIconStore.urlFor(ThemePlatformIcons().urlTemplate, "systematic", "Sega - Mega Drive - Genesis")
        assertThat(url).isEqualTo(
            "https://raw.githubusercontent.com/libretro/retroarch-assets/master/xmb/systematic/png/Sega%20-%20Mega%20Drive%20-%20Genesis.png",
        )
    }

    @Test
    fun `custom templates work with any placeholder order`() {
        val url = PlatformIconStore.urlFor("https://example.org/{name}/{set}.png", "mono chrome", "Nintendo - Nintendo DS")
        assertThat(url).isEqualTo("https://example.org/Nintendo%20-%20Nintendo%20DS/mono%20chrome.png")
    }
}
