package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure math tests for [TouchPadController] using the setPosition + pollSend API. */
class TouchPadControllerTest {

  private fun controller(): TouchPadController = TouchPadController()

  @Test
  fun setPositionAndPollSendShouldMapAndClamp() {
    data class Case(val x: Float, val y: Float, val expected: TouchPadController.Command?)
    val c = controller()

    // Centre → delta from sent(0,0) is (0,0) → no command
    c.setPosition(100f, 100f, 200f, 200f)
    assertNull(c.pollSend())

    // Right edge → clamped to 100
    c.setPosition(200f, 100f, 200f, 200f)
    assertEquals(TouchPadController.Command(100, 0), c.pollSend())

    // Left edge → clamped to -100
    c.setPosition(0f, 100f, 200f, 200f)
    assertEquals(TouchPadController.Command(-100, 0), c.pollSend())

    // Top edge → clamped to 100
    c.setPosition(100f, 0f, 200f, 200f)
    assertEquals(TouchPadController.Command(0, 100), c.pollSend())

    // Bottom edge → clamped to -100
    c.setPosition(100f, 200f, 200f, 200f)
    assertEquals(TouchPadController.Command(0, -100), c.pollSend())
  }

  @Test
  fun pollSendShouldReturnNullWhenKnobHasNotMoved() {
    val c = controller()
    c.setPosition(50f, 150f, 200f, 200f)
    assertEquals(TouchPadController.Command(-57, -57), c.pollSend())

    // Same call again → no delta from sent → null
    c.setPosition(50f, 150f, 200f, 200f)
    assertNull(c.pollSend())
  }

  @Test
  fun smallYMovedBeyondSensitivityShouldSendEvenWhenXIsWithin() {
    val c = controller()
    c.setPosition(100f, 100f, 200f, 200f) // centre → (0, 0), null
    assertNull(c.pollSend())
    // sent is (0, 0), knob moves to (0, -100) → Y delta 100 > 3 → send
    c.setPosition(100f, 200f, 200f, 200f)
    assertEquals(TouchPadController.Command(0, -100), c.pollSend())
  }

  @Test
  fun moveWithinSensitivityShouldBeSuppressed() {
    val c = controller()
    // First move: centre to right edge → (100, 0), sent
    c.setPosition(200f, 100f, 200f, 200f)
    assertEquals(TouchPadController.Command(100, 0), c.pollSend())
    // sent is (100, 0). Same position again → null
    c.setPosition(200f, 100f, 200f, 200f)
    assertNull(c.pollSend())
    // Move to a position that produces coordinates within 3 units of (100, 0):
    // (185, 103) → knobX=97 (delta 3, not >3), knobY=-3 (delta 3, not >3) → null
    c.setPosition(185f, 103f, 200f, 200f)
    assertNull(c.pollSend())
  }

  @Test
  fun onUpShouldResetBothKnobAndSentState() {
    val c = controller()
    c.setPosition(200f, 0f, 200f, 200f) // right edge → (100, 100)
    assertEquals(TouchPadController.Command(100, 100), c.pollSend())
    c.onUp()
    // After onUp, knob and sent are both (0,0); next poll returns null
    assertNull(c.pollSend())
    // Now a new touch at same right edge should send again
    c.setPosition(200f, 0f, 200f, 200f)
    assertEquals(TouchPadController.Command(100, 100), c.pollSend())
  }
}
