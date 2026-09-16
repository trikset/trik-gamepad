package com.trikset.gamepad2

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Direct tests for [RobotPresetStore] — the SharedPreferences+JSON preset persistence. */
@RunWith(RobolectricTestRunner::class)
class RobotPresetStoreTest : RobolectricTestBase() {

  private val prefs =
      PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())

  @Before
  fun clearPrefs() {
    prefs.edit().clear().commit()
  }

  private fun store() = RobotPresetStore(prefs)

  @Test
  fun emptyStoreHasNoPresets() {
    assertTrue(store().all().isEmpty())
  }

  @Test
  fun saveShouldPersistPreset() {
    store().save("robot", "10.0.0.7", "4444", "http://10.0.0.7:8080/?action=stream")
    val all = RobotPresetStore(prefs).all()
    assertEquals(1, all.size)
    assertEquals(
        RobotPresetStore.Preset("robot", "10.0.0.7", "4444", "http://10.0.0.7:8080/?action=stream"),
        all["robot"],
    )
  }

  @Test
  fun saveShouldOverwriteExistingName() {
    val s = store()
    s.save("robot", "10.0.0.7", "4444", "")
    s.save("robot", "10.0.0.8", "5555", "http://10.0.0.8:8080/?action=stream")
    assertEquals(1, s.all().size)
    assertEquals("10.0.0.8", s.all()["robot"]?.host)
  }

  @Test
  fun deleteShouldRemoveOnlyTheNamedPreset() {
    val s = store()
    s.save("a", "1.1.1.1", "4444", "")
    s.save("b", "2.2.2.2", "4444", "")
    s.delete("a")
    val all = s.all()
    assertEquals(1, all.size)
    assertEquals("b", all.keys.first())
  }

  @Test
  fun deleteMissingPresetShouldBeNoOp() {
    val s = store()
    s.save("a", "1.1.1.1", "4444", "")
    s.delete("nope")
    assertEquals(1, s.all().size)
  }

  @Test
  fun malformedEntryShouldBeSkipped() {
    prefs
        .edit()
        .putString(RobotPresetStore.SK_ROBOT_PRESETS, "{\"bad\": \"not-an-object\"}")
        .commit()
    assertTrue(store().all().isEmpty())
  }

  @Test
  fun blankNameEntryShouldBeSkipped() {
    // A preset stored under a blank name is not exposed by all().
    prefs
        .edit()
        .putString(
            RobotPresetStore.SK_ROBOT_PRESETS,
            "{\"\": {\"host\": \"10.0.0.7\", \"port\": \"4444\", \"videoUri\": \"\"}}",
        )
        .commit()
    assertTrue(store().all().isEmpty())
  }
}
