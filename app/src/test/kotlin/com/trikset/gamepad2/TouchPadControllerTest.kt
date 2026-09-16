package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure math tests for [TouchPadController]. */
class TouchPadControllerTest {

  private fun controller(): TouchPadController = TouchPadController()

  @Test
  fun nextCoordinatesShouldMapAndClamp() {
    data class Case(val x: Float, val y: Float, val expected: TouchPadController.Command?)

    val cases =
        listOf(
            Case(100f, 100f, null), // center maps to (0,0) -> no movement -> no command
            Case(200f, 100f, TouchPadController.Command(100, 0)), // right edge -> clamped to 100
            Case(0f, 100f, TouchPadController.Command(-100, 0)), // left edge -> clamped to -100
            Case(100f, 0f, TouchPadController.Command(0, 100)), // top edge -> clamped to 100
            Case(100f, 200f, TouchPadController.Command(0, -100)), // bottom edge -> clamped to -100
        )
    for (case in cases) {
      val actual = controller().nextCoordinates(case.x, case.y, 200f, 200f)
      if (case.expected == null) {
        assertNull("case $case", actual)
      } else {
        assertEquals("case $case", case.expected, actual)
      }
    }
  }

  @Test
  fun smallYMoveBeyondSensitivityShouldSendEvenWhenXIsWithin() {
    // After a previous command of (0,0), curX stays 0 but curY moves to -100:
    // first operand false, second true -> the || still sends.
    val c = controller()
    assertNull(c.nextCoordinates(100f, 100f, 200f, 200f))
    assertEquals(
        TouchPadController.Command(0, -100),
        c.nextCoordinates(100f, 200f, 200f, 200f),
    )
  }

  @Test
  fun moveWithinSensitivityShouldBeSuppressed() {
    // After a command of (100, 0), a tiny move still computes (100, 0) -> the
    // sensitivity gate suppresses a repeat.
    val c = controller()
    assertEquals(
        TouchPadController.Command(100, 0),
        c.nextCoordinates(200f, 100f, 200f, 200f),
    )
    assertNull(c.nextCoordinates(190f, 100f, 200f, 200f))
  }
}
