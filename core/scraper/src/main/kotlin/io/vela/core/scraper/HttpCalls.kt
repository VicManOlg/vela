package io.vela.core.scraper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response
import java.io.IOException

/**
 * `execute().use(read)` that the coroutine can stop. A blocking call ignores cancellation, so a
 * cancelled scrape kept its socket busy for up to the call timeout (90 s) and the scrape job stayed
 * active. A watchdog child cancels the call instead, which aborts a connect, a read or a body
 * download at once; the resulting IOException surfaces as the CancellationException it really is.
 */
internal suspend inline fun <T> Call.executeCancellable(read: (Response) -> T): T {
    val call = this
    val watchdog = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }
    try {
        return call.execute().use(read)
    } catch (e: IOException) {
        currentCoroutineContext().ensureActive()
        throw e
    } finally {
        watchdog.cancel()
    }
}
