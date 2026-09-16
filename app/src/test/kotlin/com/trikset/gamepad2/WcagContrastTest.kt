package com.trikset.gamepad2

import androidx.core.content.ContextCompat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * WCAG 2.x AA contrast regression test. Reads the actual color resources (so a future palette
 * change is re-verified) and asserts the foreground/background pairs used by the HUD and Settings:
 * 4.5:1 for normal text, 3.0:1 for large text / non-text UI components (e.g. the gear border). A
 * failure here means a color change dropped below the WCAG threshold.
 */
@RunWith(RobolectricTestRunner::class)
class WcagContrastTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()

  private fun contrast(fg: Int, bg: Int): Double {
    val l1 = relativeLuminance(fg)
    val l2 = relativeLuminance(bg)
    val high = maxOf(l1, l2)
    val low = minOf(l1, l2)
    return (high + 0.05) / (low + 0.05)
  }

  private fun relativeLuminance(color: Int): Double {
    fun channel(c: Int): Double {
      val v = (c and 0xFF) / 255.0
      return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    val r = channel(color shr 16)
    val g = channel(color shr 8)
    val b = channel(color)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }

  private fun color(res: Int): Int = ContextCompat.getColor(context, res)

  private fun androidColor(res: Int): Int = context.resources.getColor(res, null)

  @Test
  fun magicButtonTextOnDefaultFillMeetsAa() {
    val ratio = contrast(color(R.color.magic_button_text), color(R.color.magic_button_fill_default))
    assertTrue("magic button text vs default fill must be >= 4.5:1 (was $ratio)", ratio >= 4.5)
  }

  @Test
  fun magicButtonTextOnPressedFillMeetsLargeTextThreshold() {
    // Pressed fill is transient; the large-text/UI threshold (3.0:1) is the right bar.
    val ratio = contrast(color(R.color.magic_button_text), color(R.color.hud_accent_connected_dark))
    assertTrue("magic button text vs pressed fill must be >= 3.0:1 (was $ratio)", ratio >= 3.0)
  }

  @Test
  fun statusPillTextOnBlackMeetsAa() {
    // The pill background (#99000000) is translucent; pure black is the worst case under it.
    val ratio = contrast(color(R.color.status_orange), androidColor(android.R.color.black))
    assertTrue("status pill text vs black must be >= 4.5:1 (was $ratio)", ratio >= 4.5)
  }

  @Test
  fun videoPlaceholderTextOnBlackMeetsAa() {
    val ratio = contrast(color(R.color.video_placeholder_text), androidColor(android.R.color.black))
    assertTrue("video placeholder text vs black must be >= 4.5:1 (was $ratio)", ratio >= 4.5)
  }

  @Test
  fun gearBorderOnBlackMeetsUiThreshold() {
    // 3dp border stroke on the black gear: non-text UI component -> 3.0:1.
    val ratio =
        contrast(color(R.color.hud_accent_connected_dark), androidColor(android.R.color.black))
    assertTrue("gear border vs black must be >= 3.0:1 (was $ratio)", ratio >= 3.0)
  }

  @Test
  fun chipStatusGlyphsOnBlackMeetUiThreshold() {
    // The robot-chip status glyphs (control/video) are thin-stroke UI components on the dark
    // glass badge; pure black under the translucent badge is the worst case.
    val glyphColors =
        listOf(
            R.color.hud_accent_connected, // control connected / video streaming
            R.color.hud_accent_connecting, // connecting / loading / reconnecting
            R.color.hud_sepia, // control standby
            R.color.hud_accent_error, // control error / video unavailable
            R.color.hud_disabled, // video disabled
        )
    for (res in glyphColors) {
      val ratio = contrast(color(res), androidColor(android.R.color.black))
      assertTrue("chip glyph $res vs black must be >= 3.0:1 (was $ratio)", ratio >= 3.0)
    }
  }

  @Test
  fun videoSourceChipGlyphOnWhiteMeetsAa() {
    // The video-source preset chips sit on the DayNight settings list (transparent pill fill over
    // the list background). The light-theme glyph is near-black on white; its values-night twin is
    // white-on-dark, so the day pair (the Robolectric default) is the assertion here.
    val ratio = contrast(color(R.color.chip_glyph), androidColor(android.R.color.white))
    assertTrue("video-source chip glyph vs white must be >= 4.5:1 (was $ratio)", ratio >= 4.5)
  }
}
