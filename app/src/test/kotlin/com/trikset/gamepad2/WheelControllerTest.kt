package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WheelControllerTest {

  private val controller = WheelController()

  @Test
  fun nextAngleShouldMapClampAndGate() {
    data class Case(
        val x: Float,
        val y: Float,
        val currentAngle: Int = 0,
        val step: Int = 7,
        val enabled: Boolean = true,
        val expected: Int?,
    )

    val cases =
        listOf(
            Case(x = 0.7f, y = 0.7f, enabled = false, expected = null),
            Case(x = 0.0000001f, y = 0.7f, expected = null),
            Case(x = 1f, y = 0f, expected = null),
            Case(x = 0.7f, y = 0.7f, expected = 75),
            Case(x = 1f, y = 0.01f, expected = null),
            Case(x = 1f, y = 100f, expected = 100),
            Case(x = 1f, y = -2f, expected = -100),
            Case(x = 0.7f, y = 0.7f, currentAngle = 65, expected = 75),
            Case(x = 0.7f, y = 0.7f, currentAngle = 72, expected = null),
        )
    for (case in cases) {
      val actual = controller.nextAngle(case.x, case.y, case.currentAngle, case.step, case.enabled)
      if (case.expected == null) {
        assertNull("case $case", actual)
      } else {
        assertEquals("case $case", case.expected, actual)
      }
    }
  }
}
