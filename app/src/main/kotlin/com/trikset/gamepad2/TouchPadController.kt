package com.trikset.gamepad2

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure touch-coordinate math for the square gamepad pads. Maps a touch point into the robot command
 * space (-100..100) and applies the hysteresis gate that suppresses tiny movements.
 *
 * Two independent state pairs are tracked: [knobX/knobY] is the *current* finger position (updated on
 * every touch event); [sentX/sentY] is the *last transmitted* position (updated only when
 * [pollSend] returns a command). This split decouples the visual knob rendering (which consumes
 * every frame) from the network send rate (which a timer throttles to the configured interval).
 *
 * Usage:
 * - Every touch move → [setPosition] (stores the normalised coords, no side-effect).
 * - When ready to send → [pollSend] (checks hysteresis against the last-sent position).
 * - On finger lift → [onUp] (resets both state pairs).
 */
class TouchPadController {

  /** A normalized pad command in robot coordinates (-100..100). */
  data class Command(val x: Int, val y: Int)

  /** Knob position — updated on every touch event via [setPosition]. */
  private var knobX = 0
  private var knobY = 0

  /** Last sent position — updated only when [pollSend] returns a non-null command. */
  private var sentX = 0
  private var sentY = 0

  /**
   * Normalises a touch point into robot coordinates (-100..100) and stores it as the current knob
   * position. Does NOT send anything — call [pollSend] to check the hysteresis gate.
   */
  fun setPosition(x: Float, y: Float, maxX: Float, maxY: Float) {
    val rX = (COORDINATE_SCALE * SCALE * (x / maxX - CENTER_OFFSET)).toInt()
    val rY = -(COORDINATE_SCALE * SCALE * (y / maxY - CENTER_OFFSET)).toInt()
    knobX = max(-MAX_COORDINATE, min(rX, MAX_COORDINATE))
    knobY = max(-MAX_COORDINATE, min(rY, MAX_COORDINATE))
  }

  /**
   * Returns a [Command] when the knob moved more than [SENSITIVITY] units from the last *sent*
   * position; returns null to skip sending. Updates the last-sent state only on a non-null return.
   */
  fun pollSend(): Command? {
    if (abs(knobX - sentX) > SENSITIVITY || abs(knobY - sentY) > SENSITIVITY) {
      sentX = knobX
      sentY = knobY
      return Command(knobX, knobY)
    }
    return null
  }

  /** Resets both the knob and sent state to centre. Call on finger lift. */
  fun onUp() {
    sentX = 0
    sentY = 0
    knobX = 0
    knobY = 0
  }

  private companion object {
    const val SENSITIVITY = 3
    const val SCALE = 1.15
    const val COORDINATE_SCALE = 200.0
    const val CENTER_OFFSET = 0.5
    const val MAX_COORDINATE = 100
  }
}
