package io.github.illiapohukai.focusmode.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * The existing Cloudflare Worker contract (unchanged): `POST /create-mission` (multipart) and
 * `POST /adapt-mission` (JSON). Gemini is only ever called by the Worker; Android holds no key.
 * Bodies are read raw and validated here, so malformed AI output can never reach Room.
 */
interface WorkerApi {
    @Multipart
    @POST("create-mission")
    suspend fun createMission(@Part parts: List<MultipartBody.Part>): Response<ResponseBody>

    /** Foundation only in v18 (Mission review adaptation UI is not implemented yet). */
    @POST("adapt-mission")
    suspend fun adaptMission(@Body body: RequestBody): Response<ResponseBody>
}
