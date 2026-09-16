package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionIndicatorTest {

  private val indicator = ConnectionIndicator()

  @Test
  fun borderColorResourceMapsEachState() {
    data class Case(val state: ConnectionState, val expected: Int)
    val cases =
        listOf(
            Case(ConnectionState.Connecting, R.color.hud_accent_connecting),
            Case(ConnectionState.Connected, R.color.hud_accent_connected_dark),
            Case(ConnectionState.Disconnected("Target changed."), R.color.hud_accent_error),
            Case(ConnectionState.Disconnected(""), R.color.hud_sepia),
            Case(
                ConnectionState.Disconnected(ConnectionState.PAUSE_DISCONNECT_REASON),
                R.color.hud_sepia,
            ),
        )
    for (case in cases) {
      assertEquals("border ${case.state}", case.expected, indicator.borderColorResource(case.state))
    }
  }

  @Test
  fun accentColorFollowsTheSameSemantics() {
    data class Case(val state: ConnectionState, val expected: Int)
    val cases =
        listOf(
            Case(ConnectionState.Connected, R.color.hud_accent_connected),
            Case(ConnectionState.Connecting, R.color.hud_accent_connecting),
            Case(ConnectionState.Disconnected(""), R.color.hud_sepia),
            Case(ConnectionState.Disconnected("Target changed."), R.color.hud_accent_error),
        )
    for (case in cases) {
      assertEquals("accent ${case.state}", case.expected, indicator.accentColorResource(case.state))
    }
  }
}
