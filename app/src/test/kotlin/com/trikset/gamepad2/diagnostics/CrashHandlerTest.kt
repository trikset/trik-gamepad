package com.trikset.gamepad2.diagnostics

import android.content.Context
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.RobolectricTestBase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CrashHandlerTest : RobolectricTestBase() {

  private lateinit var context: Context

  @Before
  fun cleanState() {
    context = RuntimeEnvironment.getApplication()
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    File(context.filesDir, CrashLogStore.CRASH_DIR).deleteRecursively()
  }

  @Test
  fun capturesStackTraceAndDelegatesToPrevious() {
    val store = CrashLogStore(context)
    var delegated = false
    val previous = Thread.UncaughtExceptionHandler { _, _ -> delegated = true }
    CrashHandler(store, previous)
        .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

    assertTrue("the crash must reach the previous handler", delegated)
    val record = store.latest()
    assertTrue(record != null)
    assertTrue(record!!.stackTrace.contains("java.lang.RuntimeException: boom"))
  }

  @Test
  fun capturesEvenWhenNoPreviousHandlerExists() {
    val store = CrashLogStore(context)
    CrashHandler(store, null).uncaughtException(Thread.currentThread(), IllegalStateException("x"))

    assertTrue(store.latest()!!.stackTrace.contains("java.lang.IllegalStateException: x"))
    assertEquals(1, File(context.filesDir, CrashLogStore.CRASH_DIR).listFiles()!!.size)
  }

  @Test
  fun failedCaptureDoesNotBreakTheCrashPath() {
    // Block the crash directory: a regular FILE at the crashes path makes
    // writeText throw IOException, exercising the defensive catch. The crash
    // path must still delegate to the previous handler.
    val crashesFile = File(context.filesDir, CrashLogStore.CRASH_DIR)
    crashesFile.writeText("block the directory")
    var delegated = false
    val previous = Thread.UncaughtExceptionHandler { _, _ -> delegated = true }

    CrashHandler(CrashLogStore(context), previous)
        .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

    assertTrue("the crash must reach the previous handler even when capture fails", delegated)
  }
}
