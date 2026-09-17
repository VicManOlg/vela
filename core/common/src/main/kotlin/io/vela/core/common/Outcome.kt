package io.vela.core.common

/** Explicit success/failure type for service boundaries (launching, scraping, scanning). */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: VelaError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): VelaError? = (this as? Failure)?.error

    companion object {
        fun <T> success(value: T): Outcome<T> = Success(value)
        fun failure(error: VelaError): Outcome<Nothing> = Failure(error)
    }
}

/** User-presentable error with an optional technical cause. */
sealed class VelaError(open val message: String, open val cause: Throwable? = null) {
    data class NotInstalled(val packageNames: List<String>, val playerName: String) :
        VelaError("$playerName is not installed")

    data class NoPlayer(val platformName: String) :
        VelaError("No emulator configured for $platformName")

    data class FileNotAccessible(val location: String) :
        VelaError("Cannot access $location")

    data class LaunchFailed(override val message: String, override val cause: Throwable? = null) : VelaError(message, cause)

    data class Network(override val message: String, override val cause: Throwable? = null) : VelaError(message, cause)

    data class RateLimited(val providerId: String, val retryAfterMs: Long?) :
        VelaError("$providerId rate limit reached")

    data class Unauthorized(val providerId: String) : VelaError("$providerId credentials rejected")

    data class Unexpected(override val message: String, override val cause: Throwable? = null) : VelaError(message, cause)
}

inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: Throwable) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    Outcome.Failure(VelaError.Unexpected(e.message ?: e::class.simpleName ?: "error", e))
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) block(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(block: (VelaError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(error)
    return this
}
