package com.trikset.gamepad2

import android.net.Network
import com.trikset.gamepad2.diagnostics.AppLog
import java.io.IOException
import java.net.URL
import java.net.URLConnection
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * [ConnectionOpener] that prefers the Wi-Fi network for opening URLConnections, mirroring
 * [WifiSocketBinder] for sockets: when a `TRANSPORT_WIFI` network is available the connection is
 * opened over it ([Network.openConnection]) so https video traffic goes out over the robot AP even
 * when cellular is the system default network (S13 / A2). Falls back to the default network when no
 * Wi-Fi transport is available or the network-open fails.
 *
 * https connections additionally trust the robot's self-signed camera endpoint. The trust policy is
 * isolated in the companion so it can be swapped later (user decision 2026-08-19: trust-all for
 * now, possibly TOFU later) without touching the routing logic.
 *
 * The network-open and default-open calls are injectable seams so tests can assert the routing
 * decision without a real [Network] (Robolectric's `ShadowNetwork` shadows only `bindSocket`, not
 * `openConnection`). The Wi-Fi lookup is likewise injectable ([wifiNetworkProvider]); the
 * production provider is the process-wide [WifiNetworkTracker] registered once by [App] — never a
 * per-opener tracker (see WifiNetworkTracker).
 */
class WifiConnectionOpener(
    private val wifiNetworkProvider: () -> Network? = { WifiNetworkTracker.instance()?.current() },
    private val networkOpen: (Network, URL) -> URLConnection = { network, url ->
      network.openConnection(url)
    },
    private val defaultOpen: (URL) -> URLConnection = { url -> url.openConnection() },
) : ConnectionOpener {

  override fun open(url: URL): URLConnection {
    val connection =
        try {
          val wifi = wifiNetworkProvider()
          if (wifi != null) networkOpen(wifi, url) else defaultOpen(url)
        } catch (e: IOException) {
          // A network-open failure must not kill the stream attempt: fall back to the default
          // network (the robot AP may have dropped while the callback lagged behind).
          AppLog.e(TAG, "Could not open over the Wi-Fi network; using the default network.", e)
          defaultOpen(url)
        }
    return applyTrustAllTls(connection)
  }

  private fun applyTrustAllTls(connection: URLConnection): URLConnection {
    val https = connection as? HttpsURLConnection ?: return connection
    https.sslSocketFactory = trustAllSslSocketFactory
    https.hostnameVerifier = allowAllHostnameVerifier
    return https
  }

  companion object {
    /** Accepts any certificate, so the robot's self-signed camera endpoint works out of the box. */
    internal val trustAllSslSocketFactory: SSLSocketFactory by lazy {
      SSLContext.getInstance("TLS")
          .apply {
            init(null, arrayOf<TrustManager>(TRUST_ALL_CERTS), null)
          }
          .socketFactory
    }

    /** Accepts any hostname for the same self-signed reason. */
    internal val allowAllHostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }

    private val TRUST_ALL_CERTS =
        object : X509TrustManager {
          override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

          override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}

          override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

    private const val TAG = "ConnectionOpener"
  }
}
