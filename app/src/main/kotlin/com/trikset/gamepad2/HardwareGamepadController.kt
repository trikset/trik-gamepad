package com.trikset.gamepad2

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Maps a hardware gamepad (PS-style) onto the gamepad protocol: the D-pad and the left stick drive
 * pad1, the right stick drives pad2 (exchanged when the swap-sticks setting is on), and the face
 * buttons A/B/X/Y plus shoulders L1/R1 map to magic buttons 1–5 — but only within the configured
 * magic-button count. Pure controller: emits commands through [send] and reads the current
 * [Settings] through [settings] on every event, so it is directly unit-testable and needs no
 * activity. MainActivity forwards key/motion events here.
 */
class HardwareGamepadController(
    private val send: (String) -> Unit,
    private val settings: () -> Settings,
) {
  data class Settings(val swapSticks: Boolean, val magicButtonCount: Int)

  private val pad1 = PadState("1")
  private val pad2 = PadState("2")

  /** @return true when the key was consumed (do not fall through to the activity). */
  fun onKeyDown(keyCode: Int, repeatCount: Int): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> setPad(pad1, 0, MAX_VALUE)
      KeyEvent.KEYCODE_DPAD_DOWN -> setPad(pad1, 0, -MAX_VALUE)
      KeyEvent.KEYCODE_DPAD_LEFT -> setPad(pad1, -MAX_VALUE, 0)
      KeyEvent.KEYCODE_DPAD_RIGHT -> setPad(pad1, MAX_VALUE, 0)
      else -> {
        val index = magicButtonIndex(keyCode) ?: return false
        // Ignore key auto-repeat: a held button must not spam `btn N down`.
        if (repeatCount == 0 && index <= settings().magicButtonCount) {
          send("btn $index down")
        }
      }
    }
    return true
  }

  /** @return true when the key was consumed. */
  fun onKeyUp(keyCode: Int): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP,
      KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_LEFT,
      KeyEvent.KEYCODE_DPAD_RIGHT -> setPad(pad1, null, null)
      else -> return magicButtonIndex(keyCode) != null
    }
    return true
  }

  /** @return true when the motion event was consumed (a gamepad stick move). */
  fun onMotionEvent(event: MotionEvent): Boolean {
    if (event.actionMasked != MotionEvent.ACTION_MOVE) {
      return false
    }
    if (
        event.source and InputDevice.SOURCE_JOYSTICK == 0 &&
            event.source and InputDevice.SOURCE_GAMEPAD == 0
    ) {
      return false
    }
    val swap = settings().swapSticks
    // Without swap the left stick (AXIS_X/Y) drives pad1 and the right (AXIS_RX/RY) pad2; with
    // swap the assignment is exchanged.
    driveStick(
        pad1,
        axis(event, swap, MotionEvent.AXIS_X, MotionEvent.AXIS_RX),
        axis(event, swap, MotionEvent.AXIS_Y, MotionEvent.AXIS_RY),
    )
    driveStick(
        pad2,
        axis(event, !swap, MotionEvent.AXIS_X, MotionEvent.AXIS_RX),
        axis(event, !swap, MotionEvent.AXIS_Y, MotionEvent.AXIS_RY),
    )
    return true
  }

  private fun driveStick(pad: PadState, xAxis: Float, yAxis: Float) {
    val x = scaleOrZero(xAxis)
    val y = scaleOrZero(yAxis)
    if (x == null && y == null) {
      setPad(pad, null, null)
    } else {
      setPad(pad, x ?: 0, y ?: 0)
    }
  }

  /** Sends the pad position on change; sends `pad N up` when both axes return to center. */
  private fun setPad(pad: PadState, x: Int?, y: Int?) {
    if (x == null || y == null) {
      if (pad.active) {
        pad.active = false
        send("pad ${pad.name} up")
      }
    } else if (!pad.active || x != pad.x || y != pad.y) {
      pad.active = true
      pad.x = x
      pad.y = y
      send("pad ${pad.name} $x $y")
    }
  }

  private fun axis(
      event: MotionEvent,
      swap: Boolean,
      leftStickAxis: Int,
      rightStickAxis: Int,
  ): Float = event.getAxisValue(if (swap) rightStickAxis else leftStickAxis)

  /** Maps an axis value to -100..100, or null inside the dead zone. */
  private fun scaleOrZero(value: Float): Int? {
    if (value > -DEAD_ZONE && value < DEAD_ZONE) {
      return null
    }
    return (value * MAX_VALUE).toInt().coerceIn(-MAX_VALUE, MAX_VALUE)
  }

  private fun magicButtonIndex(keyCode: Int): Int? =
      when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> MAGIC_INDEX_A
        KeyEvent.KEYCODE_BUTTON_B -> MAGIC_INDEX_B
        KeyEvent.KEYCODE_BUTTON_X -> MAGIC_INDEX_X
        KeyEvent.KEYCODE_BUTTON_Y -> MAGIC_INDEX_Y
        KeyEvent.KEYCODE_BUTTON_L1 -> MAGIC_INDEX_LR
        KeyEvent.KEYCODE_BUTTON_R1 -> MAGIC_INDEX_LR
        else -> null
      }

  private class PadState(val name: String) {
    var active = false
    var x = 0
    var y = 0
  }

  private companion object {
    const val MAX_VALUE = 100
    const val DEAD_ZONE = 0.1f
    const val MAGIC_INDEX_A = 1
    const val MAGIC_INDEX_B = 2
    const val MAGIC_INDEX_X = 3
    const val MAGIC_INDEX_Y = 4
    const val MAGIC_INDEX_LR = 5
  }
}
