package com.example.comfyui_remote.domain

import org.junit.Assert.*
import org.junit.Test

class ExecutionProgressTrackerTest {

    @Test
    fun `overall never decreases across nodes even when a sampler restarts at zero`() {
        val t = ExecutionProgressTracker()
        t.start(4)
        var last = 0f
        fun check() {
            val o = t.snapshot().overall
            assertTrue("progress went backwards: $last -> $o", o >= last)
            last = o
        }
        t.onExecuting("1"); check()
        t.onExecuting("2"); check()
        t.onStep(10, 20); check()
        t.onStep(20, 20); check()
        t.onExecuting("3"); check()
        t.onStep(0, 20); check()
        t.onStep(10, 20); check()
        t.onExecuting("4"); check()
    }

    @Test
    fun `cached nodes are excluded from the total`() {
        val t = ExecutionProgressTracker()
        t.start(5)
        t.markCached(listOf("1", "2"))
        assertEquals(3, t.snapshot().totalToRun)
        t.onExecuting("3")
        t.onExecuting("4")
        t.onExecuting("5")
        val s = t.snapshot()
        assertEquals(2, s.completedNodes)
        assertEquals(2f / 3f, s.overall, 0.001f)
    }

    @Test
    fun `step fraction contributes within the current node`() {
        val t = ExecutionProgressTracker()
        t.start(2)
        t.onExecuting("1")
        t.onExecuting("2")
        t.onStep(10, 20)
        assertEquals(0.75f, t.snapshot().overall, 0.001f)
    }

    @Test
    fun `clamped below 100 percent until finish`() {
        val t = ExecutionProgressTracker()
        t.start(2)
        t.onExecuting("1")
        t.onExecuting("2")
        t.onStep(20, 20)
        assertTrue(t.snapshot().overall <= 0.99f)
        t.finish()
        assertEquals(1f, t.snapshot().overall, 0f)
    }

    @Test
    fun `unknown total is zero safe`() {
        val t = ExecutionProgressTracker()
        t.start(0)
        t.onExecuting("1")
        t.onStep(5, 10)
        assertEquals(0f, t.snapshot().overall, 0f)
    }
}
