package com.example.comfyui_remote.domain

import org.junit.Assert.*
import org.junit.Test

class QueueRecoveryTest {
    @Test fun `executing with no current item is stale`() {
        assertTrue(QueueRecovery.isStale("EXECUTING", 5, null))
        assertTrue(QueueRecovery.isStale("EXECUTING", 5, 6))
        assertFalse(QueueRecovery.isStale("EXECUTING", 5, 5))
    }

    @Test fun `other statuses are never stale`() {
        for (s in listOf("PENDING", "COMPLETED", "FAILED")) assertFalse(QueueRecovery.isStale(s, 1, null))
    }

    @Test fun `only failed items can retry`() {
        assertTrue(QueueRecovery.canRetry("FAILED"))
        for (s in listOf("PENDING", "EXECUTING", "COMPLETED")) assertFalse(QueueRecovery.canRetry(s))
    }

    @Test fun `the running item cannot be deleted but a stale one can`() {
        assertFalse(QueueRecovery.canDelete("EXECUTING", 5, 5))
        assertTrue(QueueRecovery.canDelete("EXECUTING", 5, null))
        assertTrue(QueueRecovery.canDelete("FAILED", 5, 5))
    }
}
