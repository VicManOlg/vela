package io.vela.core.scraper

import com.google.common.truth.Truth.assertThat
import io.vela.core.scraper.provider.LibretroIndex
import org.junit.Test

class LibretroIndexTest {

    private val listing = """
        <html><head><title>Index of /Nintendo - Nintendo DS/Named_Boxarts</title></head><body>
        <tr><th><a href="?C=N;O=D">Name</a></th></tr>
        <tr><td><a href="/Nintendo%20-%20Nintendo%20DS/">Parent Directory</a></td></tr>
        <tr><td><a href="Pokemon%20-%20HeartGold%20Version%20(USA).png">Pokemon - HeartGold Version (USA).png</a></td></tr>
        <tr><td><a href="Dr.%20Mario%20Express%20(USA).png">Dr. Mario Express (USA).png</a></td></tr>
        <tr><td><a href="Mario%20%2B%20Luigi%20(Europe)%20(En%2CFr%2CDe%2CEs%2CIt).png">x</a></td></tr>
        <tr><td><a href="Pokemon%20-%20HeartGold%20Version%20(USA).png">duplicate</a></td></tr>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses decoded names without extension, skipping directories and duplicates`() {
        assertThat(LibretroIndex.parseListing(listing)).containsExactly(
            "Pokemon - HeartGold Version (USA)",
            "Dr. Mario Express (USA)",
            "Mario + Luigi (Europe) (En,Fr,De,Es,It)",
        ).inOrder()
    }

    @Test
    fun `empty or unexpected html yields no names`() {
        assertThat(LibretroIndex.parseListing("")).isEmpty()
        assertThat(LibretroIndex.parseListing("<html><body>Not Found</body></html>")).isEmpty()
    }
}
