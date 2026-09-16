package com.trikset.gamepad2

import android.os.Handler
import android.os.Looper
import com.trikset.gamepad2.diagnostics.AppLog
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maintains the control channel to the robot and sends newline-terminated plain-text commands (`pad
 * 1 x y`, `btn N down`, `wheel <angle>`, `keepalive <ms>`). The transport
 * ([CommandTransport][com.trikset.gamepad2.CommandTransport]) is selected per [transportMode]
 * through [transportFactory] so the protocol is independent of the socket (persistent TCP via
 * [TcpTransport], or one command per datagram via [UdpTransport]).
 *
 * Over UDP the robot may optionally respond with `keepalive <ms>` / any control message: every
 * received line resets the robot-liveness clock, and a received `keepalive <ms>` sets the expected
 * heartbeat interval (any message resets the timer; no message within `ms` + a gap → disconnect).
 * Per keepalive tick the last pad/wheel state is re-sent so dropped UDP datagrams converge (buttons
 * are edges and are not re-sent). See DESIGN.md "Gamepad protocol (source of truth)".
 *
 * All network/keepalive collaborators are injected via the constructor (defaults preserved) so
 * tests substitute a
 * [PausedExecutorService][org.robolectric.android.util.concurrent.PausedExecutorService] for
 * [executor] without touching static state (the former `@JvmField` statics
 * `mExecutor`/`keepaliveTimeout`/`mConnectTask` are gone). The connection and keepalive helpers
 * live in [ConnectRunnable] / [KeepAliveTimer].
 */
