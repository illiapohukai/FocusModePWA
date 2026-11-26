package io.github.illiapohukai.focusmode.core.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextSafetyTest {
    @Test fun markupScriptAndExecutableSchemesAreUnsafe() {
        listOf(
            "<script>alert(1)</script>", "Lies <b>Kapitel</b> 3", "<img src=x onerror=alert(1)>", "javascript:alert(1)",
            "Öffne data:text/html;base64,AAA", "Klick onload=steal()", "<!-- versteckt -->",
        ).forEach { assertTrue(it, AiTextSafety.isUnsafe(it)) }
    }

    @Test fun ordinaryStudyTextIsSafe() {
        listOf(
            "Prüfe, ob x < y und y > 3 gilt", "Kapitel 3: Daten & Fakten", "Lies https://de.wikipedia.org/wiki/Photosynthese", "Formel: a<b", "",
        ).forEach { assertFalse(it, AiTextSafety.isUnsafe(it)) }
    }

    @Test fun persistedErrorCodesRestoreSafely() {
        assertEquals(GenerationError.RateLimited, GenerationError.fromCode("AI_RATE_LIMITED"))
        assertEquals(GenerationError.Interrupted, GenerationError.fromCode("INTERRUPTED"))
        assertTrue(GenerationError.fromCode("SOMETHING_ELSE") is GenerationError.Unknown)
        assertNull(GenerationError.fromCode(null))
        assertFalse(GenerationError.QuotaRequired.retryable) // 402 billing unavailable is never retried
        assertFalse(GenerationError.Refused.retryable)
    }
}

class ImageSnifferTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun pngHeaderGivesTheDeclaredSize() {
        val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0x4E, 0x20, 0, 0, 0x4E, 0x20)
        assertEquals(ImageHeader("image/png", 20000, 20000), ImageSniffer.sniff(png))
        assertFalse(MaterialRules.sizeAcceptable(20000, 20000, allowUnknown = true)) // rejected before any decoding
    }

    @Test fun jpegSofAndExifOrientationAreRead() {
        val exif = bytes(
            0xFF, 0xD8, 0xFF, 0xE1, 0x00, 0x22, // APP1, length 34
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00, // "Exif\0\0"
            0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // big-endian TIFF, IFD at 8
            0x00, 0x01, // one entry
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00, // orientation = 6
            0x00, 0x00, 0x00, 0x00,
            0xFF, 0xC0, 0x00, 0x11, 0x08, 0x02, 0x58, 0x03, 0x20, 0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
        )
        val h = ImageSniffer.sniff(exif)!!
        assertEquals("image/jpeg", h.mime)
        assertEquals(800, h.width)
        assertEquals(600, h.height)
        assertEquals(6, h.exifOrientation)
    }

    @Test fun webpAndUnknownFormats() {
        val vp8x = ByteArray(30).also {
            "RIFF".toByteArray().copyInto(it, 0); "WEBP".toByteArray().copyInto(it, 8); "VP8X".toByteArray().copyInto(it, 12)
            it[24] = 0x7F; it[27] = 0x3F // 128 x 64
        }
        assertEquals(ImageHeader("image/webp", 128, 64), ImageSniffer.sniff(vp8x))
        assertNull(ImageSniffer.sniff("GIF89a......".toByteArray()))
        assertNull(ImageSniffer.sniff(ByteArray(4)))
    }
}
