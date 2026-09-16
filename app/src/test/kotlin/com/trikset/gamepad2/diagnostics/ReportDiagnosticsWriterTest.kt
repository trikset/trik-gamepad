package com.trikset.gamepad2.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReportDiagnosticsWriterTest {

  @Test
  fun writeStoresReportAsMarkdownInCacheDiagnosticsDir() {
    val context = RuntimeEnvironment.getApplication()
    val file = ReportDiagnosticsWriter.write(context, "hello report")

    assertTrue(file.name.endsWith(".md"))
    assertNotNull("written report must have a parent dir", file.parentFile)
    assertEquals("diagnostics", file.parentFile!!.name)
    assertEquals(context.cacheDir.absolutePath, file.parentFile!!.parentFile?.absolutePath)
    assertTrue(file.exists())
    assertEquals("hello report", file.readText())
  }

  @Test
  fun fileProviderAuthorityUsesApplicationIdSuffix() {
    val context = RuntimeEnvironment.getApplication()
    assertEquals(
        context.packageName + ".fileprovider",
        ReportDiagnosticsWriter.fileProviderAuthority(context),
    )
  }

  @Test
  fun newFileReusesExistingDiagnosticsDir() {
    val context = RuntimeEnvironment.getApplication()
    val first = ReportDiagnosticsWriter.newFile(context, "md")
    assertTrue(first.parentFile?.exists() ?: false)
    val second = ReportDiagnosticsWriter.newFile(context, "md")
    assertNotNull("second file created", second.parentFile)
    assertEquals("same parent dir reused", first.parent, second.parent)
  }
}