class SenderService(
    executor: Executor = Executors.newSingleThreadExecutor(),
    initialKeepaliveTimeout: Int = DEFAULT_KEEPALIVE,
    keepAliveScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
          Thread(runnable, "SenderServiceKeepAlive").apply { isDaemon = true }
        },
    private val transportFactory: (TransportMode) -> CommandTransport = { mode ->
      when (mode) {
        TransportMode.TCP -> TcpTransport()
        TransportMode.UDP -> UdpTransport()
      }
    },
) {

  fun interface OnEventListener<ArgT> {
    fun onEvent(arg: ArgT)
  }

  private val syncFlag = Any()
  private var executor: Executor = executor
  var keepaliveTimeout: Int = initialKeepaliveTimeout
    set(value) {
      if (value != field) {
        field = value
        keepAliveTimer.restart()
      }
    }

  /** The control-channel transport. A change disconnects; the next command reconnects on it. */
  var transportMode: TransportMode = TransportMode.TCP
    set(value) {
      if (value != field) {
        field = value
        disconnect("Transport changed.")
      }
    }

  private var connectTask: Runnable? = null
  // internal (not private) so ConnectRunnable / KeepAliveTimer can reach them.
  internal val mainHandler = Handler(Looper.getMainLooper())
  internal var showTextCallback: OnEventListener<String>? = null
  internal var onDisconnectedListener: OnEventListener<String>? = null
  internal var transport: CommandTransport? = null
  var hostAddr: String? = null
    private set

  var hostPort: Int = 0
    private set

  /**
   * The robot's announced heartbeat interval (`ms`); `<= 0` = no expectation (disabled /
   * unlimited).
   */
  var robotKeepaliveTimeoutMs: Int = -1
    private set

  // Robot-liveness clock: every received line resets it (see checkRobotLiveness).
  private var lastRobotMessageMs = 0L
  // Last pad/wheel state, re-sent each keepalive tick over UDP so dropped datagrams converge.
  private var lastPad1Command: String? = null
  private var lastPad2Command: String? = null
  private var lastWheelCommand: String? = null

  private val keepAliveTimer = KeepAliveTimer(this, keepAliveScheduler)
  private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(""))
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  // Listener-registration setters (Android style, like setOnClickListener): Kotlin does not
  // SAM-convert a lambda assigned to a fun-interface *property*, so these stay methods.
  fun setShowTextCallback(showTextCallback: OnEventListener<String>?) {
    this.showTextCallback = showTextCallback
  }

  fun setOnDisconnectedListener(onDisconnectedListener: OnEventListener<String>?) {
    this.onDisconnectedListener = onDisconnectedListener
  }

  private fun connectAsync() {
    synchronized(syncFlag) {
      if (connectTask != null) {
        return
      }
      _connectionState.value = ConnectionState.Connecting
      val task = ConnectRunnable(this)
      connectTask = task
      executor.execute(task)
    }
  }

  // socket is closed from CommandTransport.close()
  internal fun connectToTRIK() {
    synchronized(syncFlag) {
      try {
        AppLog.i(TCP_TAG, "Connecting to $hostAddr:$hostPort")
        val transport = transportFactory(transportMode)
        transport.onMessage = ::onRobotMessage
        transport.open(hostAddr.orEmpty(), hostPort)
        this.transport = transport
        // Fresh connection: no robot heartbeat expectation until the robot announces one.
        robotKeepaliveTimeoutMs = -1
        lastRobotMessageMs = System.currentTimeMillis()
        keepAliveTimer.restart()
        _connectionState.value = ConnectionState.Connected
      } catch (e: IOException) {
        AppLog.e(TCP_TAG, "Connect: Error", e)
        // A refused/unresolvable target must not leave the pill stuck at "Connecting…".
        // Empty reason -> MainActivity does not double-notify (the "Connection to X error."
        // Snackbar from onConnectionFinished is the single notification).
        _connectionState.value = ConnectionState.Disconnected("")
      }
    }
  }

  internal fun onConnectionFinished() {
    showTextCallback?.onEvent(
        "Connection to $hostAddr:$hostPort" + if (transport != null) " established." else " error."
    )
    connectTask = null
  }

  internal fun postCommand(command: String) {
    executor.execute {
      var sendFailed = false
      synchronized(syncFlag) {
        val transport = transport
        if (transport == null || !transport.send(command)) {
          sendFailed = true
        } else {
          rememberLastState(command)
        }
      }
      if (sendFailed) {
        AppLog.e(TCP_TAG, "NotSent: $command")
        mainHandler.post { disconnect("Send failed.") }
      }
    }
  }

  /**
   * Processes a robot→app control message (UDP inbound loop; TCP is write-only and never reports).
   * Any received line resets the liveness clock; `keepalive <ms>` additionally sets the expected
   * heartbeat interval (`<= 0` = disabled). Unknown lines are ignored (additive protocol rule).
   */
  internal fun onRobotMessage(line: String) {
    lastRobotMessageMs = System.currentTimeMillis()
    val trimmed = line.trim()
    if (trimmed.startsWith(KEEPALIVE_PREFIX)) {
      val ms = trimmed.removePrefix(KEEPALIVE_PREFIX).trim().toIntOrNull()
      if (ms != null) {
        robotKeepaliveTimeoutMs = ms
        AppLog.i(TCP_TAG, "Robot keepalive: $ms ms")
      } else {
        AppLog.w(TCP_TAG, "Malformed robot keepalive: $line")
      }
    } else {
      // Additive rule: unknown robot messages are ignored (forward compatible).
      AppLog.d(TCP_TAG, "Ignoring unknown robot message: $line")
    }
  }

  /**
   * Re-sends the last pad/wheel state (UDP only): dropped datagrams converge within one keepalive
   * period. Buttons are edges and are deliberately NOT re-sent (a re-sent `btn N down` would
   * re-trigger the action). Called from the keepalive tick.
   */
  fun resendLastState() {
    if (transportMode != TransportMode.UDP) return
    lastPad1Command?.let { postCommand(it) }
    lastPad2Command?.let { postCommand(it) }
    lastWheelCommand?.let { postCommand(it) }
  }

  /**
   * Disconnects when no robot message arrived within the announced heartbeat interval plus a gap
   * (the robot-keepalive liveness rule). No-op while the robot announced no expectation (`<= 0`).
   * Called from the keepalive tick. [nowMs] is injectable so tests drive the clock
   * deterministically.
   */
  fun checkRobotLiveness(nowMs: Long = System.currentTimeMillis()) {
    val timeoutMs = robotKeepaliveTimeoutMs
    if (timeoutMs > 0) {
      val elapsed = nowMs - lastRobotMessageMs
      if (elapsed > timeoutMs + ROBOT_KEEPALIVE_GAP_MS) {
        mainHandler.post { disconnect("Robot keepalive missed.") }
      }
    }
  }

  private fun rememberLastState(command: String) {
    when {
      command.startsWith("pad 1 ") -> lastPad1Command = command
      command.startsWith("pad 2 ") -> lastPad2Command = command
      command.startsWith("wheel ") -> lastWheelCommand = command
    }
  }

  fun disconnect(reason: String) {
    keepAliveTimer.stop()
    val transport = transport
    if (transport != null) {
      transport.close()
      this.transport = null
      AppLog.i(TCP_TAG, "Disconnected.")
      onDisconnectedListener?.onEvent(reason)
      _connectionState.value = ConnectionState.Disconnected(reason)
    }
  }

  fun send(command: String) {
    if (transport == null) {
      connectAsync() // synchronized on the same object as postCommand
    }
    AppLog.d(TCP_TAG, "Sending '$command'")
    postCommand(command)
    keepAliveTimer.restart()
  }

  /**
   * Establishes the connection without sending a command (the "tap to connect" entry point). No-ops
   * when no target is configured (a blank host is a valid video-only configuration — there is
   * nothing to connect to).
   */
  fun connect() {
    if (transport == null && !hostAddr.isNullOrBlank()) {
      connectAsync()
    }
  }

  fun setTarget(hostAddr: String, hostPort: Int) {
    if (!hostAddr.equals(this.hostAddr, ignoreCase = true) || this.hostPort != hostPort) {
      disconnect("Target changed.")
    }
    this.hostAddr = hostAddr
    this.hostPort = hostPort
  }

  companion object {
    const val DEFAULT_KEEPALIVE = 5000
    const val MINIMAL_KEEPALIVE = 1000
    // Leeway over the robot's announced heartbeat interval before declaring it missed (see
    // checkRobotLiveness; DESIGN.md "Gamepad protocol").
    const val ROBOT_KEEPALIVE_GAP_MS = 2000

    private const val KEEPALIVE_PREFIX = "keepalive "
    private const val TCP_TAG = "TCP"
  }
}
