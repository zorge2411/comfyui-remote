package com.example.comfyui_remote.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun isValidHost_correctInputs_returnsTrue() {
        assertTrue(ValidationUtils.isValidHost("192.168.1.1"))
        assertTrue(ValidationUtils.isValidHost("example.com"))
        assertTrue(ValidationUtils.isValidHost("sub.example.com"))
        assertTrue(ValidationUtils.isValidHost("localhost"))
        assertTrue(ValidationUtils.isValidHost("my-pc"))
        assertTrue(ValidationUtils.isValidHost("[::1]")) // IPv6
    }

    @Test
    fun isValidHost_invalidInputs_returnsFalse() {
        assertFalse(ValidationUtils.isValidHost("")) // Empty
        assertFalse(ValidationUtils.isValidHost("   ")) // Blank
        assertFalse(ValidationUtils.isValidHost("http://example.com")) // Protocol included
        assertFalse(ValidationUtils.isValidHost("example.com/api")) // Path included
        assertFalse(ValidationUtils.isValidHost("example.com?q=1")) // Query included
        assertFalse(ValidationUtils.isValidHost("user@example.com")) // Auth included
    }

    @Test
    fun isValidPort_correctInputs_returnsTrue() {
        assertTrue(ValidationUtils.isValidPort("8188"))
        assertTrue(ValidationUtils.isValidPort("80"))
        assertTrue(ValidationUtils.isValidPort("1"))
        assertTrue(ValidationUtils.isValidPort("65535"))
    }

    @Test
    fun isValidPort_invalidInputs_returnsFalse() {
        assertFalse(ValidationUtils.isValidPort("")) // Empty
        assertFalse(ValidationUtils.isValidPort("abc")) // Non-numeric
        assertFalse(ValidationUtils.isValidPort("0")) // Out of range (min 1)
        assertFalse(ValidationUtils.isValidPort("-1")) // Negative
        assertFalse(ValidationUtils.isValidPort("65536")) // Out of range
        assertFalse(ValidationUtils.isValidPort("8188.5")) // Float
    }
}
