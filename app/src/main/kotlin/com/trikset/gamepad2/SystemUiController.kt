package com.trikset.gamepad2

import android.view.View
import android.view.Window
import androidx.appcompat.app.ActionBar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Owns the immersive system-bar visibility and the delayed auto-hide for the gamepad. Extracted
 * from MainActivity so the window/insets plumbing is isolated from the activity lifecycle. The
 * [Window], the main view and the action bar are injected as providers so the controller can be
 * constructed before the view hierarchy exists (fields initialize before `onCreate`'s
 * `setContentView`).
 */
class SystemUiController(
    private val window: Window,
    private val mainViewProvider: () -> View?,
    private val actionBarProvider: () -> ActionBar?,
    private val hideDelayMs: Long,
) {
  private val hideRunnable = Runnable { setVisibility(false) }

  fun setVisibility(show: Boolean) {
    val mainView = mainViewProvider() ?: return
    val controller = WindowCompat.getInsetsController(window, mainView) ?: return
    if (show) {
      controller.show(WindowInsetsCompat.Type.systemBars())
      actionBarProvider()?.show()
    } else {
      controller.hide(WindowInsetsCompat.Type.systemBars())
      controller.setSystemBarsBehavior(
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      )
    }
    mainView.removeCallbacks(hideRunnable)
    mainView.postDelayed(hideRunnable, hideDelayMs)
  }

  /** Cancels the pending auto-hide (called from onDestroy). */
  fun detach() {
    mainViewProvider()?.removeCallbacks(hideRunnable)
  }
}
