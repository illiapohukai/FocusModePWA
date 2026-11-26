package io.github.illiapohukai.focusmode.core.domain.ai

/**
 * AI output is untrusted data (prompt injection through user material is possible). Compose renders
 * text as plain text, so markup cannot execute on Android; a response that carries markup, script
 * or executable URL schemes is still rejected as a whole, because it is not a plan the user asked
 * for and it would be stored and later exported in backups.
 */
object AiTextSafety {
    /**
     * An HTML/XML tag (`<b>`, `</div>`, `<img …>`, `<!-- -->`). A tag name follows "<" directly,
     * so comparisons such as "x < y und y > 3" or "a<b" are not markup.
     */
    private val TAG = Regex("<(?:/)?[A-Za-z!][A-Za-z0-9:-]*(?:\\s[^<>]*)?>")
    /** Script schemes, and real data URLs (`data:text/html;…`), not the word "Data:". */
    private val SCHEME = Regex("(?i)\\b(?:javascript|vbscript|livescript)\\s*:|\\bdata:[a-z]+/[a-z0-9.+-]+[;,]")
    private val HANDLER = Regex("(?i)\\bon[a-z]{3,20}\\s*=")

    fun isUnsafe(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        return TAG.containsMatchIn(text) || SCHEME.containsMatchIn(text) || HANDLER.containsMatchIn(text)
    }
}
