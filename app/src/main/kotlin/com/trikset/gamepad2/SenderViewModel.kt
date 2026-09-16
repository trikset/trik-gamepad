package com.trikset.gamepad2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Activity-scoped owner of the [SenderService]. Survives configuration changes so the connection
 * and keepalive timer are not torn down and rebuilt on rotation, and closes the socket in
 * [onCleared] instead of relying on the Activity's onDestroy. Settings already live in
 * SharedPreferences, so a process death re-derives the target for free. No DI framework: the
 * service is created here and injected into the views by [MainActivity].
 *
 * One [WifiSocketBinder]/[WifiDatagramBinder] per transport mode, created lazily and reused for the
 * ViewModel's whole lifetime. Binders are stateless wrappers over the process-wide
 * [WifiNetworkTracker], so building a fresh one per connect attempt was pure waste — and it is what
 * used to register a fresh network callback every attempt, accumulating until Android's per-app
 * callback cap threw (see WifiNetworkTracker).
 */
class SenderViewModel(application: Application) : AndroidViewModel(application) {

  private val tcpSocketBinder by lazy { WifiSocketBinder() }
  private val udpDatagramBinder by lazy { WifiDatagramBinder() }

  var sender: SenderService =
      SenderService(
          transportFactory = { mode ->
            when (mode) {
              TransportMode.TCP -> TcpTransport(socketBinder = tcpSocketBinder)
              TransportMode.UDP -> UdpTransport(datagramBinder = udpDatagramBinder)
            }
          }
      )
    internal set

  val connectionState: StateFlow<ConnectionState> = sender.connectionState

  override fun onCleared() {
    sender.disconnect("ViewModel cleared")
    super.onCleared()
  }
}
