package com.trikset.gamepad2.diagnostics

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.ConnectionState
import com.trikset.gamepad2.R
import com.trikset.gamepad2.RobolectricTestBase
import com.trikset.gamepad2.SettingsFragment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class CrashReportDialogTest : RobolectricTestBase() {

  private lateinit var activity: Activity
  private lateinit var store: CrashLogStore

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val context = RuntimeEnvironment.getApplication()
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    File(context.filesDir, CrashLogStore.CRASH_DIR).deleteRecursively()
    store = CrashLogStore(context)
  }

  @Test
  fun doesNothingWithoutACrash() {
    CrashReportDialog(activity, store) { null }.showIfNeeded()
    assertNull(ShadowDialog.getLatestDialog())
  }

  @Test
  fun showsDialogWithReviewActionByDefault() {
    // "Review & share" when shareWithoutEditing is explicitly false
    PreferenceManager.getDefaultSharedPreferences(activity)
        .edit()
        .putBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, false)
        .commit()
    store.save("boom")
    CrashReportDialog(activity, store) { ConnectionState.Connected }.showIfNeeded()

    val dialog = ShadowDialog.getLatestDialog() as AlertDialog
    assertNotNull(dialog)
    val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
    assertEquals(activity.getString(R.string.review_and_share), positive.text)
    activity.finish()
  }

  @Test
  fun shareWithoutEditingShowsShareLabel() {
    PreferenceManager.getDefaultSharedPreferences(activity)
        .edit()
        .putBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, true)
        .commit()
    store.save("boom")
    CrashReportDialog(activity, store) { null }.showIfNeeded()

    val dialog = ShadowDialog.getLatestDialog() as AlertDialog
    assertNotNull(dialog)
    assertEquals(
        activity.getString(R.string.share),
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).text,
    )
    activity.finish()
  }

  @Test
  fun copyButtonCopiesTheReport() {
    store.save("boom")
    CrashReportDialog(activity, store) { null }.showIfNeeded()

    val dialog = ShadowDialog.getLatestDialog() as AlertDialog
    dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
    // appcompat's button click posts a ButtonHandler message to the main looper.
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
    assertTrue("the copied report must include the crash trace", text.contains("boom"))
    activity.finish()
  }
}
