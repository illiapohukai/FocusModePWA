package io.github.illiapohukai.focusmode.core.domain.ai

/** Optional study-material photo bounds (PWA `ai-service.js` v17.17). */
object MaterialRules {
    val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")
    /** One image per Mission (PWA `MISSION_IMAGE_MAX_ATTACHMENTS`). */
    const val MAX_ATTACHMENTS = 1
    /** Largest original accepted for processing (20 MB). */
    const val MAX_ORIGINAL_BYTES = 20L * 1024 * 1024
    /** Largest re-encoded upload (~1.5 MB). */
    const val MAX_PROCESSED_BYTES = MissionMaterial.MAX_BYTES
    const val OPTIMIZE_THRESHOLD_BYTES = 350_000L
    /** Long side of the upload. */
    const val MAX_DIMENSION = 1800
    /** Header-declared bounds checked before decoding (decompression-bomb guard). */
    const val MAX_SOURCE_SIDE = 12_000
    const val MAX_SOURCE_PIXELS = 40_000_000L
    /** Bytes of the file head inspected for the format and dimensions (JPEG SOF can follow EXIF). */
    const val HEAD_BYTES = 262_144

    fun sizeAcceptable(width: Int, height: Int, allowUnknown: Boolean): Boolean {
        if (width <= 0 || height <= 0) return allowUnknown
        return width <= MAX_SOURCE_SIDE && height <= MAX_SOURCE_SIDE && width.toLong() * height <= MAX_SOURCE_PIXELS
    }
}

/** Format and declared size read from the file signature, without decoding pixels. */
data class ImageHeader(val mime: String, val width: Int, val height: Int, val exifOrientation: Int = 1)

/** Pure port of the PWA `sniffImageHeader`, plus the JPEG EXIF orientation tag. */
object ImageSniffer {
    private fun u16be(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun u32be(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)
    private fun u16le(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun u24le(b: ByteArray, i: Int) = u16le(b, i) or ((b[i + 2].toInt() and 0xFF) shl 16)
    private fun at(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    fun sniff(b: ByteArray): ImageHeader? {
        if (b.size < 12) return null
        // PNG: 89 50 4E 47 0D 0A 1A 0A, IHDR width/height at 16/20
        if (at(b, 0) == 0x89 && at(b, 1) == 0x50 && at(b, 2) == 0x4E && at(b, 3) == 0x47 && at(b, 4) == 0x0D && at(b, 5) == 0x0A && at(b, 6) == 0x1A && at(b, 7) == 0x0A) {
            if (b.size < 24) return ImageHeader("image/png", 0, 0)
            return ImageHeader("image/png", u32be(b, 16).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), u32be(b, 20).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        // WebP: "RIFF" .... "WEBP"
        if (at(b, 0) == 0x52 && at(b, 1) == 0x49 && at(b, 2) == 0x46 && at(b, 3) == 0x46 && at(b, 8) == 0x57 && at(b, 9) == 0x45 && at(b, 10) == 0x42 && at(b, 11) == 0x50) {
            var w = 0
            var h = 0
            val chunk = if (b.size >= 16) String(byteArrayOf(b[12], b[13], b[14], b[15]), Charsets.US_ASCII) else ""
            if (chunk == "VP8X" && b.size >= 30) { w = u24le(b, 24) + 1; h = u24le(b, 27) + 1 }
            else if (chunk == "VP8 " && b.size >= 30) { w = u16le(b, 26) and 0x3fff; h = u16le(b, 28) and 0x3fff }
            else if (chunk == "VP8L" && b.size >= 25) {
                val bits = at(b, 21) or (at(b, 22) shl 8) or (at(b, 23) shl 16) or (at(b, 24) shl 24)
                w = (bits and 0x3fff) + 1; h = ((bits shr 14) and 0x3fff) + 1
            }
            return ImageHeader("image/webp", w, h)
        }
        // JPEG: FF D8 FF, then scan segments for a SOFn marker (and APP1 EXIF orientation)
        if (at(b, 0) == 0xFF && at(b, 1) == 0xD8 && at(b, 2) == 0xFF) {
            var orientation = 1
            var i = 2
            while (i + 9 < b.size) {
                if (at(b, i) != 0xFF) { i += 1; continue }
                val marker = at(b, i + 1)
                if (marker == 0xFF) { i += 1; continue }
                if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
                val len = u16be(b, i + 2)
                if (len < 2) break
                if (marker == 0xE1) orientation = exifOrientation(b, i + 4, len - 2) ?: orientation
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    return ImageHeader("image/jpeg", u16be(b, i + 7), u16be(b, i + 5), orientation)
                }
                i += 2 + len
            }
            return ImageHeader("image/jpeg", 0, 0, orientation)
        }
        return null
    }

    /** Reads tag 0x0112 from an APP1 "Exif\0\0" TIFF block; null when absent or malformed. */
    internal fun exifOrientation(b: ByteArray, start: Int, length: Int): Int? {
        val end = minOf(b.size, start + length)
        if (end - start < 14) return null
        if (!(at(b, start) == 0x45 && at(b, start + 1) == 0x78 && at(b, start + 2) == 0x69 && at(b, start + 3) == 0x66)) return null
        val tiff = start + 6
        val little = at(b, tiff) == 0x49 && at(b, tiff + 1) == 0x49
        val big = at(b, tiff) == 0x4D && at(b, tiff + 1) == 0x4D
        if (!little && !big) return null
        fun u16(i: Int) = if (little) u16le(b, i) else u16be(b, i)
        fun u32(i: Int): Long = if (little) (u16le(b, i).toLong() or (u16le(b, i + 2).toLong() shl 16)) else u32be(b, i)
        if (tiff + 8 > end) return null
        val ifd = tiff + u32(tiff + 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (ifd < tiff || ifd + 2 > end) return null
        val count = u16(ifd)
        for (n in 0 until count.coerceAtMost(64)) {
            val entry = ifd + 2 + n * 12
            if (entry + 12 > end) return null
            if (u16(entry) == 0x0112) return u16(entry + 8).takeIf { it in 1..8 }
        }
        return null
    }
}
