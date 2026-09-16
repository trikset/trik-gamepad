package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStatusIndicatorTest {

  private val indicator = VideoStatusIndicator()

  @Test
  fun colorResourceMapsEachStatus() {
    data class Case(val status: VideoStatus, val expected: Int)
    val cases =
        listOf(
            Case(VideoStatus.PLAYING, R.color.hud_accent_connected),
            Case(VideoStatus.LOADING, R.color.hud_accent_connecting),
            Case(VideoStatus.RECONNECTING, R.color.hud_accent_connecting),
            Case(VideoStatus.UNAVAILABLE, R.color.hud_accent_error),
            Case(VideoStatus.DISABLED, R.color.hud_disabled),
        )
    for (case in cases) {
      assertEquals("${case.status}", case.expected, indicator.colorResource(case.status))
    }
  }
}
