package app.focusmodeplus.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

/** Reads a Worker response body with a hard size cap, so a runaway answer cannot exhaust memory. */
internal object WorkerBodies {
    /** Plans and adaptations are a few kilobytes; anything far larger is not a valid answer. */
    const val MAX_BODY_BYTES = 512L * 1024

    fun read(response: Response<ResponseBody>): String? {
        val body = response.body() ?: response.errorBody() ?: return null
        return body.use { b ->
            val source = b.source()
            if (source.request(MAX_BODY_BYTES + 1)) throw IOException("body_too_large")
            source.buffer.readUtf8()
        }
    }
}
