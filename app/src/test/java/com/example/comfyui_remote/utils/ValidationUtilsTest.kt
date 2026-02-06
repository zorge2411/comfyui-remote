package com.example.comfyui_remote.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUtilsTest {

    @Test
    fun isValidHost_correctIPs_returnsTrue() {
        assertTrue(ValidationUtils.isValidHost("192.168.1.1"))
        assertTrue(ValidationUtils.isValidHost("127.0.0.1"))
        assertTrue(ValidationUtils.isValidHost("8.8.8.8"))
    }

    @Test
    fun isValidHost_ambiguousButValidHostnames_returnsTrue() {
        // These look like partial IPs but are valid hostnames on local networks
        assertTrue(ValidationUtils.isValidHost("192.168.1"))
        assertTrue(ValidationUtils.isValidHost("192.168.1.a"))
    }

    @Test
    fun isValidHost_correctHostnames_returnsTrue() {
        assertTrue(ValidationUtils.isValidHost("localhost"))
        assertTrue(ValidationUtils.isValidHost("example.com"))
        assertTrue(ValidationUtils.isValidHost("sub.example.com"))
        assertTrue(ValidationUtils.isValidHost("my-pc"))
        assertTrue(ValidationUtils.isValidHost("my-pc.local"))
    }

    @Test
    fun isValidHost_invalidStrings_returnsFalse() {
        assertFalse(ValidationUtils.isValidHost("-start-dash"))
        assertFalse(ValidationUtils.isValidHost("end-dash-"))
        assertFalse(ValidationUtils.isValidHost("inv@lid.char"))
        assertFalse(ValidationUtils.isValidHost("space in name"))
        assertFalse(ValidationUtils.isValidHost("..."))
        assertFalse(ValidationUtils.isValidHost(".startdot"))
    }

    @Test
    fun isValidHost_withScheme_stripsAndValidates() {
        assertTrue(ValidationUtils.isValidHost("http://192.168.1.1"))
        assertTrue(ValidationUtils.isValidHost("https://example.com"))
        assertTrue(ValidationUtils.isValidHost("http://localhost/"))
    }

    @Test
    fun isValidHost_withPort_returnsFalse() {
        // We decided to enforce port separation
        assertFalse(ValidationUtils.isValidHost("192.168.1.1:8188"))
        assertFalse(ValidationUtils.isValidHost("example.com:80"))
    }

    @Test
    fun isValidPort_correctPorts_returnsTrue() {
        assertTrue(ValidationUtils.isValidPort("80"))
        assertTrue(ValidationUtils.isValidPort("8188"))
        assertTrue(ValidationUtils.isValidPort("65535"))
        assertTrue(ValidationUtils.isValidPort("1"))
    }

    @Test
    fun isValidPort_invalidPorts_returnsFalse() {
        assertFalse(ValidationUtils.isValidPort("0"))
        assertFalse(ValidationUtils.isValidPort("65536"))
        assertFalse(ValidationUtils.isValidPort("-1"))
        assertFalse(ValidationUtils.isValidPort("abc"))
        assertFalse(ValidationUtils.isValidPort(""))
    }
}
