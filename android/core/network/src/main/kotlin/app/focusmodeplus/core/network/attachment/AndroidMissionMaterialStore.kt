package app.focusmodeplus.core.network.attachment

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import app.focusmodeplus.core.domain.ai.GenerationError
import app.focusmodeplus.core.domain.ai.ImageSniffer
import app.focusmodeplus.core.domain.ai.MaterialPreparation
import app.focusmodeplus.core.domain.ai.MaterialRules
import app.focusmodeplus.core.domain.ai.MissionMaterial
import app.focusmodeplus.core.domain.ai.MissionMaterialStore
import app.focusmodeplus.core.domain.mission.DraftAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The one draft-photo owner (PWA `handleMissionFile` + `compressMissionImage`). Reads only the
 * `content://` Uri the system photo picker granted (no storage permission), checks size, real
 * format and declared dimensions before decoding, decodes down-sampled, applies the EXIF
 * rotation, re-encodes (which drops EXIF/GPS/camera metadata) and keeps only the processed bytes
 * in no-backup app storage until the Mission is accepted or the draft is discarded. Nothing is
 * logged and nothing is uploaded here.
 */
@Singleton
class AndroidMissionMaterialStore internal constructor(
    private val resolver: ContentResolver,
    private val dir: File,
    private val capture: CaptureFiles? = null,
) : MissionMaterialStore {

    /** The system-camera target: one file in [dir] and its FileProvider Uri. */
    internal class CaptureFiles(val dir: File, val toUri: (File) -> Uri)

    @Inject constructor(@ApplicationContext context: Context) : this(
        context.contentResolver,
        File(context.noBackupFilesDir, "mission_material"),
        CaptureFiles(File(context.cacheDir, "material_capture")) { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".materialcapture", it) },
    )

    private val captureFile get() = capture?.let { File(it.dir, "capture.jpg") }

    override suspend fun captureTarget(): String? = lock.withLock {
        withContext(Dispatchers.IO) {
            val c = capture ?: return@withContext null
            val file = captureFile ?: return@withContext null
            runCatching {
                if (!c.dir.isDirectory && !c.dir.mkdirs()) return@withContext null
                file.delete()
                file.createNewFile()
                c.toUri(file).toString()
            }.getOrNull()
        }
    }

    private val lock = Mutex()
    private val jpeg get() = File(dir, "draft.jpg")
    private val png get() = File(dir, "draft.png")

    override suspend fun prepare(source: String): MaterialPreparation = lock.withLock {
        withContext(Dispatchers.IO) {
            val uri = runCatching { source.toUri() }.getOrNull()
            if (uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT) return@withContext reject(GenerationError.ImageProcessingFailed, READ_FAILED)
            val declared = runCatching { resolver.getType(uri) }.getOrNull()
            val bytes = try {
                resolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MaterialRules.MAX_ORIGINAL_BYTES) return@withContext reject(GenerationError.FileTooLarge, ORIGINAL_TOO_LARGE)
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } ?: return@withContext reject(GenerationError.ImageProcessingFailed, READ_FAILED)
            } catch (_: SecurityException) {
                return@withContext reject(GenerationError.ImageProcessingFailed, READ_FAILED)
            } catch (_: IOException) {
                return@withContext reject(GenerationError.ImageProcessingFailed, READ_FAILED)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                // v22 (V22-F08): the document provider is another app. Across Binder it can throw
                // IllegalState/IllegalArgument/UnsupportedOperation/NullPointer exceptions, which
                // used to escape and crash the app; any of them is an unreadable photo.
                return@withContext reject(GenerationError.ImageProcessingFailed, READ_FAILED)
            }
            process(bytes, declared)
        }.also {
            // The camera original carries EXIF/GPS metadata: it never outlives its processing.
            val c = capture
            val file = captureFile
            if (c != null && file != null && runCatching { c.toUri(file).toString() == source }.getOrDefault(false)) withContext(Dispatchers.IO) { file.delete() }
        }
    }

    /** The checks and re-encoding, separated from the Uri so they can be tested directly. */
    internal suspend fun process(bytes: ByteArray, declaredMime: String?): MaterialPreparation = withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) return@withContext reject(GenerationError.ImageProcessingFailed, EMPTY)
        if (bytes.size > MaterialRules.MAX_ORIGINAL_BYTES) return@withContext reject(GenerationError.FileTooLarge, ORIGINAL_TOO_LARGE)
        val declared = declaredMime?.lowercase()?.trim()?.let { if (it == "image/jpg") "image/jpeg" else it }
        if (declared != null && declared != "application/octet-stream" && declared !in MaterialRules.ALLOWED_MIME) {
            return@withContext reject(GenerationError.UnsupportedFile, UNSUPPORTED)
        }
        val header = ImageSniffer.sniff(bytes.copyOf(minOf(bytes.size, MaterialRules.HEAD_BYTES)))
        if (header == null || (declared in MaterialRules.ALLOWED_MIME && header.mime != declared)) {
            return@withContext reject(GenerationError.UnsupportedFile, CONTENT_MISMATCH)
        }
        if (!MaterialRules.sizeAcceptable(header.width, header.height, allowUnknown = true)) return@withContext reject(GenerationError.FileTooLarge, RESOLUTION)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return@withContext reject(GenerationError.ImageProcessingFailed, PROCESSING)
        if (!MaterialRules.sizeAcceptable(srcW, srcH, allowUnknown = false)) return@withContext reject(GenerationError.FileTooLarge, RESOLUTION)

        val longSide = max(srcW, srcH)
        var sample = 1
        while (longSide / (sample * 2) >= MaterialRules.MAX_DIMENSION) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return@withContext reject(GenerationError.ImageProcessingFailed, PROCESSING)

        val upright = orient(decoded, header.exifOrientation)
        try {
            val needsResize = longSide > MaterialRules.MAX_DIMENSION
            val needsCompress = bytes.size > MaterialRules.OPTIMIZE_THRESHOLD_BYTES
            val keepPng = header.mime == "image/png" && !needsResize && !needsCompress
            val attempts = if (!needsResize && !needsCompress) listOf(max(upright.width, upright.height) to 92, max(upright.width, upright.height) to 84)
            else listOf(MaterialRules.MAX_DIMENSION to 84, 1600 to 76)
            var best: Triple<ByteArray, Int, Int>? = null
            var mime = "image/jpeg"
            for ((index, attempt) in attempts.withIndex()) {
                val (maxSide, quality) = attempt
                val scaled = scaleToFit(upright, maxSide)
                val png = keepPng && index == 0
                val out = encode(scaled, png, quality)
                if (scaled !== upright) scaled.recycle()
                best = Triple(out.first, out.second, out.third)
                mime = if (png) "image/png" else "image/jpeg"
                if (out.first.size <= MaterialRules.MAX_PROCESSED_BYTES) break
            }
            val (data, w, h) = best ?: return@withContext reject(GenerationError.ImageProcessingFailed, PROCESSING)
            if (data.isEmpty()) return@withContext reject(GenerationError.ImageProcessingFailed, PROCESSING)
            if (data.size > MaterialRules.MAX_PROCESSED_BYTES) return@withContext reject(GenerationError.FileTooLarge, PROCESSED_TOO_LARGE)
            withContext(Dispatchers.IO) { write(data, mime) }
            MaterialPreparation.Ready(DraftAttachment(data.size, mime, w, h))
        } catch (_: IOException) {
            reject(GenerationError.ImageProcessingFailed, PROCESSING)
        } catch (_: OutOfMemoryError) {
            reject(GenerationError.ImageProcessingFailed, PROCESSING)
        } finally {
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
        }
    }

    override suspend fun load(): MissionMaterial? = lock.withLock {
        withContext(Dispatchers.IO) {
            val (file, mime) = when {
                jpeg.isFile -> jpeg to "image/jpeg"
                png.isFile -> png to "image/png"
                else -> return@withContext null
            }
            val data = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
            MissionMaterial(data, mime).takeIf { it.validationError() == null }
        }
    }

    override suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.delete() }
            capture?.dir?.listFiles()?.forEach { it.delete() }
            Unit
        }
    }

    private fun write(data: ByteArray, mime: String) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("material_dir")
        val target = if (mime == "image/png") png else jpeg
        val other = if (mime == "image/png") jpeg else png
        val tmp = File(dir, "draft.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(target)) { tmp.delete(); throw IOException("material_write") }
        other.delete()
    }

    /** EXIF orientation 1–8 applied to the pixels, because the re-encoded file has no EXIF any more. */
    private fun orient(src: Bitmap, orientation: Int): Bitmap {
        if (orientation <= 1 || orientation > 8) return src
        val m = Matrix()
        when (orientation) {
            2 -> m.setScale(-1f, 1f)
            3 -> m.setRotate(180f)
            4 -> m.setScale(1f, -1f)
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.setRotate(-90f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun scaleToFit(src: Bitmap, maxSide: Int): Bitmap {
        val long = max(src.width, src.height)
        if (long <= maxSide) return src
        val scale = maxSide.toFloat() / long
        return src.scale(max(1, (src.width * scale).roundToInt()), max(1, (src.height * scale).roundToInt()), filter = true)
    }

    /** PWA canvas `alpha: false` with a white fill: transparent areas never turn black in JPEG. */
    private fun encode(src: Bitmap, png: Boolean, quality: Int): Triple<ByteArray, Int, Int> {
        val out = ByteArrayOutputStream()
        if (png) {
            src.compress(Bitmap.CompressFormat.PNG, 100, out)
            return Triple(out.toByteArray(), src.width, src.height)
        }
        val opaque = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            opaque.compress(Bitmap.CompressFormat.JPEG, quality, out)
        } finally {
            opaque.recycle()
        }
        return Triple(out.toByteArray(), src.width, src.height)
    }

    private fun reject(error: GenerationError, message: String) = MaterialPreparation.Rejected(error, message)

    internal companion object {
        const val READ_FAILED = "Das Bild konnte nicht gelesen werden. Bitte wähle ein anderes Foto."
        const val EMPTY = "Die ausgewählte Bilddatei ist leer. Bitte wähle ein anderes Foto aus."
        const val ORIGINAL_TOO_LARGE = "Das Bild ist zu groß (maximal 20 MB). Bitte wähle ein kleineres Foto aus."
        const val UNSUPPORTED = "Nicht unterstütztes Dateiformat. Bitte wähle ein JPEG-, PNG- oder WEBP-Bild aus."
        const val CONTENT_MISMATCH = "Der Dateiinhalt passt nicht zu einem JPEG-, PNG- oder WEBP-Bild. Bitte wähle ein anderes Foto."
        const val RESOLUTION = "Die Bildauflösung ist zu groß. Bitte wähle ein kleineres Foto oder einen Ausschnitt."
        const val PROCESSING = "Das Foto konnte nicht vorbereitet werden. Bitte versuche ein anderes Bild."
        const val PROCESSED_TOO_LARGE = "Das Bild ist auch nach der Komprimierung zu groß (max. 1,5 MB). Bitte wähle einen engeren Ausschnitt."
    }
}
