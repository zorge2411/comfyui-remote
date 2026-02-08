package com.example.comfyui_remote.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUtilsTest {

    @Test
    fun testValidHostnames() {
        assertTrue(ValidationUtils.isValidHost("localhost"))
        assertTrue(ValidationUtils.isValidHost("192.168.1.1"))
        assertTrue(ValidationUtils.isValidHost("example.com"))
        assertTrue(ValidationUtils.isValidHost("my-server.local"))
        assertTrue(ValidationUtils.isValidHost("comfyui-server-1"))
        assertTrue(ValidationUtils.isValidHost("sub.domain.com"))
    }

    @Test
    fun testInvalidHostnames() {
        assertFalse(ValidationUtils.isValidHost(""))
        assertFalse(ValidationUtils.isValidHost("   "))
        assertFalse(ValidationUtils.isValidHost("http://example.com")) // Protocol included
        assertFalse(ValidationUtils.isValidHost("example.com/api")) // Path included
        assertFalse(ValidationUtils.isValidHost("example.com:8188")) // Port included
        assertFalse(ValidationUtils.isValidHost("-example.com")) // Starts with hyphen
        assertFalse(ValidationUtils.isValidHost("example.com-")) // Ends with hyphen
        assertFalse(ValidationUtils.isValidHost(".example.com")) // Starts with dot
        assertFalse(ValidationUtils.isValidHost("example.com.")) // Ends with dot
        assertFalse(ValidationUtils.isValidHost("example..com")) // Consecutive dots
        assertFalse(ValidationUtils.isValidHost("evil_host")) // Underscore (standard hostname rules usually allow hyphens but not underscores, though some resolvers allow it, let's stick to strict)
        assertFalse(ValidationUtils.isValidHost("invalid character!"))
    }

    @Test
    fun testValidPorts() {
        assertTrue(ValidationUtils.isValidPort(1))
        assertTrue(ValidationUtils.isValidPort(80))
        assertTrue(ValidationUtils.isValidPort(443))
        assertTrue(ValidationUtils.isValidPort(8188))
        assertTrue(ValidationUtils.isValidPort(65535))

        assertTrue(ValidationUtils.isValidPort("8188"))
        assertTrue(ValidationUtils.isValidPort("80"))
    }

    @Test
    fun testInvalidPorts() {
        assertFalse(ValidationUtils.isValidPort(0))
        assertFalse(ValidationUtils.isValidPort(-1))
        assertFalse(ValidationUtils.isValidPort(65536))
        assertFalse(ValidationUtils.isValidPort(100000))

        assertFalse(ValidationUtils.isValidPort("abc"))
        assertFalse(ValidationUtils.isValidPort(""))
        assertFalse(ValidationUtils.isValidPort("8188a"))
    }
}
