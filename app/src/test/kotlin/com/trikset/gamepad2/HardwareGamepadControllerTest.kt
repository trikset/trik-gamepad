package com.trikset.gamepad2

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct tests for [HardwareGamepadController] — the pure key/motion -> command mapping. */
@RunWith(RobolectricTestRunner::class)
class HardwareGamepadControllerTest : RobolectricTestBase() {

  private val sent = ArrayList<String>()
  private var swap = false
  private var magicCount = 5
  private val controller =
      HardwareGamepadController(
          send = { sent.add(it) },
          settings = { HardwareGamepadController.Settings(swap, magicCount) },
      )

  private fun stickEvent(
      x: Float = 0f,
      y: Float = 0f,
      rx: Float = 0f,
      ry: Float = 0f,
      action: Int = MotionEvent.ACTION_MOVE,
      source: Int = InputDevice.SOURCE_JOYSTICK,
  ): MotionEvent {
    val coords = MotionEvent.PointerCoords()
    coords.setAxisValue(MotionEvent.AXIS_X, x)
    coords.setAxisValue(MotionEvent.AXIS_Y, y)
    coords.setAxisValue(MotionEvent.AXIS_RX, rx)
    coords.setAxisValue(MotionEvent.AXIS_RY, ry)
    val props = MotionEvent.PointerProperties()
    props.id = 0
    val now = SystemClock.uptimeMillis()
    return MotionEvent.obtain(
        now,
        now,
        action,
        1,
        arrayOf(props),
        arrayOf(coords),
        0,
        0,
        1f,
        1f,
        0,
        0,
        source,
        0,
    )
  }

  @Test
  fun dpadShouldDrivePad1() {
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, 0))
    assertTrue(controller.onKeyUp(KeyEvent.KEYCODE_DPAD_UP))
    assertEquals(
        listOf("pad 1 0 100", "pad 1 100 0", "pad 1 0 -100", "pad 1 -100 0", "pad 1 up"),
        sent,
    )
  }

  @Test
  fun dpadRepeatShouldNotResend() {
    controller.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, 0)
    controller.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, 1)
    assertEquals(listOf("pad 1 0 100"), sent)
  }

  @Test
  fun stickShouldDriveBothPads() {
    controller.onMotionEvent(stickEvent(x = 0.5f, ry = -1f))
    assertEquals(listOf("pad 1 50 0", "pad 2 0 -100"), sent)
  }

  @Test
  fun stickReturningToCenterShouldSendUp() {
    controller.onMotionEvent(stickEvent(x = 0.5f, ry = -1f))
    controller.onMotionEvent(stickEvent())
    assertEquals(listOf("pad 1 50 0", "pad 2 0 -100", "pad 1 up", "pad 2 up"), sent)
  }

  @Test
  fun stickInsideDeadZoneShouldNotMovePad() {
    // Both axes inside the dead zone -> the pad is inactive, so no command at all.
    controller.onMotionEvent(stickEvent(x = 0.05f, y = -0.02f, rx = 0.09f, ry = -0.09f))
    assertTrue(sent.isEmpty())
  }

  @Test
  fun stickUnchangedPositionShouldNotResend() {
    controller.onMotionEvent(stickEvent(x = 0.5f))
    controller.onMotionEvent(stickEvent(x = 0.5f))
    // pad2 never left the dead zone, so only the first pad1 move was sent.
    assertEquals(listOf("pad 1 50 0"), sent)
  }

  @Test
  fun stickMovingOnlyOneAxisShouldResend() {
    controller.onMotionEvent(stickEvent(x = 0.5f))
    controller.onMotionEvent(stickEvent(x = 0.5f, y = 0.5f))
    assertEquals(listOf("pad 1 50 0", "pad 1 50 50"), sent)
  }

  @Test
  fun swapShouldExchangeSticks() {
    swap = true
    controller.onMotionEvent(stickEvent(x = 0.5f, ry = -1f))
    // Pad1 reads the right stick (rx/ry), pad2 reads the left (x/y).
    assertEquals(listOf("pad 1 0 -100", "pad 2 50 0"), sent)
  }

  @Test
  fun magicButtonsShouldSendWithinCount() {
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_A, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_B, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_X, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_Y, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_L1, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_R1, 0))
    assertEquals(
        listOf("btn 1 down", "btn 2 down", "btn 3 down", "btn 4 down", "btn 5 down", "btn 5 down"),
        sent,
    )
  }

  @Test
  fun magicButtonsBeyondCountShouldBeConsumedButSilent() {
    magicCount = 3
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_A, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_B, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_X, 0))
    // Buttons 4 and 5 are beyond the configured count: consumed but silent.
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_Y, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_L1, 0))
    assertTrue(controller.onKeyUp(KeyEvent.KEYCODE_BUTTON_Y))
    assertEquals(listOf("btn 1 down", "btn 2 down", "btn 3 down"), sent)
  }

  @Test
  fun magicButtonRepeatShouldNotResend() {
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_A, 0))
    assertTrue(controller.onKeyDown(KeyEvent.KEYCODE_BUTTON_A, 1))
    assertEquals(listOf("btn 1 down"), sent)
  }

  @Test
  fun unmappedKeyShouldNotBeConsumed() {
    assertFalse(controller.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, 0))
    assertFalse(controller.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP))
  }

  @Test
  fun nonMoveMotionShouldNotBeConsumed() {
    assertFalse(controller.onMotionEvent(stickEvent(action = MotionEvent.ACTION_DOWN)))
  }

  @Test
  fun nonGamepadSourceShouldNotBeConsumed() {
    assertFalse(controller.onMotionEvent(stickEvent(source = InputDevice.SOURCE_MOUSE)))
  }

  @Test
  fun gamepadOnlySourceShouldDriveSticks() {
    // SOURCE_GAMEPAD without SOURCE_JOYSTICK: the source mask check's second
    // condition evaluates to false and the move drives the pads.
    assertTrue(controller.onMotionEvent(stickEvent(x = 0.5f, source = InputDevice.SOURCE_GAMEPAD)))
    assertEquals(listOf("pad 1 50 0"), sent)
  }
}
