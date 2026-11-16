package com.kryptex.vault

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.db.entity.CarrierStatus
import com.kryptex.vault.data.media.CarrierMediaStore
import com.kryptex.vault.stego.CarrierBitmap
import com.kryptex.vault.stego.EmbedOutcome
import com.kryptex.vault.stego.ExtractOutcome
import com.kryptex.vault.stego.PayloadContainer
import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.stego.StegoEngine
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

/** Phase 3 gate: embed, save into the real photo library, reopen from there, extract. */
@RunWith(AndroidJUnit4::class)
class CarrierMediaStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = CarrierMediaStore(context)
    private val engine = StegoEngine()
    private val key = ByteArray(32) { (it * 13 + 1).toByte() }
    private val saved = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        saved.forEach { store.delete(it) }
    }

    private fun photo(width: Int = 500, height: Int = 400): CarrierBitmap {
        val random = Random(17)
        val pixels = IntArray(width * height) { i ->
            Color.rgb(i % width * 255 / width, i / width * 255 / height, random.nextInt(256))
        }
        return CarrierBitmap.fromBitmap(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888))
    }

    private fun entityFor(uri: Uri, sha: String) = CarrierEntity(
        mediaUri = uri.toString(), displayName = "x.png", widthPx = 1, heightPx = 1,
        capacityBytes = 1, fileSha256 = sha, containerKey = ByteArray(32), createdAt = 0,
    )

    @Test
    fun embedSaveReopenFromTheLibraryAndExtractReturnsTheIdenticalPayload() {
        val payload = PayloadContainer(
            PayloadType.TEXT, "text/plain", "geheim.txt", 1_700_000_000_000L,
            "Dieser Text lebt nur in den niedrigsten Bits eines Fotos.".toByteArray(),
        )
        val work = photo().copy()
        assertTrue(engine.embed(work.pixels, work.width, work.height, payload, key) is EmbedOutcome.Embedded)

        val png = work.toPng()
        val carrier = store.save(png).also { saved += it.uri }

        // Reopen exactly as a user would: through the content URI of the photo library.
        assertArrayEquals("file in the library must be byte-identical", png, store.readBytes(carrier.uri))
        val decoded = CarrierBitmap.decodeForExtraction(context, carrier.uri)
        val outcome = engine.extract(decoded.pixels, decoded.width, decoded.height, key)

        assertTrue("outcome $outcome", outcome is ExtractOutcome.Found)
        assertEquals(payload, (outcome as ExtractOutcome.Found).container)
    }

    @Test
    fun theSavedFileLooksLikeACameraPictureInAPlainPicturesFolder() {
        val carrier = store.save(photo(64, 64).toPng()).also { saved += it.uri }

        assertTrue("name ${carrier.displayName}", Regex("""IMG_\d{8}_\d{6}( \(\d+\))?\.png""").matches(carrier.displayName))

        val projection = arrayOf(
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.IS_PENDING,
        )
        context.contentResolver.query(carrier.uri, projection, null, null, null)!!.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Pictures/", c.getString(0))
            assertEquals("image/png", c.getString(1))
            assertEquals("must not stay hidden as pending", 0, c.getInt(2))
        }
    }

    @Test
    fun theFileIsTrackedByItsHashAndExternalChangesAreDetected() {
        val png = photo(80, 60).toPng()
        val carrier = store.save(png).also { saved += it.uri }
        assertEquals(CarrierMediaStore.sha256Hex(png), carrier.sha256)
        assertEquals(carrier.sha256, store.currentSha256(carrier.uri))
        assertEquals(CarrierStatus.OK, store.check(entityFor(carrier.uri, carrier.sha256)))

        // Another app (or the user) edits the picture.
        context.contentResolver.openOutputStream(carrier.uri, "wt")!!.use { it.write(png.copyOf(png.size - 1)) }
        assertEquals(CarrierStatus.MODIFIED, store.check(entityFor(carrier.uri, carrier.sha256)))
    }

    @Test
    fun aDeletedFileIsReportedMissing() {
        val carrier = store.save(photo(80, 60).toPng())
        assertTrue(store.delete(carrier.uri))

        assertNull(store.readBytes(carrier.uri))
        assertNull(store.currentSha256(carrier.uri))
        assertEquals(CarrierStatus.MISSING, store.check(entityFor(carrier.uri, carrier.sha256)))
        assertFalse("second delete finds nothing", store.delete(carrier.uri))
    }

    @Test
    fun aFolderOutsideThePhotoRootsIsRefusedBeforeAnythingIsWritten() {
        assertThrows(IllegalArgumentException::class.java) { store.save(ByteArray(10), folder = "Download") }
        assertThrows(IllegalArgumentException::class.java) { store.save(ByteArray(10), folder = "Pictures/../Download") }
    }

    @Test
    fun aCustomSubfolderInPicturesIsHonoured() {
        val carrier = store.save(photo(32, 32).toPng(), folder = "Pictures/Reisen").also { saved += it.uri }
        context.contentResolver.query(carrier.uri, arrayOf(MediaStore.Images.Media.RELATIVE_PATH), null, null, null)!!.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Pictures/Reisen/", c.getString(0))
        }
        assertNotNull(store.readBytes(carrier.uri))
    }
}
