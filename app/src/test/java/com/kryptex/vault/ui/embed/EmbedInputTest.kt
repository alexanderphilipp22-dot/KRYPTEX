package com.kryptex.vault.ui.embed

import com.kryptex.vault.stego.CarrierBitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class EmbedInputTest {

    @Test
    fun utf8SizeMatchesTheEncoder() {
        for (text in listOf("", "plain", "Grüße", "日本語", "emoji 🔑 key")) {
            assertEquals(text, text.toByteArray(Charsets.UTF_8).size.toLong(), utf8Size(text))
        }
    }

    @Test
    fun fitUtf8NeverSplitsACharacterAndNeverExceedsTheLimit() {
        val text = "ab🔑cd" // 'a','b', 4-byte key emoji, 'c','d'
        assertEquals("ab", fitUtf8(text, 5)) // the emoji would need bytes 3..6
        assertEquals("ab🔑", fitUtf8(text, 6))
        assertEquals(text, fitUtf8(text, 100))
        assertEquals("", fitUtf8(text, 0))
        assertTrue(utf8Size(fitUtf8("é".repeat(200), 255)) <= 255)
    }

    @Test
    fun readBoundedReadsExactlySizedAndUnsizedInput() {
        val data = ByteArray(1000) { (it * 31).toByte() }
        assertArrayEquals(data, readBounded(ByteArrayInputStream(data), limit = 5000, sizeHint = 1000))
        assertArrayEquals(data, readBounded(ByteArrayInputStream(data), limit = 5000, sizeHint = -1))
        assertArrayEquals(data, readBounded(ByteArrayInputStream(data), limit = 1000, sizeHint = 1000))
        assertEquals(0, readBounded(ByteArrayInputStream(ByteArray(0)), limit = 10, sizeHint = 0).size)
    }

    @Test
    fun readBoundedGrowsWhenTheSizeHintIsTooSmall() {
        val data = ByteArray(200_000) { it.toByte() }
        assertArrayEquals(data, readBounded(ByteArrayInputStream(data), limit = 1_000_000, sizeHint = 10))
    }

    @Test
    fun readBoundedRefusesMoreThanTheLimit() {
        val data = ByteArray(1001)
        for (hint in listOf(-1L, 1001L, 1000L, 10L)) {
            try {
                readBounded(ByteArrayInputStream(data), limit = 1000, sizeHint = hint)
                fail("expected PayloadTooLargeException for hint $hint")
            } catch (_: PayloadTooLargeException) {
                // expected
            }
        }
    }

    @Test
    fun readBoundedHandlesShortReads() {
        val data = ByteArray(5000) { it.toByte() }
        val trickle = object : InputStream() {
            private val inner = ByteArrayInputStream(data)
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, minOf(len, 7))
        }
        assertArrayEquals(data, readBounded(trickle, limit = 10_000, sizeHint = -1))
    }

    @Test
    fun largePicturesAreScaledToTheBudgetKeepingTheAspectRatio() {
        assertEquals(4000 to 3000, CarrierBitmap.scaledSize(4000, 3000, 12_000_000))
        val (w, h) = CarrierBitmap.scaledSize(8000, 6000, 12_000_000)
        assertTrue(w.toLong() * h <= 12_000_000)
        assertTrue(w.toLong() * h > 11_000_000)
        assertEquals(8000.0 / 6000.0, w.toDouble() / h, 0.01)
        assertEquals(1 to 1, CarrierBitmap.scaledSize(1, 1, 1))
    }
}
