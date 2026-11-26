package io.github.illiapohukai.focusmode.core.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.ai.ImageSniffer
import io.github.illiapohukai.focusmode.core.domain.ai.MaterialPreparation
import io.github.illiapohukai.focusmode.core.domain.ai.MaterialRules
import io.github.illiapohukai.focusmode.core.network.attachment.AndroidMissionMaterialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidMissionMaterialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.noBackupFilesDir, "mission_material_test")
    private val store = AndroidMissionMaterialStore(context.contentResolver, dir)

    private fun encode(w: Int, h: Int, format: Bitmap.CompressFormat, quality: Int = 95): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until h step 7) for (x in 0 until w step 5) setPixel(x, y, Color.rgb((x * 31) % 255, (y * 17) % 255, (x + y) % 255))
        }
        return ByteArrayOutputStream().also { bmp.compress(format, quality, it) }.toByteArray()
    }

    @Test fun smallPngStaysLosslessAndIsStoredForUpload() = runBlocking {
        val r = store.process(encode(320, 200, Bitmap.CompressFormat.PNG), "image/png") as MaterialPreparation.Ready
        assertEquals("image/png", r.attachment.mime)
        assertEquals(320, r.attachment.width)
        val loaded = store.load()!!
        assertEquals("image/png", loaded.mimeType)
        assertEquals("material.png", loaded.uploadName)
        assertEquals(r.attachment.byteCount, loaded.bytes.size)
        store.clear()
        assertNull(store.load())
    }

    @Test fun largePhotosAreDownscaledReencodedAndStripped() = runBlocking {
        val r = store.process(encode(3600, 2400, Bitmap.CompressFormat.JPEG), "image/jpeg") as MaterialPreparation.Ready
        assertEquals("image/jpeg", r.attachment.mime)
        assertTrue(maxOf(r.attachment.width, r.attachment.height) <= MaterialRules.MAX_DIMENSION)
        assertTrue(r.attachment.byteCount <= MaterialRules.MAX_PROCESSED_BYTES)
        val bytes = store.load()!!.bytes
        assertEquals(1, ImageSniffer.sniff(bytes)!!.exifOrientation) // no EXIF block survives re-encoding
        assertTrue(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null)
    }

    @Test fun contentThatDoesNotMatchTheDeclaredTypeIsRejected() = runBlocking {
        val png = encode(40, 40, Bitmap.CompressFormat.PNG)
        val r = store.process(png, "image/jpeg") as MaterialPreparation.Rejected
        assertEquals(GenerationError.UnsupportedFile, r.error)
        assertTrue(r.message.startsWith("Der Dateiinhalt passt nicht"))
        assertNull(store.load())
    }

    @Test fun unsupportedEmptyAndHugeInputsAreRejectedBeforeDecoding() = runBlocking {
        assertEquals(GenerationError.UnsupportedFile, (store.process("GIF89a\u0001\u0000".toByteArray(), "image/gif") as MaterialPreparation.Rejected).error)
        assertEquals(GenerationError.ImageProcessingFailed, (store.process(ByteArray(0), "image/png") as MaterialPreparation.Rejected).error)
        // A tiny file that declares 20 000 × 20 000 px is refused from its header alone.
        val bomb = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0x4E, 0x20, 0, 0, 0x4E, 0x20, 8, 6, 0, 0, 0)
        val r = store.process(bomb, "image/png") as MaterialPreparation.Rejected
        assertEquals(GenerationError.FileTooLarge, r.error)
        assertTrue(r.message.contains("Bildauflösung"))
    }

    @Test fun onlyContentUrisFromThePickerAreRead() = runBlocking {
        val r = store.prepare("file:///sdcard/secret.jpg") as MaterialPreparation.Rejected
        assertEquals(GenerationError.ImageProcessingFailed, r.error)
    }
}
