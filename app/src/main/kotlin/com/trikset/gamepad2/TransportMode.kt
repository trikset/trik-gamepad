package com.trikset.gamepad2

/** The control-channel transport the robot commands travel over. */
enum class TransportMode {
  /** One persistent TCP stream (the original protocol; write-only, robot never replies). */
  TCP,

  /**
   * One command per UDP datagram (optional protocol extension; robot may respond with keepalive).
   */
  UDP,
}
