package io.vela.core.launcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RomIdTest {
    @Test
    fun `the id is the last bracketed code, else the whole stem`() {
        assertThat(romId("Gravity Rush [PCSF00024]")).isEqualTo("PCSF00024")
        assertThat(romId("Ys VIII- Lacrimosa of DANA (EU) [PCSH00297]")).isEqualTo("PCSH00297")
        assertThat(romId("PCSE00001")).isEqualTo("PCSE00001")
    }
}
