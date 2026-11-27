package app.focusmodeplus.core.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.domain.ai.GenerationError
import app.focusmodeplus.core.domain.ai.ImageSniffer
import app.focusmodeplus.core.domain.ai.MaterialPreparation
import app.focusmodeplus.core.domain.ai.MaterialRules
import app.focusmodeplus.core.network.attachment.AndroidMissionMaterialStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * v22 adversarial tests of the one draft-photo pipeline. Every image is generated at test time
 * (no personal photos, no committed binary corpora). The pipeline must never crash, decode
 * only bounded images, keep no metadata, use neutral names and leave no temporary files.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecurityV22AttachmentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.noBackupFilesDir, "mission_material_v22").apply { deleteRecursively() }
    private val store = AndroidMissionMaterialStore(context.contentResolver, dir)

    @After fun tearDown() { dir.deleteRecursively() }

    private fun encode(w: Int, h: Int, format: Bitmap.CompressFormat, quality: Int = 90): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until h step 9) for (x in 0 until w step 7) setPixel(x, y, Color.rgb((x * 13) % 255, (y * 29) % 255, 128))
        }
        return ByteArrayOutputStream().also { bmp.compress(format, quality, it) }.toByteArray()
    }

    private fun rejected(bytes: ByteArray, mime: String?) = runBlocking { store.process(bytes, mime) } as? MaterialPreparation.Rejected
        ?: error("expected a rejection")

    /** Inserts an APP1 segment right after the JPEG SOI marker. */
    private fun withApp1(jpeg: ByteArray, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
    }

    /** A little-endian Exif block with an orientation tag, a GPS IFD pointer and a recognizable marker. */
    private fun exifWithGps(marker: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("Exif".toByteArray()); out.write(byteArrayOf(0, 0))
        val tiff = ByteArrayOutputStream()
        fun u16(v: Int) { tiff.write(v and 0xFF); tiff.write((v shr 8) and 0xFF) }
        fun u32(v: Int) { u16(v and 0xFFFF); u16((v shr 16) and 0xFFFF) }
        tiff.write("II".toByteArray()); u16(42); u32(8)
        u16(2) // two IFD0 entries
        u16(0x0112); u16(3); u32(1); u16(6); u16(0) // orientation = 6
        u16(0x8825); u16(4); u32(1); u32(38) // GPS IFD pointer
        u32(0)
        u16(1); u16(0x0002); u16(2); u32(marker.length + 1); u32(56); u32(0) // GPSLatitude as ASCII at offset 56
        while (tiff.size() < 56) tiff.write(0)
        tiff.write(marker.toByteArray()); tiff.write(0)
        out.write(tiff.toByteArray())
        return out.toByteArray()
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean =
        (0..size - needle.size).any { i -> needle.indices.all { j -> this[i + j] == needle[j] } }

    // ------------------------------------------------------------------ formats and signatures

    @Test fun validJpegPngAndWebpAreAcceptedAndReencoded() = runBlocking {
        for ((format, mime) in listOf(Bitmap.CompressFormat.JPEG to "image/jpeg", Bitmap.CompressFormat.PNG to "image/png", Bitmap.CompressFormat.WEBP_LOSSY to "image/webp")) {
            val r = store.process(encode(400, 300, format), mime)
            assertTrue("$mime: $r", r is MaterialPreparation.Ready)
            val out = store.load()!!
            assertTrue(out.mimeType == "image/jpeg" || out.mimeType == "image/png")
            assertTrue(out.uploadName == "material.jpg" || out.uploadName == "material.png")
        }
    }

    @Test fun signatureAndDeclaredTypeMustAgree() {
        val png = encode(60, 40, Bitmap.CompressFormat.PNG)
        val jpeg = encode(60, 40, Bitmap.CompressFormat.JPEG)
        assertEquals(GenerationError.UnsupportedFile, rejected(png, "image/jpeg").error)
        assertEquals(GenerationError.UnsupportedFile, rejected(jpeg, "image/png").error)
        assertEquals(GenerationError.UnsupportedFile, rejected("Hallo, ich bin kein Bild <script>".toByteArray(), "image/jpeg").error)
        assertEquals(GenerationError.UnsupportedFile, rejected("%PDF-1.7\n".toByteArray() + ByteArray(64), "application/pdf").error)
        // GIF, BMP and HEIC are not accepted, whatever they declare.
        assertEquals(GenerationError.UnsupportedFile, rejected("GIF89a".toByteArray() + ByteArray(64), "image/gif").error)
        assertEquals(GenerationError.UnsupportedFile, rejected("BM".toByteArray() + ByteArray(64), "image/bmp").error)
        assertEquals(GenerationError.UnsupportedFile, rejected(byteArrayOf(0, 0, 0, 0x18) + "ftypheic".toByteArray() + ByteArray(64), "image/heic").error)
        // An executable disguised by name only.
        assertEquals(GenerationError.UnsupportedFile, rejected(byteArrayOf(0x7F, 0x45, 0x4C, 0x46) + ByteArray(64), "image/jpeg").error)
    }

    @Test fun truncatedAndCorruptImagesNeverCrash() = runBlocking {
        val jpeg = encode(800, 600, Bitmap.CompressFormat.JPEG)
        for (cut in listOf(3, 12, 64, 200, jpeg.size / 3, jpeg.size / 2, jpeg.size - 10)) {
            val r = store.process(jpeg.copyOf(cut), "image/jpeg")
            assertTrue("cut=$cut", r is MaterialPreparation.Ready || r is MaterialPreparation.Rejected)
        }
        val corrupt = jpeg.copyOf().also { for (i in 200 until it.size step 11) it[i] = 0x5A }
        val r = store.process(corrupt, "image/jpeg")
        assertTrue(r is MaterialPreparation.Ready || r is MaterialPreparation.Rejected)
    }

    @Test fun malformedExifIsIgnoredSafely() = runBlocking {
        val jpeg = encode(120, 90, Bitmap.CompressFormat.JPEG)
        val hostile = listOf(
            "Exif".toByteArray() + byteArrayOf(0, 0, 0x49, 0x49, 42, 0, -1, -1, -1, 0x7F), // IFD offset beyond the file
            "Exif".toByteArray() + byteArrayOf(0, 0, 0x4D, 0x4D, 0, 42, 0, 0, 0, 8, -1, -1), // 65 535 entries claimed
            "Exif".toByteArray() + byteArrayOf(0, 0, 0x58, 0x58), // neither II nor MM
            ByteArray(3),
        )
        for (payload in hostile) {
            val bytes = withApp1(jpeg, payload)
            val header = ImageSniffer.sniff(bytes)!!
            assertEquals(1, header.exifOrientation)
            assertTrue(store.process(bytes, "image/jpeg") is MaterialPreparation.Ready)
        }
    }

    @Test fun exifGpsAndCameraMetadataNeverSurviveProcessing() = runBlocking {
        val marker = "GPS-48.137154N-11.576124E"
        val source = withApp1(encode(640, 480, Bitmap.CompressFormat.JPEG), exifWithGps(marker))
        assertTrue(source.contains(marker.toByteArray()))
        assertEquals(6, ImageSniffer.sniff(source)!!.exifOrientation)
        val r = store.process(source, "image/jpeg") as MaterialPreparation.Ready
        // The rotation was applied to the pixels; the metadata itself is gone.
        assertEquals(480, r.attachment.width)
        assertEquals(640, r.attachment.height)
        val out = store.load()!!.bytes
        assertFalse(out.contains(marker.toByteArray()))
        assertFalse(out.contains("Exif".toByteArray() + byteArrayOf(0, 0)))
        assertEquals(1, ImageSniffer.sniff(out)!!.exifOrientation)
    }

    // ------------------------------------------------------------------ size and dimensions

    @Test fun decompressionBombDimensionsAreRefusedFromTheHeader() {
        // JPEG SOF0 declaring 65 000 × 65 000, PNG IHDR 50 000 × 1, WebP VP8X 16 384 × 16 384 — all a few bytes.
        val sof = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte(), 0, 17, 8, 0xFD.toByte(), 0xE8.toByte(), 0xFD.toByte(), 0xE8.toByte(), 3) + ByteArray(64)
        assertEquals(GenerationError.FileTooLarge, rejected(sof, "image/jpeg").error)
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0xC3.toByte(), 0x50, 0, 0, 0, 1, 8, 6, 0, 0, 0)
        assertEquals(GenerationError.FileTooLarge, rejected(png, "image/png").error)
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8X".toByteArray() + ByteArray(8) + byteArrayOf(-1, 0x3F, 0, -1, 0x3F, 0) + ByteArray(32)
        assertEquals(GenerationError.FileTooLarge, rejected(webp, "image/webp").error)
        assertTrue(MaterialRules.sizeAcceptable(12_000, 3_000, allowUnknown = false))
        assertFalse(MaterialRules.sizeAcceptable(8_000, 8_000, allowUnknown = false)) // 64 MP > 40 MP
    }

    @Test fun anOversizedOriginalIsRefusedWithoutDecoding() {
        val big = ByteArray((MaterialRules.MAX_ORIGINAL_BYTES + 1).toInt()).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }
        val r = rejected(big, "image/jpeg")
        assertEquals(GenerationError.FileTooLarge, r.error)
        assertTrue(r.message.contains("20 MB"))
    }

    // ------------------------------------------------------------------ uris and names

    @Test fun onlyReadableContentUrisAreOpened() = runBlocking {
        for (source in listOf("file:///data/data/app.focusmodeplus/databases/focus.db", "http://example.invalid/a.jpg", "javascript:alert(1)", "", "content", "\u0000")) {
            val r = store.prepare(source) as MaterialPreparation.Rejected
            assertEquals(source, GenerationError.ImageProcessingFailed, r.error)
        }
        // A provider that does not answer (no stream, no type) is a read failure, never a crash.
        val r = store.prepare("content://com.example.gone/document/1") as MaterialPreparation.Rejected
        assertEquals(GenerationError.ImageProcessingFailed, r.error)
    }

    /** A document provider (another installed app, chosen in the system picker) that fails on purpose. */
    class HostileProvider : android.content.ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String = throw IllegalStateException("hostile_type")
        override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor = when (uri.lastPathSegment) {
            "state" -> throw IllegalStateException("hostile")
            "argument" -> throw IllegalArgumentException("hostile")
            "unsupported" -> throw UnsupportedOperationException("hostile")
            else -> throw NullPointerException("hostile")
        }
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): android.database.Cursor? = null
        override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: android.content.ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    /** V22-F08: exceptions a provider can send across Binder used to escape prepare() and crash the app. */
    @Test fun aHostileDocumentProviderCannotCrashTheApp() = runBlocking {
        org.robolectric.Robolectric.setupContentProvider(HostileProvider::class.java, "com.example.hostile")
        for (kind in listOf("state", "argument", "unsupported", "null")) {
            val r = store.prepare("content://com.example.hostile/document/$kind")
            assertTrue("$kind: $r", r is MaterialPreparation.Rejected)
            assertEquals(GenerationError.ImageProcessingFailed, (r as MaterialPreparation.Rejected).error)
        }
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun hostileFileNamesNeverReachStorageOrTheUpload() = runBlocking {
        val uri = Uri.parse("content://com.example.docs/document/..%2F..%2Fshared_prefs%2Fx.xml%00.jpg?name=%1B%5B31m.jpg")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(encode(300, 200, Bitmap.CompressFormat.JPEG)))
        // A provider that reports no type and no size still works through the signature.
        assertTrue(store.prepare(uri.toString()) is MaterialPreparation.Ready)
        val names = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("draft.jpg"), names)
        assertEquals("material.jpg", store.load()!!.uploadName)
        assertTrue(File(context.filesDir.parentFile, "shared_prefs/x.xml").exists().not())
    }

    // ------------------------------------------------------------------ cleanup and cancellation

    @Test fun noTemporaryFileSurvivesAndClearRemovesEverything() = runBlocking {
        store.process(encode(500, 500, Bitmap.CompressFormat.PNG), "image/png")
        store.process(encode(500, 500, Bitmap.CompressFormat.JPEG), "image/jpeg")
        assertEquals(listOf("draft.jpg"), dir.listFiles()!!.map { it.name }) // the newer draft replaced the older one
        store.clear()
        assertTrue(dir.listFiles().isNullOrEmpty())
        assertNull(store.load())
    }

    @Test fun cancellationWhileReadingWritesNothing() = runBlocking {
        val uri = Uri.parse("content://com.example.docs/document/slow")
        lateinit var job: Job
        val source = encode(900, 700, Bitmap.CompressFormat.JPEG)
        val cancelling = object : InputStream() {
            private val inner = ByteArrayInputStream(source)
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                job.cancel() // the user leaves the screen while the provider is still delivering
                return inner.read(b, off, len)
            }
        }
        shadowOf(context.contentResolver).registerInputStream(uri, cancelling)
        coroutineScope {
            job = launch(start = CoroutineStart.LAZY) { store.prepare(uri.toString()) }
            job.join()
        }
        assertTrue(job.isCancelled)
        assertTrue(dir.listFiles().isNullOrEmpty())
        assertNull(store.load())
    }
}
