package com.kryptex.vault.stego

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StegoEngineTest {
    private val engine = StegoEngine()
    private val key = ByteArray(32) { (it * 7 + 2).toByte() }

    private fun photo(width: Int, height: Int, seed: Long = 21): IntArray {
        val random = Random(seed)
        return IntArray(width * height) { i ->
            val r = (i % width * 255 / width + random.nextInt(10)).coerceIn(0, 255)
            val g = (i / width * 255 / height + random.nextInt(10)).coerceIn(0, 255)
            val b = (128 + random.nextInt(40)).coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun container(data: ByteArray, name: String = "note.txt", mime: String = "text/plain") =
        PayloadContainer(PayloadType.TEXT, mime, name, 1_700_000_000_000L, data)

    private fun found(outcome: ExtractOutcome): PayloadContainer {
        assertTrue("expected Found but was $outcome", outcome is ExtractOutcome.Found)
        return (outcome as ExtractOutcome.Found).container
    }

    @Test
    fun embedThenExtractReturnsTheSamePayload() {
        val pixels = photo(800, 600)
        val original = container("Treffpunkt: Hauptbahnhof, 18:30 Uhr. Passwort: Kryptex".toByteArray())

        val outcome = engine.embed(pixels, 800, 600, original, key)
        assertTrue("outcome $outcome", outcome is EmbedOutcome.Embedded)

        assertEquals(original, found(engine.extract(pixels, 800, 600, key)))
    }

    @Test
    fun aBinaryPayloadOfSeveralHundredKilobytesSurvives() {
        val pixels = photo(1600, 1200)
        val data = ByteArray(300_000).also { Random(4).nextBytes(it) }
        val original = PayloadContainer(PayloadType.AUDIO, "audio/ogg", "memo.ogg", 5L, data)

        assertTrue(engine.embed(pixels, 1600, 1200, original, key) is EmbedOutcome.Embedded)
        assertArrayEquals(data, found(engine.extract(pixels, 1600, 1200, key)).data)
    }

    @Test
    fun theWrongKeyFindsNothing() {
        val pixels = photo(400, 300)
        engine.embed(pixels, 400, 300, container("secret".toByteArray()), key)

        val wrong = key.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertEquals(ExtractOutcome.NothingFound, engine.extract(pixels, 400, 300, wrong))
    }

    @Test
    fun anImageThatNeverHeldAPayloadFindsNothing() {
        val pixels = photo(400, 300)
        assertEquals(ExtractOutcome.NothingFound, engine.extract(pixels, 400, 300, key))
    }

    @Test
    fun extractionDoesNotModifyTheImage() {
        val pixels = photo(400, 300)
        engine.embed(pixels, 400, 300, container("x".toByteArray()), key)
        val before = pixels.copyOf()
        engine.extract(pixels, 400, 300, key)
        assertArrayEquals(before, pixels)
    }

    @Test
    fun aPayloadThatDoesNotFitIsRefusedAndTheImageStaysUntouched() {
        val pixels = photo(100, 100)
        val before = pixels.copyOf()
        val tooBig = container(ByteArray(3000).also { Random(8).nextBytes(it) }, name = "big")

        val outcome = engine.embed(pixels, 100, 100, tooBig, key)

        assertTrue("outcome $outcome", outcome is EmbedOutcome.TooLarge)
        assertArrayEquals(before, pixels)
    }

    @Test
    fun theCapacityBoundaryIsExact() {
        val (w, h) = 120 to 90
        val name = "boundary.bin"
        val mime = "application/octet-stream"
        val max = CapacityCalculator.maxPayloadBytes(w, h, name, mime).toInt()
        assertTrue("image too small for the test", max > 100)

        fun payload(n: Int) = PayloadContainer(PayloadType.FILE, mime, name, 0, ByteArray(n).also { Random(n.toLong()).nextBytes(it) })

        val fits = photo(w, h)
        assertTrue(engine.embed(fits, w, h, payload(max), key, compress = false) is EmbedOutcome.Embedded)
        assertEquals(payload(max), found(engine.extract(fits, w, h, key)))

        val over = photo(w, h)
        assertTrue(engine.embed(over, w, h, payload(max + 1), key, compress = false) is EmbedOutcome.TooLarge)
    }

    @Test
    fun capacityNumbersMatchThePlan() {
        // A 12 MP carrier holds about 2.25 MB at the 50 % cap.
        assertEquals(4_500_000L, CapacityCalculator.theoreticalBytes(4000, 3000))
        assertEquals(2_250_000L, CapacityCalculator.maxStreamBytes(4000, 3000))
        assertTrue(CapacityCalculator.fits(4000, 3000, 2_000_000))
        assertTrue(!CapacityCalculator.fits(4000, 3000, 2_300_000))
    }

    @Test
    fun anyAlterationOfThePixelsDestroysThePayloadInsteadOfCorruptingIt() {
        val pixels = photo(800, 600)
        engine.embed(pixels, 800, 600, container("do not tamper".toByteArray()), key)

        // Flip the low bit of the red channel of every 10th pixel: what a retouch or re-save would do.
        val tampered = pixels.copyOf()
        for (i in tampered.indices step 10) tampered[i] = tampered[i] xor 0x00010000

        assertEquals(ExtractOutcome.NothingFound, engine.extract(tampered, 800, 600, key))
    }

    @Test
    fun lossyRecompressionNoiseDestroysThePayload() {
        val pixels = photo(800, 600)
        engine.embed(pixels, 800, 600, container("fragile".toByteArray()), key)

        val random = Random(99)
        val noisy = IntArray(pixels.size) { i ->
            fun jitter(shift: Int): Int = (((pixels[i] ushr shift) and 0xFF) + random.nextInt(3) - 1).coerceIn(0, 255) shl shift
            (0xFF shl 24) or jitter(16) or jitter(8) or jitter(0)
        }
        assertEquals(ExtractOutcome.NothingFound, engine.extract(noisy, 800, 600, key))
    }

    @Test
    fun theEmbeddedChangesAreSpreadOverTheWholeImage() {
        val original = photo(800, 600)
        val pixels = original.copyOf()
        engine.embed(pixels, 800, 600, container(ByteArray(20_000).also { Random(2).nextBytes(it) }), key, compress = false)

        // Count changed pixels per horizontal tenth of the image.
        val perBand = IntArray(10)
        for (i in pixels.indices) if (pixels[i] != original[i]) perBand[i / (pixels.size / 10)]++
        val total = perBand.sum()
        assertTrue("nothing changed", total > 1000)
        perBand.forEachIndexed { band, n ->
            assertTrue("band $band holds ${n * 100 / total}% of the changes: ${perBand.toList()}", n in (total * 0.07).toInt()..(total * 0.13).toInt())
        }
    }

    @Test
    fun aTwelveMegapixelCarrierIsPracticalToProcess() {
        val (w, h) = 4000 to 3000
        val pixels = photo(w, h)
        val data = ByteArray(2_000_000).also { Random(6).nextBytes(it) }
        val original = PayloadContainer(PayloadType.FILE, "application/octet-stream", "big.bin", 0, data)

        val t0 = System.nanoTime()
        assertTrue(engine.embed(pixels, w, h, original, key, compress = false) is EmbedOutcome.Embedded)
        val embedMs = (System.nanoTime() - t0) / 1_000_000
        val t1 = System.nanoTime()
        assertArrayEquals(data, found(engine.extract(pixels, w, h, key)).data)
        val extractMs = (System.nanoTime() - t1) / 1_000_000

        println("12 MP, 2 MB payload: embed $embedMs ms, extract $extractMs ms")
        assertTrue("embed took $embedMs ms", embedMs < 30_000)
        assertTrue("extract took $extractMs ms", extractMs < 30_000)
    }
}
