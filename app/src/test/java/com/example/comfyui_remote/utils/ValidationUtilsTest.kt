package com.example.comfyui_remote.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUtilsTest {

    @Test
    fun testIsValidHost() {
        assertTrue("Localhost should be valid", ValidationUtils.isValidHost("localhost"))
        assertTrue("IPv4 should be valid", ValidationUtils.isValidHost("192.168.1.1"))
        assertTrue("Simple domain should be valid", ValidationUtils.isValidHost("google.com"))
        assertTrue("Subdomain should be valid", ValidationUtils.isValidHost("sub.example.co.uk"))
        assertTrue("Hyphenated domain should be valid", ValidationUtils.isValidHost("my-server.com"))

        assertFalse("Empty string should be invalid", ValidationUtils.isValidHost(""))
        assertFalse("HTTP prefix should be invalid", ValidationUtils.isValidHost("http://google.com"))
        assertFalse("Path should be invalid", ValidationUtils.isValidHost("google.com/path"))
        assertFalse("Port in host should be invalid", ValidationUtils.isValidHost("google.com:80"))
        assertFalse("Starting hyphen should be invalid", ValidationUtils.isValidHost("-start.com"))
        assertFalse("Ending hyphen should be invalid", ValidationUtils.isValidHost("end.com-"))
        assertFalse("Starting dot should be invalid", ValidationUtils.isValidHost(".start.com"))
        assertFalse("Ending dot should be invalid", ValidationUtils.isValidHost("end.com."))
        assertFalse("Consecutive dots should be invalid", ValidationUtils.isValidHost("a..b"))
        assertFalse("Space should be invalid", ValidationUtils.isValidHost("invalid char"))
        assertFalse("Command injection chars should be invalid", ValidationUtils.isValidHost("rm -rf /"))
        assertFalse("Special chars should be invalid", ValidationUtils.isValidHost("user@host"))
    }

    @Test
    fun testIsValidPort() {
        assertTrue("80 should be valid", ValidationUtils.isValidPort("80"))
        assertTrue("8188 should be valid", ValidationUtils.isValidPort("8188"))
        assertTrue("65535 should be valid", ValidationUtils.isValidPort("65535"))
        assertTrue("1 should be valid", ValidationUtils.isValidPort("1"))

        assertFalse("0 should be invalid", ValidationUtils.isValidPort("0"))
        assertFalse("65536 should be invalid", ValidationUtils.isValidPort("65536"))
        assertFalse("-1 should be invalid", ValidationUtils.isValidPort("-1"))
        assertFalse("Non-numeric should be invalid", ValidationUtils.isValidPort("abc"))
        assertFalse("Alphanumeric should be invalid", ValidationUtils.isValidPort("8188a"))
        assertFalse("Empty string should be invalid", ValidationUtils.isValidPort(""))
    }
}
