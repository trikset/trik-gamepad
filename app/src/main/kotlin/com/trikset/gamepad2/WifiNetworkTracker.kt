package com.trikset.gamepad2

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.trikset.gamepad2.diagnostics.AppLog

/**
 * Tracks the currently available `TRANSPORT_WIFI` network by registering ONE
 * [ConnectivityManager.NetworkCallback] for the whole process. The instance is created once by
 * [App] ([initialize]) and read by [WifiSocketBinder], [WifiDatagramBinder] and
 * [WifiConnectionOpener] via [instance] — never constructed per binder/player, because Android
 * enforces a per-app cap on active network callbacks and every extra `registerNetworkCallback` eats
 * that budget. Unbounded accumulation ends in `ConnectivityManager$TooManyRequestsException`, a
 * `RuntimeException` the connect path does not catch (seen in the field 2026-09-05: repeated
 * connect attempts each built a fresh `WifiNetworkTracker`, and every `MjpegVideoPlayer` built two
 * more).
 *
 * A connect that happens before the first [ConnectivityManager.NetworkCallback.onAvailable] falls
 * back to the default network; the next reconnect then binds to Wi-Fi. Requires
 * `ACCESS_NETWORK_STATE`.
 */
class WifiNetworkTracker private constructor(context: Context) {
  private val appContext: Context = context.applicationContext
  private val connectivityManager =
      appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  @Volatile private var wifiNetwork: Network? = null

  init {
    // A registration failure (callback budget already exhausted elsewhere, or a missing
    // ACCESS_NETWORK_STATE) must NOT crash the caller: it degrades to default-network routing
    // ([current] stays null) instead of surfacing on the connect/video paths.
    try {
      val request =
          NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
      connectivityManager.registerNetworkCallback(
          request,
          object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
              wifiNetwork = network
            }

            override fun onLost(network: Network) {
              if (wifiNetwork == network) wifiNetwork = null
            }
          },
      )
      // Catch is deliberately broad: ConnectivityManager surfaces a missing ACCESS_NETWORK_STATE as
      // SecurityException and its exhausted per-app callback budget as a RuntimeException subtype
      // (TooManyRequestsException). The hardened path must never crash for either.
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
      AppLog.e(TAG, "Wi-Fi network callback registration failed; using the default network.", e)
    }
  }

  fun current(): Network? = wifiNetwork

  /** True when [context] resolves to the same application this tracker was created for. */
  private fun belongsTo(context: Context): Boolean = appContext === context.applicationContext

  companion object {
    @Volatile private var shared: WifiNetworkTracker? = null

    /**
     * Creates (once per application context) the shared tracker; called from [App.onCreate].
     * Idempotent. The instance is keyed to the application context identity so a process never
     * registers a second callback, while Robolectric (which boots a fresh Application per test
     * method) gets a tracker wired to the current test's ConnectivityManager.
     */
    fun initialize(context: Context): WifiNetworkTracker {
      val existing = shared
      if (existing != null && existing.belongsTo(context)) return existing
      return synchronized(this) {
        val again = shared
        if (again != null && again.belongsTo(context)) again
        else WifiNetworkTracker(context).also { shared = it }
      }
    }

    /** The process-wide tracker, or null before [App] initialized it. */
    fun instance(): WifiNetworkTracker? = shared

    /**
     * Test-only: drops the cached instance so a test can exercise the not-yet-initialized path (a
     * Robolectric test boots a fresh Application per method, whose App.onCreate re-initializes it).
     */
    internal fun resetForTest() {
      shared = null
    }

    private const val TAG = "WifiNetworkTracker"
  }
}
