package com.trikset.gamepad2

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Stores named robot presets (host / TCP port / video URI) as a single JSON object under one pref
 * key. Pure data access: [save]/[all]/[delete] operate on the whole set, so the settings UI can
 * render dynamic rows and "apply a preset" with a single `edit { }` of the three live keys. The
 * schema is per-entry JSON so a corrupt entry cannot take down the whole store.
 */
class RobotPresetStore(private val prefs: SharedPreferences) {

  data class Preset(val name: String, val host: String, val port: String, val videoUri: String)

  fun save(name: String, host: String, port: String, videoUri: String) {
    val updated = all().toMutableMap()
    updated[name] = Preset(name, host, port, videoUri)
    persist(updated)
  }

  @Suppress("SwallowedException") // a corrupt payload is a recoverable "empty store", not an error
  fun all(): Map<String, Preset> {
    val raw = prefs.getString(SK_ROBOT_PRESETS, null) ?: return emptyMap()
    return try {
      val root = JSONObject(raw)
      val names = root.keys()
      buildMap {
        while (names.hasNext()) {
          val name = names.next()
          val entry = root.optJSONObject(name) ?: continue
          val host = entry.optString("host", "")
          val port = entry.optString("port", "")
          val videoUri = entry.optString("videoUri", "")
          if (name.isNotBlank()) put(name, Preset(name, host, port, videoUri))
        }
      }
    } catch (e: org.json.JSONException) {
      emptyMap()
    }
  }

  fun delete(name: String) {
    val updated = all().toMutableMap()
    if (updated.remove(name) != null) {
      persist(updated)
    }
  }

  private fun persist(presets: Map<String, Preset>) {
    val root = JSONObject()
    for ((name, preset) in presets) {
      root.put(
          name,
          JSONObject()
              .put("host", preset.host)
              .put("port", preset.port)
              .put("videoUri", preset.videoUri),
      )
    }
    prefs.edit { putString(SK_ROBOT_PRESETS, root.toString()) }
  }

  companion object {
    const val SK_ROBOT_PRESETS = "robotPresetsData"
  }
}
