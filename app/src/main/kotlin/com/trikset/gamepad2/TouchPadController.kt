package com.trikset.gamepad2

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure touch-coordinate math for the square gamepad pads. Extracted from [SquareTouchPadLayout] so
 * the clamp and sensitivity rules are directly unit-testable. Maps a touch point in pad coordinates
 * (0..[maxX], 0..[maxY]) into the robot command space (-100..100) and applies the hysteresis gate
 * that suppresses tiny movements. The controller owns the previous command (prevX/prevY) so
 * [nextCoordinates] has a short signature.
 */
class TouchPadController {

  /** A normalized pad command in robot coordinates (-100..100). */
  data class Command(val x: Int, val y: Int)

  private var prevX = 0
  private var prevY = 0

  /**
   * Computes the command for a touch point, or null when the point moved less than [SENSITIVITY] on
   * both axes from the previous command (no command should be sent).
   */
  fun nextCoordinates(x: Float, y: Float, maxX: Float, maxY: Float): Command? {
    val rX = (COORDINATE_SCALE * SCALE * (x / maxX - CENTER_OFFSET)).toInt()
    val rY = -(COORDINATE_SCALE * SCALE * (y / maxY - CENTER_OFFSET)).toInt()
    val curX = max(-MAX_COORDINATE, min(rX, MAX_COORDINATE))
    val curY = max(-MAX_COORDINATE, min(rY, MAX_COORDINATE))
    if (abs(curX - prevX) > SENSITIVITY || abs(curY - prevY) > SENSITIVITY) {
      prevX = curX
      prevY = curY
      return Command(curX, curY)
    }
    return null
  }

  private companion object {
    const val SENSITIVITY = 3
    const val SCALE = 1.15
    const val COORDINATE_SCALE = 200.0
    const val CENTER_OFFSET = 0.5
    const val MAX_COORDINATE = 100
  }
}
