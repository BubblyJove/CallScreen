package com.callscreen.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {

    @Test
    fun `maskPhone masks long number keeping last 4 digits`() {
        assertEquals("***5678", maskPhone("+12345678"))
    }

    @Test
    fun `maskPhone masks standard 10-digit number`() {
        assertEquals("***7890", maskPhone("1234567890"))
    }

    @Test
    fun `maskPhone masks short number with more than 4 chars`() {
        assertEquals("***2345", maskPhone("12345"))
    }

    @Test
    fun `maskPhone returns stars for 4-char number`() {
        assertEquals("***", maskPhone("1234"))
    }

    @Test
    fun `maskPhone returns stars for empty string`() {
        assertEquals("***", maskPhone(""))
    }

    @Test
    fun `maskPhone returns stars for single char`() {
        assertEquals("***", maskPhone("5"))
    }

    @Test
    fun `sha256 produces correct hash`() {
        val hash = sha256("hello")
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash)
    }

    @Test
    fun `sha256 produces different hash for different input`() {
        val h1 = sha256("hello")
        val h2 = sha256("world")
        assert(h1 != h2)
    }
}
