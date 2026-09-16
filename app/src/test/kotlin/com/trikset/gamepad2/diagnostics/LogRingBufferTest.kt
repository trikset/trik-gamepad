package com.trikset.gamepad2.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRingBufferTest {

  @Test
  fun appendKeepsNewestAndTailPreservesOrder() {
    val ring = LogRingBuffer(3)
    ring.append("a")
    ring.append("b")
    ring.append("c")

    assertEquals(listOf("a", "b", "c"), ring.tail(10))
  }

  @Test
  fun evictsOldestPastCapacity() {
    val ring = LogRingBuffer(3)
    ring.append("a")
    ring.append("b")
    ring.append("c")
    ring.append("d")
    ring.append("e")

    val lines = ring.tail(10)
    assertEquals(3, lines.size)
    assertEquals("c", lines[0])
    assertEquals("e", lines[2])
  }

  @Test
  fun tailReturnsAtMostRequestedCount() {
    val ring = LogRingBuffer(10)
    ring.append("a")
    ring.append("b")
    ring.append("c")

    val one = ring.tail(1)
    assertEquals(1, one.size)
    assertEquals("c", one[0])
    assertTrue(ring.tail(2).contains("b"))
  }

  @Test
  fun tailBelowCapacityReturnsEverything() {
    val ring = LogRingBuffer(10)
    ring.append("a")
    ring.append("b")

    assertEquals(listOf("a", "b"), ring.tail(100))
  }

  @Test
  fun clearDropsAllLines() {
    val ring = LogRingBuffer(3)
    ring.append("a")
    ring.clear()
    assertTrue(ring.tail(10).isEmpty())
  }

  @Test
  fun capacityOneKeepsOnlyNewest() {
    val ring = LogRingBuffer(1)
    ring.append("a")
    ring.append("b")

    assertEquals(listOf("b"), ring.tail(10))
  }
}
