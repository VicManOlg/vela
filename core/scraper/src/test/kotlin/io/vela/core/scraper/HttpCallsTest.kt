package io.vela.core.scraper

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class HttpCallsTest {

    @Test
    fun `cancelling the coroutine aborts a call the server never answers`() = runBlocking {
        ServerSocket(0).use { server ->
            // Accepts the connection and then says nothing, like a stalled CDN.
            val accepted = Thread { runCatching { server.accept() } }.apply { isDaemon = true; start() }
            val client = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
            val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build())

            var outcome: Throwable? = null
            val elapsed = measureTimeMillis {
                val request = async(Dispatchers.IO) { call.executeCancellable { it.code } }
                delay(300)
                request.cancel()
                outcome = runCatching { request.await() }.exceptionOrNull()
            }

            assertThat(outcome).isInstanceOf(CancellationException::class.java)
            assertThat(call.isCanceled()).isTrue()
            assertThat(elapsed).isLessThan(5_000)
            accepted.interrupt()
        }
    }
}
