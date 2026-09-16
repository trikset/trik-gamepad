package com.trikset.gamepad2

import android.app.Activity
import android.view.View
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Direct tests for [SystemUiController]. */
@RunWith(RobolectricTestRunner::class)
class SystemUiControllerTest : RobolectricTestBase() {

  private lateinit var activity: Activity
  private lateinit var mainView: View
  private lateinit var controller: SystemUiController

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    mainView = View(activity)
    controller =
        SystemUiController(
            activity.window,
            mainViewProvider = { mainView },
            actionBarProvider = { null },
            hideDelayMs = 3000,
        )
  }

  @Test
  fun setVisibilityHideShouldNotThrow() {
    controller.setVisibility(false)
    assertNotNull(mainView)
  }

  @Test
  fun setVisibilityShowShouldNotThrow() {
    controller.setVisibility(true)
    assertNotNull(mainView)
  }

  @Test
  fun setVisibilityWithNullMainViewShouldBeSafe() {
    val noView =
        SystemUiController(
            activity.window,
            mainViewProvider = { null },
            actionBarProvider = { null },
            hideDelayMs = 3000,
        )
    noView.setVisibility(false)
    noView.setVisibility(true)
    noView.detach()
  }

  @Test
  fun detachShouldBeSafe() {
    controller.detach()
  }
}
