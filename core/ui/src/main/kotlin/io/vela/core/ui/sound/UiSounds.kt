package io.vela.core.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import io.vela.core.ui.R

/** The handful of interface sounds, each with its own loudness relative to the user's volume. */
enum class UiSound(val res: Int, val gain: Float) {
    FOCUS(R.raw.ui_focus, 0.55f),
    CONFIRM(R.raw.ui_confirm, 0.9f),
    BACK(R.raw.ui_back, 0.8f),
    TAB(R.raw.ui_tab, 0.7f),
    LAUNCH(R.raw.ui_launch, 1f),
}

/**
 * Console-style interface sounds through a small SoundPool: a tick when focus moves, a chime on
 * confirm, a lower one on back, a note on tab changes and a swell when a game launches. Focus
 * ticks are rate limited so holding a direction does not turn into a rattle.
 */
class UiSounds(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<UiSound, Int> = UiSound.entries.associateWith { pool.load(context, it.res, 1) }

    @Volatile var enabled: Boolean = true
    @Volatile var volume: Float = 0.5f

    private var lastFocusAt = 0L

    fun play(sound: UiSound) {
        if (!enabled || volume <= 0f) return
        if (sound == UiSound.FOCUS) {
            val now = SystemClock.uptimeMillis()
            if (now - lastFocusAt < FOCUS_MIN_GAP_MS) return
            lastFocusAt = now
        }
        val id = ids[sound] ?: return
        val level = (volume * sound.gain).coerceIn(0f, 1f)
        pool.play(id, level, level, 1, 0, 1f)
    }

    private companion object {
        const val FOCUS_MIN_GAP_MS = 35L
    }
}

/** Null where no sound system exists (previews, tests). */
val LocalUiSounds = staticCompositionLocalOf<UiSounds?> { null }
