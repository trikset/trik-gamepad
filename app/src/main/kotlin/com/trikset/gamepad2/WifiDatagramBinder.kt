package com.trikset.gamepad2

import android.annotation.SuppressLint
import android.net.Network
import android.os.Build
import com.trikset.gamepad2.diagnostics.AppLog
import java.io.IOException
import java.net.DatagramSocket

/**
 * [DatagramBinder] that prefers the Wi-Fi network (the UDP twin of [WifiSocketBinder]): when a
 * `TRANSPORT_WIFI` network is available, [bind] calls [Network.bindSocket] so the datagram socket's
 * traffic goes out over the robot AP even when cellular is the system default network (S13 / A2).
 * Falls back to the default network when no Wi-Fi transport is available. Requires
 * `ACCESS_NETWORK_STATE`. The Wi-Fi lookup is injectable ([wifiNetworkProvider]); the production
 * provider is the process-wide [WifiNetworkTracker] registered once by [App] — never a per-binder
 * tracker (see WifiNetworkTracker).
 */
class WifiDatagramBinder(
    private val wifiNetworkProvider: () -> Network? = { WifiNetworkTracker.instance()?.current() },
    private val isSdkAtLeast22: () -> Boolean = { Build.VERSION.SDK_INT >= API_22 },
) : DatagramBinder {

  @SuppressLint("NewApi") // guarded by runtime SDK check (isSdkAtLeast22)
  override fun bind(socket: DatagramSocket): DatagramSocket {
    val wifi = wifiNetworkProvider()
    if (wifi != null && isSdkAtLeast22()) {
      try {
        wifi.bindSocket(socket)
      } catch (e: IOException) {
        // A bind failure must not kill the send path: fall back to the default network.
        AppLog.e(TAG, "Could not bind datagram socket to the Wi-Fi network; using the default.", e)
      }
    }
    return socket
  }

  private companion object {
    const val TAG = "UdpBinder"
    const val API_22 = 22
  }
}
