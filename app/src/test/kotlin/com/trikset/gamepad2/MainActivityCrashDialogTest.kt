package com.trikset.gamepad2

import androidx.preference.PreferenceManager
import com.trikset.gamepad2.diagnostics.CrashLogStore
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class MainActivityCrashDialogTest : RobolectricTestBase() {

  @Before
  fun cleanState() {
    val context = RuntimeEnvironment.getApplication()
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    File(context.filesDir, CrashLogStore.CRASH_DIR).deleteRecursively()
  }

  @Test
  fun crashDialogIsShownWhenACrashWasCaptured() {
    CrashLogStore(RuntimeEnvironment.getApplication()).save("java.lang.RuntimeException: boom")
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    // The dialog is posted after the first frame, so let the main looper run it.
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    assertNotNull(
        "the crash dialog must appear after a captured crash",
        ShadowDialog.getLatestDialog(),
    )
    activity.finish()
  }

  @Test
  fun noDialogWithoutACrash() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    assertNull("no crash -> no dialog", ShadowDialog.getLatestDialog())
    activity.finish()
  }
}
