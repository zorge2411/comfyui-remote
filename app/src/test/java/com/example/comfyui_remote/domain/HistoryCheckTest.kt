package com.example.comfyui_remote.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class HistoryCheckTest {

    @Test
    fun `an empty history has no entries`() {
        assertFalse(HistoryCheck.hasEntries(StringReader("{}")))
        assertFalse(HistoryCheck.hasEntries(StringReader("  { }  ")))
    }

    @Test
    fun `a history with a prompt has entries`() {
        assertTrue(HistoryCheck.hasEntries(StringReader("""{"3b69df25": {"prompt": [1, "3b69df25", {}], "outputs": {}}}""")))
    }

    @Test
    fun `garbage or an error body counts as no entries`() {
        assertFalse(HistoryCheck.hasEntries(StringReader("")))
        assertFalse(HistoryCheck.hasEntries(StringReader("[]")))
        assertFalse(HistoryCheck.hasEntries(StringReader("<html>502</html>")))
    }
}
