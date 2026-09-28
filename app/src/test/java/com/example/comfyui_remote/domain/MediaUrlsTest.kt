package com.example.comfyui_remote.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUrlsTest {

    @Test
    fun `plain names are unchanged`() {
        assertEquals(
            "http://h:8188/view?filename=z_0001_.png&type=output",
            MediaUrls.view("http://h:8188", "z_0001_.png")
        )
    }

    @Test
    fun `spaces, ampersands and subfolders are encoded`() {
        assertEquals(
            "http://h:8188/view?filename=my%20shot%20%26%20more.png&subfolder=krea%2Fday%201&type=input",
            MediaUrls.view("http://h:8188", "my shot & more.png", "krea/day 1", "input")
        )
    }

    @Test
    fun `an empty subfolder is left out`() {
        assertEquals("http://h:8188/view?filename=a.png&type=output", MediaUrls.view("http://h:8188", "a.png", ""))
    }
}
