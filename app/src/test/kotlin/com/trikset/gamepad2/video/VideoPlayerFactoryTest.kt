package com.trikset.gamepad2.video

import android.view.TextureView
import com.trikset.gamepad2.mjpeg.MjpegView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VideoPlayerFactoryTest {

  private val mjpegView = MjpegView(RuntimeEnvironment.getApplication())
  private val textureView = TextureView(RuntimeEnvironment.getApplication())

  @Test
  fun createRoutesByUrlScheme() {
    data class Case(val url: String?, val expectedType: Class<*>)
    val cases =
        listOf(
            Case("http://192.168.1.1:8080/stream", MjpegVideoPlayer::class.java),
            Case("https://192.168.1.1:8443/stream", MjpegVideoPlayer::class.java),
            Case("rtsp://192.168.1.1:554/stream", MediaPlayerVideoPlayer::class.java),
            Case(null, MjpegVideoPlayer::class.java),
        )
    for (case in cases) {
      val player = VideoPlayerFactory.create(case.url, mjpegView, textureView)
      assertTrue(
          "$case.url -> ${case.expectedType.simpleName}",
          case.expectedType.isInstance(player),
      )
    }
  }
}
