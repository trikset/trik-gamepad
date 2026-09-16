package com.trikset.gamepad2

import com.trikset.gamepad2.mjpeg.HttpsMjpegServer
import com.trikset.gamepad2.mjpeg.MjpegView
import com.trikset.gamepad2.mjpeg.SyntheticMjpegServer
import com.trikset.gamepad2.mjpeg.assertDecodedFramesMatchSeeded
import com.trikset.gamepad2.video.MjpegVideoPlayer
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * E3 end-to-end over the wire: the https branch of [MjpegVideoPlayer] must open the camera stream
 * through `WifiConnectionOpener` (which trust-alls the self-signed endpoint when no Wi-Fi network
 * is present) and deliver real, byte-identical frames — the whole point of the feature rather than
 * the default-network-only `HttpURLConnection` the raw-socket client never touched.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HttpsVideoStreamTest : RobolectricTestBase() {

  private val seededFrames = SyntheticMjpegServer.catFrameImages()

  @Test
  fun openStreamReadsFramesFromSelfSignedHttpsServer() {
    val server = HttpsMjpegServer(frameImages = seededFrames)
    try {
      val port = server.start()
      val context = RuntimeEnvironment.getApplication()
      val player =
          MjpegVideoPlayer(
              MjpegView(context),
              connectionOpener = WifiConnectionOpener(wifiNetworkProvider = { null }),
          )
      val stream = player.openStream("https://127.0.0.1:$port/?action=stream")
      assertNotNull("the self-signed https stream must open (trust-all)", stream)
      val opened = requireNotNull(stream)
      try {
        assertDecodedFramesMatchSeeded(opened, seededFrames)
      } finally {
        opened.close()
      }
    } finally {
      server.close()
    }
  }
}
