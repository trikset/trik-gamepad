package com.trikset.gamepad2.video

import android.os.Looper
import android.view.TextureView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MediaPlayerVideoPlayerTest {

  private fun newPlayer(): Pair<MediaPlayerVideoPlayer, TextureView> {
    val textureView = TextureView(RuntimeEnvironment.getApplication())
    return MediaPlayerVideoPlayer(textureView) to textureView
  }

  @Test
  fun playWithNullUrlReportsFailureAndStops() {
    val (player, _) = newPlayer()
    var result: Boolean? = null
    player.onPlayResult = { result = it }
    player.play(null)
    shadowOf(Looper.getMainLooper()).idle()
    assertFalse("null URL must report a failed play", result!!)
  }

  @Test
  fun playDeferredUntilSurfaceAvailable() {
    val (player, _) = newPlayer()
    var result: Boolean? = null
    player.onPlayResult = { result = it }
    // No TextureView surface yet (Robolectric: isAvailable=false) -> the URL is pending and no
    // result is reported until onSurfaceTextureAvailable fires.
    player.play("rtsp://192.168.1.1:554/stream")
    assertNull(result)
  }

  @Test
  fun stopAndReleaseWithoutPlayerAreSafe() {
    val (player, _) = newPlayer()
    player.stop()
    player.release()
  }

  @Test
  fun listenersAreStoredAndShowFpsIsNoOp() {
    val (player, _) = newPlayer()
    player.showFps = true
    var errorFired = false
    var frameFired = false
    player.setOnStreamErrorListener { errorFired = true }
    player.setOnFirstFrameListener { frameFired = true }
    assertFalse(player.isPlaying)
    assertFalse(errorFired)
    assertFalse(frameFired)
  }

  @Test
  fun isPlayingIsFalseWhenNoMediaPlayer() {
    val (player, _) = newPlayer()
    assertFalse(player.isPlaying)
  }

  @Test
  fun rtspUrlIsAcceptedAsDataSource() {
    // The factory routes rtsp:// to this player; MediaPlayer supports the scheme natively.
    val (player, _) = newPlayer()
    player.setOnStreamErrorListener {}
    player.play("rtsp://192.168.1.1:554/stream")
    assertTrue(player.isPlaying == false)
  }
}
