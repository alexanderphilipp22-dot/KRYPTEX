package com.kryptex.vault

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptex.vault.stego.CapacityCalculator
import com.kryptex.vault.stego.CarrierBitmap
import com.kryptex.vault.stego.EmbedOutcome
import com.kryptex.vault.stego.ExtractOutcome
import com.kryptex.vault.stego.PayloadContainer
import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.stego.StegoEngine
import com.kryptex.vault.stego.UnsupportedCarrierException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

/**
 * The part of steganography that only a real Android runtime can break: bitmap premultiplication,
 * colour management and the PNG codec must all be bit-exact, or the payload silently dies.
 */
@RunWith(AndroidJUnit4::class)
class PngRoundTripTest {
    private val engine = StegoEngine()
    private val key = ByteArray(32) { (it * 11 + 5).toByte() }

    private fun photoBitmap(width: Int, height: Int, seed: Long = 3): Bitmap {
        val random = Random(seed)
        val pixels = IntArray(width * height) { i ->
            Color.rgb(
                (i % width * 255 / width + random.nextInt(10)).coerceIn(0, 255),
                (i / width * 255 / height + random.nextInt(10)).coerceIn(0, 255),
                random.nextInt(256),
            )
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun container(text: String) =
        PayloadContainer(PayloadType.TEXT, "text/plain", "note.txt", 1_700_000_000_000L, text.toByteArray())

    private fun found(outcome: ExtractOutcome): PayloadContainer {
        assertTrue("expected Found but was $outcome", outcome is ExtractOutcome.Found)
        return (outcome as ExtractOutcome.Found).container
    }

    @Test
    fun embedEncodePngDecodeExtractIsBitExact() {
        val carrier = CarrierBitmap.fromBitmap(photoBitmap(640, 480))
        val work = carrier.copy()
        val payload = container("Alles, was in dieses Bild passt.")

        assertTrue(engine.embed(work.pixels, work.width, work.height, payload, key) is EmbedOutcome.Embedded)
        val png = work.toPng()

        val decoded = CarrierBitmap.decodeForExtraction(png)
        assertEquals(work.width, decoded.width)
        assertEquals(work.height, decoded.height)
        assertArrayEquals("PNG round trip must not change a single pixel", work.pixels, decoded.pixels)
        assertEquals(payload, found(engine.extract(decoded.pixels, decoded.width, decoded.height, key)))
    }

    @Test
    fun aCarrierFilledToItsMaximumSurvivesThePngCodec() {
        val carrier = CarrierBitmap.fromBitmap(photoBitmap(1600, 1200))
        val work = carrier.copy()
        // Exactly the largest payload the app allows for this carrier (the 50 % cap).
        val size = CapacityCalculator.maxPayloadBytes(work.width, work.height, "voice.m4a", "audio/aac").toInt()
        val data = ByteArray(size).also { Random(9).nextBytes(it) }
        val payload = PayloadContainer(PayloadType.AUDIO, "audio/aac", "voice.m4a", 3L, data)

        val outcome = engine.embed(work.pixels, work.width, work.height, payload, key)
        assertTrue("a $size byte payload must fit at the cap, but: $outcome", outcome is EmbedOutcome.Embedded)
        val decoded = CarrierBitmap.decodeForExtraction(work.toPng())

        assertArrayEquals(data, found(engine.extract(decoded.pixels, decoded.width, decoded.height, key)).data)
    }

    @Test
    fun theSavedPictureIsAnOrdinaryRgbPngWithoutAnAlphaChannel() {
        val png = CarrierBitmap.fromBitmap(photoBitmap(64, 64)).toPng()
        val signature = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        assertArrayEquals(signature, png.copyOf(8))
        // IHDR data starts at byte 16: width(4) height(4) bitDepth(1) colourType(1). Colour type 2 = RGB.
        assertEquals("colour type", 2, png[25].toInt())
        assertEquals("bit depth", 8, png[24].toInt())
    }

    @Test
    fun translucentPicturesAreFlattenedSoPremultiplicationCannotCorruptTheBits() {
        val width = 200
        val height = 150
        val argb = IntArray(width * height) { i ->
            // A mix of fully transparent, half transparent and opaque pixels.
            val alpha = when (i % 3) { 0 -> 0; 1 -> 128; else -> 255 }
            Color.argb(alpha, i % 256, (i / 3) % 256, (i * 7) % 256)
        }
        val source = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
        assertTrue(source.hasAlpha())

        val carrier = CarrierBitmap.fromBitmap(source)
        assertTrue("every pixel must be opaque", carrier.pixels.all { (it ushr 24) == 0xFF })

        val work = carrier.copy()
        val payload = container("flattened")
        assertTrue(engine.embed(work.pixels, width, height, payload, key) is EmbedOutcome.Embedded)

        val decoded = CarrierBitmap.decodeForExtraction(work.toPng())
        assertArrayEquals(work.pixels, decoded.pixels)
        assertEquals(payload, found(engine.extract(decoded.pixels, width, height, key)))
    }

    @Test
    fun theOriginalPictureAndTheWrongKeyBothFindNothing() {
        val carrier = CarrierBitmap.fromBitmap(photoBitmap(320, 240))
        assertEquals(ExtractOutcome.NothingFound, engine.extract(carrier.pixels, carrier.width, carrier.height, key))

        val work = carrier.copy()
        engine.embed(work.pixels, work.width, work.height, container("x"), key)
        val wrong = key.copyOf().also { it[5] = (it[5] + 1).toByte() }
        assertEquals(ExtractOutcome.NothingFound, engine.extract(work.pixels, work.width, work.height, wrong))
    }

    @Test
    fun reEncodingToJpegDestroysThePayloadAsDocumented() {
        val work = CarrierBitmap.fromBitmap(photoBitmap(400, 300)).copy()
        engine.embed(work.pixels, work.width, work.height, container("fragile"), key)

        val jpeg = java.io.ByteArrayOutputStream().also { out ->
            val bmp = work.toBitmap()
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            bmp.recycle()
        }.toByteArray()
        val decoded = CarrierBitmap.decodeForExtraction(jpeg)

        assertEquals(ExtractOutcome.NothingFound, engine.extract(decoded.pixels, decoded.width, decoded.height, key))
    }

    @Test
    fun bytesThatAreNotAPictureAreRejectedCleanly() {
        assertThrows(UnsupportedCarrierException::class.java) {
            CarrierBitmap.decodeForExtraction(ByteArray(500) { it.toByte() })
        }
    }
}
