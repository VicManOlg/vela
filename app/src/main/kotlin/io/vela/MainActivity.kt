package io.vela

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.vela.core.ui.input.GamepadConfig
import io.vela.core.ui.input.GamepadInputController
import io.vela.core.settings.SettingsRepository
import io.vela.ui.VelaApp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Intent
import io.vela.core.ui.sound.UiSound
import io.vela.core.ui.sound.UiSounds
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The only Activity. Owns the gamepad controller so every key/motion event is seen before Compose,
 * keeps the window immersive, and hosts the Compose tree.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var startup: StartupCoordinator

    private lateinit var gamepad: GamepadInputController
    private val sounds: UiSounds by lazy { (application as VelaApplication).uiSounds }
    /** Emits when the Home button brings the app back while it is already running. */
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        gamepad = GamepadInputController(lifecycleScope)
        gamepad.dispatchSyntheticKey = { event -> window.decorView.dispatchKeyEvent(event) }
        gamepad.performBack = { sounds.play(UiSound.BACK); onBackPressedDispatcher.onBackPressed() }

        lifecycleScope.launch {
            settings.settings.collectLatest { s ->
                gamepad.config = GamepadConfig(
                    swapConfirmCancel = s.confirmButton == io.vela.core.model.ConfirmButton.B,
                    stickNavigation = s.analogStickNavigation,
                    deadZone = s.stickDeadZone,
                    repeatInitialDelayMs = s.repeatInitialDelayMs.toLong(),
                    repeatIntervalMs = s.repeatIntervalMs.toLong(),
                    repeatFastAfterMs = s.repeatFastAfterMs.toLong(),
                    repeatFastIntervalMs = s.repeatFastIntervalMs.toLong(),
                )
                sounds.enabled = s.uiSounds
                sounds.volume = s.uiSoundVolume
            }
        }

        setContent { VelaApp(gamepad, homePresses, sounds) }
        startup.onAppStarted()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.tryEmit(Unit)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Synthetic D-pad events we injected carry FLAG_SOFT_KEYBOARD; let them straight through.
        if (event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.keyCode == KeyEvent.KEYCODE_BACK) sounds.play(UiSound.BACK)
        val forwarded = gamepad.onKeyEvent(event) ?: return true
        return super.dispatchKeyEvent(forwarded)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepad.onGenericMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gamepad.onTouch()
        return super.dispatchTouchEvent(ev)
    }
}
