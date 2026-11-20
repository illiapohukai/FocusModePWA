package io.github.illiapohukai.focusmode.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * HTTPS-only client for the public Worker URL. No HTTP cache, no redirects (a POST body never
 * follows to another host), no automatic retry (a retry could create a duplicate generation),
 * no logging interceptor (no request or AI text in logs), no cookies.
 */
object WorkerClientFactory {
    fun create(baseUrl: String, requireHttps: Boolean = true): WorkerApi {
        require(baseUrl.endsWith("/")) { "worker_base_url_must_end_with_slash" }
        if (requireHttps) require(baseUrl.startsWith("https://")) { "worker_base_url_must_be_https" }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(70, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cache(null)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", "application/json")
                        .header("User-Agent", "FocusModePlus-Android/18")
                        .build(),
                )
            }
            .build()
        return Retrofit.Builder().baseUrl(baseUrl).client(client).build().create(WorkerApi::class.java)
    }
}
