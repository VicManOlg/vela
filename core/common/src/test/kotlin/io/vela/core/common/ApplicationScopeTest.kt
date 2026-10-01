package io.vela.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationScopeTest {

    @Test
    fun `a failing job is reported and the scope keeps running`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io = dispatcher
            override val default = dispatcher
            override val main = dispatcher
        }
        val errors = mutableListOf<Throwable>()
        val scope = ApplicationScope(dispatchers) { errors += it }

        scope.launch { error("disk full") }
        var ranAfter = false
        scope.launch { ranAfter = true }

        assertThat(errors.map { it.message }).containsExactly("disk full")
        assertThat(ranAfter).isTrue()
        assertThat(scope.isActive).isTrue()
    }
}
