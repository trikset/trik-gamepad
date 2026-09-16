package com.trikset.gamepad2.diagnostics

/**
 * A bounded, thread-safe log line store with FIFO eviction: [append] drops the oldest lines once
 * the capacity is exceeded, [tail] returns the newest lines oldest-first. Extracted from [AppLog]
 * so the eviction/ordering semantics are unit-testable without Robolectric (the app-wide buffer is
 * fed from several threads: the sender executor, the MJPEG render thread and the main thread).
 */
class LogRingBuffer(private val capacity: Int) {

  private val buffer = ArrayDeque<String>()

  fun append(line: String) {
    synchronized(this) {
      buffer.addLast(line)
      while (buffer.size > capacity) {
        buffer.removeFirst()
      }
    }
  }

  /** The newest [count] lines, oldest first; fewer when the buffer is not full yet. */
  fun tail(count: Int): List<String> =
      synchronized(this) {
        val size = buffer.size
        val take = if (count < size) count else size
        buffer.takeLast(take).toList()
      }

  fun clear() {
    synchronized(this) { buffer.clear() }
  }
}
