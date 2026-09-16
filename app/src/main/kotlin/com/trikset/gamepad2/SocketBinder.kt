package com.trikset.gamepad2

import java.net.Socket

/**
 * Binds a fresh [Socket] to a preferred network before it connects. The default Android route is
 * the "default network", which on a phone with cellular data enabled can be the mobile data network
 * even while the Wi-Fi (the robot's AP) is connected — the robot has no internet, so the system
 * keeps cellular as default. Binding the socket to the Wi-Fi network forces the traffic out over
 * the robot AP (A2).
 *
 * The binding happens before [java.net.Socket.connect]; [bind] receives the raw socket and returns
 * the same socket, network-bound or not. When no suitable network exists, [bind] must leave the
 * socket untouched (default-network fallback) rather than throw.
 */
fun interface SocketBinder {
  fun bind(socket: Socket): Socket

  companion object {
    /** Leaves the socket on the default network (no binding). */
    val identity: SocketBinder = SocketBinder { it }
  }
}
