package com.trikset.gamepad2

import android.view.View
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Touch-target regression test: interactive controls on the gamepad screen must meet the 48dp
 * Material/WCAG minimum. The magic buttons are covered by MagicButtonPanelTest
 * (minimumWidth/Height); here we check the fixed-size gear, the tap-to-connect pill and the
 * magic-button row container.
 */
@RunWith(RobolectricTestRunner::class)
class TouchTargetSizeTest : RobolectricTestBase() {

  private fun minTarget(): Int =
      org.robolectric.RuntimeEnvironment.getApplication()
          .resources
          .getDimensionPixelSize(R.dimen.touch_target_min)

  @Test
  fun gearButtonMeets48dpTarget() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val gear = activity.findViewById<View>(R.id.btnSettings)
    assertNotNull(gear)
    // The gear is a wrap-content Button whose circle is sized by minHeight (48dp touch target);
    // assert the declared minimum (layoutParams stays WRAP_CONTENT for wrap_content).
    assertTrue("gear minHeight must be at least 48dp", gear!!.minimumHeight >= minTarget())
  }

  @Test
  fun statusPillMeets48dpTarget() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val pill = activity.findViewById<View>(R.id.connectionStatus)
    assertNotNull(pill)
    pill.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    assertTrue("status pill must be at least 48dp tall", pill.measuredHeight >= minTarget())
  }

  @Test
  fun magicButtonRowMeets48dpTarget() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val row = activity.findViewById<View>(R.id.buttons)
    assertNotNull(row)
    assertTrue("magic button row must be at least 48dp tall", row!!.minimumHeight >= minTarget())
  }
}
