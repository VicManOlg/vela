package io.vela.core.ui.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/** Logical buttons. D-pad is not here on purpose: Compose focus handles it natively. */
enum class GamepadButton { A, B, X, Y, L1, R1, L2, R2, START, SELECT, L3, R3 }

/** Tunables that come from settings. */
data class GamepadConfig(
    /** When true the physical B confirms and A goes back (Nintendo layout). */
    val swapConfirmCancel: Boolean = false,
    val stickNavigation: Boolean = true,
    val deadZone: Float = 0.5f,
    val repeatInitialDelayMs: Long = 400,
    val repeatIntervalMs: Long = 90,
    val repeatFastAfterMs: Long = 1500,
    val repeatFastIntervalMs: Long = 40,
)

/**
 * Activity-level gamepad plumbing.
 *
 * - Face buttons other than A/B, shoulders, triggers, Start/Select go to a LIFO stack of handlers
 *   (the top-most screen or dialog wins) and are consumed.
 * - A and B are left alone so Android's fallback mapping (A -> DPAD_CENTER, B -> BACK) keeps
 *   Compose `clickable` and `BackHandler` working with zero glue. Swapping is done by remapping
 *   the key code before the framework sees it.
 * - Analog sticks and hat axes are converted into synthetic D-pad key events with our own repeat
 *   timer, dispatched through the window so Compose focus moves exactly like a real D-pad.
 */
class GamepadInputController(private val scope: CoroutineScope) {

    fun interface Handler {
        /** Return true to consume. */
        fun onButton(button: GamepadButton): Boolean
    }

    var config: GamepadConfig = GamepadConfig()

    /** Where synthetic D-pad events are injected; set by the Activity. */
    var dispatchSyntheticKey: ((KeyEvent) -> Unit)? = null

    private val handlers = CopyOnWriteArrayList<Handler>()
    private var pressed = HashSet<GamepadButton>()
    private var lastDeviceIsGamepad = false

    fun register(handler: Handler): () -> Unit {
        handlers.add(handler)
        return { handlers.remove(handler) }
    }

    /** True when the most recent input came from a controller (used to hide touch affordances). */
    val controllerActive: Boolean get() = lastDeviceIsGamepad

    // ---- Key events -------------------------------------------------------------------------

    /** Returns a possibly remapped event to hand to super, or null when fully consumed. */
    fun onKeyEvent(event: KeyEvent): KeyEvent? {
        if (event.isFromGamepad()) lastDeviceIsGamepad = true
        var e = event
        if (config.swapConfirmCancel) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> e = e.withKeyCode(KeyEvent.KEYCODE_BUTTON_B)
                KeyEvent.KEYCODE_BUTTON_B -> e = e.withKeyCode(KeyEvent.KEYCODE_BUTTON_A)
            }
        }
        val button = buttonFor(e.keyCode) ?: return e
        if (button == GamepadButton.A || button == GamepadButton.B) return e
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) {
                pressed += button
                dispatch(button)
            }
            KeyEvent.ACTION_UP -> pressed -= button
        }
        return null
    }

    private fun dispatch(button: GamepadButton) {
        for (i in handlers.indices.reversed()) {
            if (handlers.getOrNull(i)?.onButton(button) == true) return
        }
    }

    // ---- Motion events ----------------------------------------------------------------------

    private var horizontal = 0
    private var vertical = 0
    private var repeatJob: Job? = null
    private var triggerLeft = false
    private var triggerRight = false

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE) return false
        lastDeviceIsGamepad = true

        // Analog triggers as L2/R2 for devices that report them only as axes.
        val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        triggerLeft = edge(triggerLeft, lt > 0.6f, GamepadButton.L2)
        triggerRight = edge(triggerRight, rt > 0.6f, GamepadButton.R2)

        if (!config.stickNavigation) return true
        val x = pick(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_HAT_X))
        val y = pick(event.getAxisValue(MotionEvent.AXIS_Y), event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        val h = direction(x)
        val v = direction(y)
        if (h != horizontal || v != vertical) {
            horizontal = h
            vertical = v
            restartRepeat()
        }
        return true
    }

    private fun edge(was: Boolean, now: Boolean, button: GamepadButton): Boolean {
        if (now && !was) dispatch(button)
        return now
    }

    private fun pick(stick: Float, hat: Float): Float = if (abs(hat) > 0.5f) hat else stick

    private fun direction(value: Float): Int = when {
        value > config.deadZone -> 1
        value < -config.deadZone -> -1
        else -> 0
    }

    private fun restartRepeat() {
        repeatJob?.cancel()
        if (horizontal == 0 && vertical == 0) return
        repeatJob = scope.launch {
            val start = System.currentTimeMillis()
            sendDirection()
            delay(config.repeatInitialDelayMs)
            while (isActive) {
                sendDirection()
                val held = System.currentTimeMillis() - start
                delay(if (held > config.repeatFastAfterMs) config.repeatFastIntervalMs else config.repeatIntervalMs)
            }
        }
    }

    private fun sendDirection() {
        val code = when {
            vertical < 0 -> KeyEvent.KEYCODE_DPAD_UP
            vertical > 0 -> KeyEvent.KEYCODE_DPAD_DOWN
            horizontal < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
            horizontal > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> return
        }
        val now = android.os.SystemClock.uptimeMillis()
        dispatchSyntheticKey?.invoke(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_DPAD))
        dispatchSyntheticKey?.invoke(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_DPAD))
    }

    fun onTouch() {
        lastDeviceIsGamepad = false
    }

    private fun KeyEvent.isFromGamepad(): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD

    private fun KeyEvent.withKeyCode(code: Int) =
        KeyEvent(downTime, eventTime, action, code, repeatCount, metaState, deviceId, scanCode, flags, source)

    companion object {
        fun buttonFor(keyCode: Int): GamepadButton? = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> GamepadButton.A
            KeyEvent.KEYCODE_BUTTON_B -> GamepadButton.B
            KeyEvent.KEYCODE_BUTTON_X -> GamepadButton.X
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadButton.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadButton.L1
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadButton.R1
            KeyEvent.KEYCODE_BUTTON_L2 -> GamepadButton.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> GamepadButton.R2
            KeyEvent.KEYCODE_BUTTON_START -> GamepadButton.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadButton.SELECT
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadButton.L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadButton.R3
            // Keyboard equivalents for development on desktop / emulator.
            KeyEvent.KEYCODE_X -> GamepadButton.X
            KeyEvent.KEYCODE_Y -> GamepadButton.Y
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_PAGE_UP -> GamepadButton.L1
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_PAGE_DOWN -> GamepadButton.R1
            KeyEvent.KEYCODE_MENU -> GamepadButton.START
            else -> null
        }
    }
}

val LocalGamepad = compositionLocalOf<GamepadInputController?> { null }

/**
 * Registers a button handler for as long as this composable is in the composition. The most
 * recently composed handler gets the event first, which naturally gives dialogs priority.
 */
@Composable
fun GamepadHandler(onButton: (GamepadButton) -> Boolean) {
    val controller = LocalGamepad.current ?: return
    val current = rememberUpdatedState(onButton)
    DisposableEffect(controller) {
        val unregister = controller.register { button -> current.value(button) }
        onDispose { unregister() }
    }
}
