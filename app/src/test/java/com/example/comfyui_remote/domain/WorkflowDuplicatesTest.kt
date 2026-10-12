package com.example.comfyui_remote.domain

import org.junit.Assert.*
import org.junit.Test

class WorkflowDuplicatesTest {
    private val tmpl = "SERVER_TEMPLATE"
    private val user = "SERVER_USERDATA"
    private val existing = listOf(
        WorkflowRef(1, "Flux Dev", tmpl, "flux_dev"),
        WorkflowRef(2, "My Flow", user, "workflows/My Flow.json"),
        WorkflowRef(3, "Pasted", "LOCAL_IMPORT", null)
    )

    @Test fun `sourceKey match wins even when the title changed`() {
        assertEquals(1L, WorkflowDuplicates.find(existing, tmpl, "flux_dev", "Renamed")?.id)
    }

    @Test fun `different key with the same name is not a duplicate`() {
        assertNull(WorkflowDuplicates.find(existing, tmpl, "other", "Flux Dev"))
    }

    @Test fun `no key falls back to source and name, ignoring case`() {
        assertEquals(3L, WorkflowDuplicates.find(existing, "LOCAL_IMPORT", null, "pasted")?.id)
        assertNull(WorkflowDuplicates.find(existing, "OTHER", null, "Pasted"))
    }

    @Test fun `no match gives null`() {
        assertNull(WorkflowDuplicates.find(emptyList(), tmpl, "x", "x"))
        assertNull(WorkflowDuplicates.find(existing, user, "nope.json", "Nope"))
    }

    @Test fun `uniqueName numbers copies and fills gaps`() {
        assertEquals("X", WorkflowDuplicates.uniqueName("X", listOf("Y")))
        assertEquals("X (2)", WorkflowDuplicates.uniqueName("X", listOf("X")))
        assertEquals("X (3)", WorkflowDuplicates.uniqueName("X", listOf("x", "X (2)")))
        assertEquals("X (2)", WorkflowDuplicates.uniqueName("X", listOf("X", "X (3)")))
        assertEquals("X (2) (2)", WorkflowDuplicates.uniqueName("X (2)", listOf("X (2)")))
    }
}
