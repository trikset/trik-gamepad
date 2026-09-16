package com.trikset.gamepad2.diagnostics

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.R
import com.trikset.gamepad2.RobolectricTestBase
import com.trikset.gamepad2.SettingsFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReportSharerTest : RobolectricTestBase() {

  private lateinit var activity: Activity
  private val reportUri =
      Uri.parse("content://com.trikset.gamepad2.fileprovider/diagnostics/probe.md")

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    // Force shareWithoutEditing off so the edit-path tests are deterministic
    PreferenceManager.getDefaultSharedPreferences(activity)
        .edit()
        .putBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, false)
        .commit()
  }

  private fun startedInnerIntent(): Intent {
    val chooser = shadowOf(activity).nextStartedActivity
    assertNotNull("a chooser must have been started", chooser)
    // The typed getParcelableExtra(String, Class) overload is API 33+; these tests run at
    // minSdk (23), so the deprecated single-arg form is the only one available.
    @Suppress("DEPRECATION")
    return chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
  }

  /** The shared "addressed to support" mail cover asserted by the text-forward and zip tests. */
  private fun assertMailCover(
      inner: Intent,
      expectedStream: Uri,
      subjectPrefix: String,
      expectedHead: String,
  ) {
    // The typed getParcelableExtra(String, Class) overload is API 33+; these tests run at
    // minSdk (23), so the deprecated single-arg form is the only one available.
    @Suppress("DEPRECATION")
    assertEquals(expectedStream, inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    val subject = inner.getStringExtra(Intent.EXTRA_SUBJECT)
    assertNotNull(subject)
    assertTrue(subject!!.startsWith(subjectPrefix))
    assertEquals(
        listOf(ReportShareContent.SUPPORT_EMAIL),
        inner.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList(),
    )
    // The body is the subject line followed by the bounded report head.
    val body = inner.getStringExtra(Intent.EXTRA_TEXT)
    assertTrue("body must lead with the subject", body!!.startsWith(subject + "\n\n"))
    assertTrue("body must carry the report head", body.endsWith(expectedHead))
  }

  @Test
  fun editingPathOpensTextEditorWithFileUri() {
    ReportSharer.share(activity, "report", reportUri, editorAvailable = true)

    val inner = startedInnerIntent()
    assertEquals(Intent.ACTION_EDIT, inner.action)
    assertEquals("text/plain", inner.type)
    assertEquals(reportUri, inner.data)
  }

  @Test
  fun editingChooserTitleGuidesToReview() {
    ReportSharer.share(activity, "report", reportUri, editorAvailable = true)

    val chooser = shadowOf(activity).nextStartedActivity
    assertEquals(
        activity.getString(R.string.report_editor_chooser_title),
        chooser.getStringExtra(Intent.EXTRA_TITLE),
    )
  }

  @Test
  fun directSharePathUsesSendSheetWithAttachment() {
    PreferenceManager.getDefaultSharedPreferences(activity)
        .edit()
        .putBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, true)
        .commit()
    ReportSharer.share(activity, "report", reportUri, editorAvailable = true)

    val inner = startedInnerIntent()
    assertEquals(Intent.ACTION_SEND, inner.action)
    assertEquals("text/plain", inner.type)
    assertMailCover(inner, reportUri, "[trik-gamepad][report] v", "\n\nreport")
  }

  @Test
  fun crashShareUsesCrashSubjectKind() {
    PreferenceManager.getDefaultSharedPreferences(activity)
        .edit()
        .putBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, true)
        .commit()
    ReportSharer.share(activity, "report", reportUri, editorAvailable = true, crash = true)

    val inner = startedInnerIntent()
    assertTrue(inner.getStringExtra(Intent.EXTRA_SUBJECT)!!.startsWith("[trik-gamepad][crash] v"))
  }

  @Test
  fun noEditorFallsBackToShareSheet() {
    ReportSharer.share(activity, "report", reportUri, editorAvailable = false)

    val inner = startedInnerIntent()
    assertEquals(Intent.ACTION_SEND, inner.action)
  }

  @Test
  fun hasEditHandlerWithoutEditorReturnsFalse() {
    // Robolectric resolves no ACTION_EDIT handler for the probe content URI.
    assertFalse(ReportSharer.hasEditHandler(activity))
  }

  @Test
  fun zipForwardUsesZipMimeAndMailCover() {
    val zipUri =
        Uri.parse(
            "content://com.trikset.gamepad2.fileprovider/diagnostics/trik-gamepad-report-1.zip"
        )
    ReportSharer.shareZip(activity, "report head", zipUri)

    val inner = startedInnerIntent()
    assertEquals(Intent.ACTION_SEND, inner.action)
    assertEquals("application/zip", inner.type)
    assertMailCover(inner, zipUri, "[trik-gamepad][report] v", "report head")
  }

  @Test
  fun zipForwardChooserTitleMatchesReportShare() {
    val zipUri =
        Uri.parse(
            "content://com.trikset.gamepad2.fileprovider/diagnostics/trik-gamepad-report-1.zip"
        )
    ReportSharer.shareZip(activity, "report head", zipUri)

    val chooser = shadowOf(activity).nextStartedActivity
    assertEquals(
        activity.getString(R.string.share_report),
        chooser.getStringExtra(Intent.EXTRA_TITLE),
    )
  }
}
