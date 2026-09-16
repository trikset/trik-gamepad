package com.trikset.gamepad2

import java.net.DatagramSocket

/**
 * Binds a fresh [DatagramSocket] to a preferred network before it sends. The UDP twin of
 * [SocketBinder]: `Network.bindSocket(DatagramSocket)` binds the datagram socket to the Wi-Fi
 * network so UDP control traffic routes over the robot AP even when cellular is the system default
 * network (S13 / A2). When no suitable network exists, [bind] must leave the socket untouched
 * (default-network fallback) rather than throw.
 */
fun interface DatagramBinder {
  fun bind(socket: DatagramSocket): DatagramSocket

  companion object {
    /** Leaves the socket on the default network (no binding). */
    val identity: DatagramBinder = DatagramBinder { it }
  }
}
