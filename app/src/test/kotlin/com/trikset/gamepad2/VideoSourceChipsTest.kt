package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct tests for the video-source preset chips' pure URI computation ([VideoSourceChips]). */
@RunWith(RobolectricTestRunner::class)
class VideoSourceChipsTest : RobolectricTestBase() {

  @Test
  fun defaultStreamUriBuildsHostDerivedStream() {
    assertEquals(
        "http://192.168.1.5:8080/?action=stream",
        VideoSourceChips.defaultStreamUri("192.168.1.5", 8080),
    )
  }

  @Test
  fun targetForNoneAlwaysClearsStoredUri() {
    assertEquals(
        "",
        VideoSourceChips.targetFor(
            "http://192.168.1.5:8080/?action=stream",
            "1.1.1.1",
            VideoSourceChip.NONE,
        ),
    )
    assertEquals("", VideoSourceChips.targetFor("", "", VideoSourceChip.NONE))
  }

  @Test
  fun targetForFillsFromHostWhenStoredEmpty() {
    assertEquals(
        "http://192.168.1.5:8081/?action=stream",
        VideoSourceChips.targetFor("", "192.168.1.5", VideoSourceChip.CAMERA_2),
    )
    // An explicitly-unset (null) stored URI behaves the same as an empty one.
    assertEquals(
        "http://192.168.1.5:8081/?action=stream",
        VideoSourceChips.targetFor(null, "192.168.1.5", VideoSourceChip.CAMERA_2),
    )
  }

  @Test
  fun targetForReturnsNullWhenStoredEmptyAndNoHost() {
    assertNull(VideoSourceChips.targetFor("", "", VideoSourceChip.CAMERA_2))
    assertNull(VideoSourceChips.targetFor("", "  ", VideoSourceChip.CAMERA_1))
  }

  @Test
  fun targetForRewritesOnlyThePortOfStoredUri() {
    assertEquals(
        "http://192.168.1.5:8080/stream?low=1",
        VideoSourceChips.targetFor(
            "http://192.168.1.5:8081/stream?low=1",
            "10.0.0.9",
            VideoSourceChip.CAMERA_1,
        ),
    )
    assertEquals(
        "http://192.168.1.5:8082/?action=stream",
        VideoSourceChips.targetFor(
            "http://192.168.1.5:8080/?action=stream",
            "10.0.0.9",
            VideoSourceChip.USB,
        ),
    )
  }

  @Test
  fun targetForKeepsUserInfoWhenRewritingPort() {
    assertEquals(
        "rtsp://user:pass@192.168.1.5:8080/stream",
        VideoSourceChips.targetFor(
            "rtsp://user:pass@192.168.1.5:8554/stream",
            "10.0.0.9",
            VideoSourceChip.CAMERA_1,
        ),
    )
  }

  @Test
  fun targetForFallsBackToHostWhenStoredUriHasNoHost() {
    // A stored URI without a parseable host can't be port-rewritten; fall back to the host build.
    assertEquals(
        "http://192.168.1.5:8080/?action=stream",
        VideoSourceChips.targetFor("/just/a/path", "192.168.1.5", VideoSourceChip.CAMERA_1),
    )
  }

  @Test
  fun targetForReturnsNullWhenStoredUriUnparseableAndNoHost() {
    assertNull(VideoSourceChips.targetFor("::::not a uri", "", VideoSourceChip.CAMERA_1))
  }

  @Test
  fun chipsExposeTheirPortsAndGlyphs() {
    assertEquals(8080, VideoSourceChip.CAMERA_1.port)
    assertEquals(8081, VideoSourceChip.CAMERA_2.port)
    assertEquals(8082, VideoSourceChip.USB.port)
    assertNull(VideoSourceChip.NONE.port)
    // Glyphs must be non-blank (each chip renders something in the glyph row).
    for (chip in VideoSourceChip.entries) {
      assertTrue("chip ${chip.name} must carry a glyph", chip.glyph.isNotBlank())
    }
  }
}
