package io.github.illiapohukai.focusmode.core.domain.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.security.MessageDigest

/** PWA `verifyBundleIntegrity` result. */
enum class IntegrityStatus(val label: String) {
    /** The digest matches: the file is unchanged since it was written. Says nothing about honesty. */
    VERIFIED("Backup geprüft (Datei unverändert)"),
    /** Older backup or a browser without Web Crypto: there is nothing to check. */
    ABSENT("Älteres Backup ohne Prüfsumme"),
    INVALID("Prüfsumme ungültig"),
    MISMATCH("Prüfsumme stimmt nicht"),
}

/**
 * Backup corruption detection, byte-compatible with the PWA (v17.17, `data-validator.js`):
 * SHA-256 over the canonical form "focusmode-canonical-json-v1" of
 * `{format, exportVersion, schemaVersions, scope, data}`. Canonical JSON sorts object keys by
 * UTF-16 code units, keeps array order, has no whitespace and formats numbers and strings exactly
 * like `JSON.stringify`. Anyone can recompute this digest: it proves a file is unchanged since it
 * was written, never who wrote it or that its History, streak or progress are honest.
 */
object BackupIntegrity {
    const val ALGORITHM = "SHA-256"
    const val CANONICALIZATION = "focusmode-canonical-json-v1"
    val COVERS = listOf("format", "exportVersion", "schemaVersions", "scope", "data")
    private val DIGEST = Regex("^[0-9a-f]{64}$")

    fun payload(bundle: JsonObject): JsonObject = buildJsonObject {
        COVERS.forEach { key -> put(key, bundle[key] ?: JsonNull) }
    }

    fun digest(bundle: JsonObject): String {
        val bytes = MessageDigest.getInstance(ALGORITHM).digest(canonical(payload(bundle)).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun verify(bundle: JsonObject): IntegrityStatus {
        val integrity = bundle["integrity"] as? JsonObject ?: return IntegrityStatus.ABSENT
        val digest = integrity["digest"]
        if (digest == null || digest is JsonNull || integrity.text("status") == "unavailable") return IntegrityStatus.ABSENT
        val value = (digest as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (integrity.text("algorithm") != ALGORITHM || integrity.text("canonicalization") != CANONICALIZATION || value == null || !DIGEST.matches(value)) {
            return IntegrityStatus.INVALID
        }
        return if (digest(bundle) == value) IntegrityStatus.VERIFIED else IntegrityStatus.MISMATCH
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    // ---------------------------------------------------------------- canonical JSON

    fun canonical(element: JsonElement): String = StringBuilder().also { write(element, it) }.toString()

    private fun write(e: JsonElement, out: StringBuilder) {
        when (e) {
            is JsonNull -> out.append("null")
            is JsonObject -> {
                out.append('{')
                e.keys.sorted().forEachIndexed { i, key ->
                    if (i > 0) out.append(',')
                    string(key, out)
                    out.append(':')
                    write(e.getValue(key), out)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                e.forEachIndexed { i, item -> if (i > 0) out.append(','); write(item, out) }
                out.append(']')
            }
            is JsonPrimitive -> when {
                e.isString -> string(e.content, out)
                e.content == "true" || e.content == "false" -> out.append(e.content)
                else -> out.append(number(e.content))
            }
        }
    }

    /** `JSON.stringify` string escaping (ES2019: lone surrogates as lowercase `\udxxx`). */
    private fun string(s: String, out: StringBuilder) {
        out.append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u").append("%04x".format(c.code))
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> { out.append(c).append(s[i + 1]); i++ }
                c.isSurrogate() -> out.append("\\u").append("%04x".format(c.code))
                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }

    /**
     * `Number.prototype.toString` formatting of a JSON number literal. The literal is read as an
     * exact decimal (no binary rounding), so a file written by `JSON.stringify` keeps its digits on
     * every platform: integers below 1e21 are plain, -6 < exponent ≤ 21 is plain decimal, anything
     * else uses `d.ddde±n`.
     */
    internal fun number(raw: String): String {
        val value = raw.toBigDecimalOrNull() ?: return "null"
        if (value.signum() == 0) return "0"
        val stripped = value.stripTrailingZeros()
        val digits = stripped.unscaledValue().abs().toString()
        val k = digits.length
        val n = k - stripped.scale() // value = 0.digits × 10^n
        val sign = if (stripped.signum() < 0) "-" else ""
        val body = when {
            n in k..21 -> digits + "0".repeat(n - k)
            n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
            n in -5..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val exp = n - 1
                (if (k == 1) digits else digits.substring(0, 1) + "." + digits.substring(1)) + "e" + (if (exp >= 0) "+" else "-") + kotlin.math.abs(exp)
            }
        }
        return sign + body
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
}
