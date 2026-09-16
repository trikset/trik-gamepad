package com.trikset.gamepad2

/**
 * Pure wheel-angle math for the accelerometer-driven gamepad wheel. Extracted from [MainActivity]'s
 * `processSensor` so the floor, dead-zone, clamp and hysteresis-step rules are directly
 * unit-testable.
 */
class WheelController {

  /**
   * Computes the next wheel angle for the accelerometer sample, or null when no command should be
   * sent (wheel disabled, x below the acceleration floor, the dead zone zeroes the angle, or the
   * angle moved by less than [step] from [currentAngle]).
   */
  fun nextAngle(x: Float, y: Float, currentAngle: Int, step: Int, enabled: Boolean): Int? {
    if (!enabled || x < MIN_ACCELERATION_X) {
      return null
    }
    var angle =
        (WHEEL_ANGLE_SCALE * WHEEL_BOOSTER_MULTIPLIER * Math.atan2(y.toDouble(), x.toDouble()) /
                Math.PI)
            .toInt()
    if (Math.abs(angle) < ANGLE_DEAD_ZONE) {
      angle = 0
    } else if (angle > ANGLE_CLAMP) {
      angle = ANGLE_CLAMP
    } else if (angle < -ANGLE_CLAMP) {
      angle = -ANGLE_CLAMP
    }
    if (Math.abs(currentAngle - angle) < step) {
      return null
    }
    return angle
  }

  private companion object {
    const val MIN_ACCELERATION_X = 1e-6
    const val WHEEL_ANGLE_SCALE = 200
    const val WHEEL_BOOSTER_MULTIPLIER = 1.5
    const val ANGLE_DEAD_ZONE = 10
    const val ANGLE_CLAMP = 100
  }
}
