package com.trikset.gamepad2.diagnostics

import android.content.Context
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.RobolectricTestBase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CrashLogStoreTest : RobolectricTestBase() {

  private lateinit var context: Context

  @Before
  fun cleanState() {
    context = RuntimeEnvironment.getApplication()
    // Both the prefs and the crash files persist across methods in a JVM.
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    File(context.filesDir, CrashLogStore.CRASH_DIR).deleteRecursively()
  }

  @Test
  fun saveThenLatestReturnsTheStackTrace() {
    val store = CrashLogStore(context)
    store.save("java.lang.IllegalStateException: boom")

    val record = store.latest()
    assertNotNull(record)
    assertEquals("java.lang.IllegalStateException: boom", record!!.stackTrace)
  }

  @Test
  fun latestIsNullWhenNothingSaved() {
    assertNull(CrashLogStore(context).latest())
  }

  @Test
  fun onlyNewestCrashesSurviveBeyondCapacity() {
    val store = CrashLogStore(context)
    for (i in 1..5) {
      store.save("crash $i")
    }
    val files = File(context.filesDir, CrashLogStore.CRASH_DIR).listFiles()
    assertEquals(3, files!!.size)
    assertEquals("crash 5", store.latest()!!.stackTrace)
  }

  @Test
  fun shouldPromptUntilMarkedAndRepromptsOnNewCrash() {
    val store = CrashLogStore(context)
    store.save("crash 1")
    assertTrue(store.shouldPrompt())
    store.markPrompted()
    assertFalse(store.shouldPrompt())

    store.save("crash 2")
    assertTrue(store.shouldPrompt())
    store.markPrompted()
    assertFalse(store.shouldPrompt())
  }

  @Test
  fun markPromptedWithoutAnyCrashIsSafe() {
    CrashLogStore(context).markPrompted()
    assertFalse(CrashLogStore(context).shouldPrompt())
  }
}
