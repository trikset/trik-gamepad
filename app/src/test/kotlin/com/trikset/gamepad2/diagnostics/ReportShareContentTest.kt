package com.trikset.gamepad2.diagnostics

import android.os.Build
import com.trikset.gamepad2.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReportShareContentTest {

  private val reportWithBoundary =
      "# TRIK Gamepad diagnostic report\n\n" +
          "## App\n- Version: 1.0\n\n" +
          "## Connection\n- State: Connected\n\n" +
          "## Settings\n- Robot IP address: 1.2.3.4\n"

  @Test
  fun subjectIsAMachineReadableTagWithFixedTimestamp() {
    val subject = ReportShareContent.subject(crash = false, nowMs = 1_752_700_000_000L)
    assertTrue(subject.startsWith("[trik-gamepad][report] v"))
    assertTrue(subject.contains(BuildConfig.VERSION_NAME))
    assertTrue(subject.contains("API ${Build.VERSION.SDK_INT}"))
    assertTrue(subject.contains(Build.MODEL))
    assertTrue(subject.endsWith("2025-07-16T21:06:40Z"))
  }

  @Test
  fun crashKindIsTaggedInTheSubject() {
    assertTrue(ReportShareContent.subject(crash = true).startsWith("[trik-gamepad][crash] v"))
    assertTrue(ReportShareContent.subject(crash = false).startsWith("[trik-gamepad][report] v"))
  }

  @Test
  fun headSnippetStopsBeforeTheSettingsSection() {
    val head = ReportShareContent.headSnippet(reportWithBoundary)
    assertTrue(head.contains("## Connection"))
    assertFalse(head.contains("## Settings"))
    assertFalse(head.contains("Robot IP address"))
  }

  @Test
  fun headSnippetWithoutBoundaryReturnsWholeText() {
    assertEquals("plain report", ReportShareContent.headSnippet("plain report"))
  }

  @Test
  fun messageBodyLeadsWithSubjectThenHead() {
    val body = ReportShareContent.messageBody(reportWithBoundary, crash = false)
    val subject = ReportShareContent.subject(crash = false)
    assertTrue(body.startsWith(subject + "\n\n# TRIK Gamepad diagnostic report"))
    assertFalse(body.contains("## Settings"))
  }
}
