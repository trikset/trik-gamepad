package com.trikset.gamepad2

import java.net.URL
import java.net.URLConnection

/**
 * Opens a [URLConnection] for a URL, analogous to [SocketBinder] for sockets. The default Android
 * route is the "default network", which on a phone with cellular data enabled can be the mobile
 * data network even while the Wi-Fi (the robot's AP) is connected — so the https video path must
 * open its connection over the Wi-Fi network the same way the raw-socket http path binds its socket
 * (S13 / A2).
 */
fun interface ConnectionOpener {
  fun open(url: URL): URLConnection

  companion object {
    /** Opens on the default network (no network preference). */
    val identity: ConnectionOpener = ConnectionOpener { it.openConnection() }
  }
}
