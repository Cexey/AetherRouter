package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.Locale

data class FetchedWebpage(
    val url: String,
    val title: String,
    val readableText: String,
)

/**
 * Local HTTPS webpage reader fallback alongside OpenRouter's `openrouter:web_fetch`.
 * Enforces strict http/https scheme validation and blocks loopback, private, and link-local hosts.
 */
class LiveWebFetcher(private val okHttpClient: OkHttpClient) {

    companion object {
        private const val MAX_BYTES = 250_000
        private const val MAX_TEXT_CHARS = 18_000
        private val SCRIPT_STYLE_REGEX = Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>")
        private val TITLE_REGEX = Regex("(?is)<title[^>]*>(.*?)</title>")
        private val TAG_REGEX = Regex("(?is)<[^>]+>")
        private val MULTI_WS_REGEX = Regex("[ \\t\\x0B\\f\\r]+")
        private val MULTI_NL_REGEX = Regex("\\n{3,}")
    }

    fun isSafeExternalUrl(rawUrl: String): Boolean {
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return false
        val scheme = parsed.scheme.lowercase(Locale.US)
        if (scheme != "https" && scheme != "http") return false
        val host = parsed.host.lowercase(Locale.US)
        if (host.isBlank() || host == "localhost" || host.endsWith(".local")) return false
        return true
    }

    private fun isPublicHost(host: String): Boolean {
        return runCatching {
            val addresses = InetAddress.getAllByName(host)
            addresses.isNotEmpty() && addresses.all { addr ->
                !addr.isAnyLocalAddress &&
                    !addr.isLoopbackAddress &&
                    !addr.isLinkLocalAddress &&
                    !addr.isSiteLocalAddress &&
                    !addr.isMulticastAddress
            }
        }.getOrDefault(false)
    }

    suspend fun fetchPage(rawUrl: String): Result<FetchedWebpage> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = rawUrl.trim().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid URL")
            val scheme = parsed.scheme.lowercase(Locale.US)
            if (scheme != "https" && scheme != "http") {
                throw SecurityException("Only http and https URLs are allowed")
            }
            if (!isPublicHost(parsed.host)) {
                throw SecurityException("Private or local network addresses cannot be fetched")
            }

            val request = Request.Builder()
                .url(parsed)
                .header("User-Agent", "AetherRouter/1.0 (Android)")
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} fetching webpage")
                }
                val finalUrl = response.request.url
                if (!isPublicHost(finalUrl.host)) {
                    throw SecurityException("Redirect to private host blocked")
                }

                val bodyBytes = response.body?.source()?.let { source ->
                    source.request(MAX_BYTES.toLong())
                    val buffer = source.buffer
                    val readLen = minOf(buffer.size, MAX_BYTES.toLong())
                    buffer.clone().readByteArray(readLen)
                } ?: ByteArray(0)

                val rawHtml = String(bodyBytes, Charsets.UTF_8)
                val title = TITLE_REGEX.find(rawHtml)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::decodeBasicEntities)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: finalUrl.host

                val stripped = rawHtml
                    .replace(SCRIPT_STYLE_REGEX, " ")
                    .replace(Regex("(?i)</(p|div|li|tr|h[1-6]|br|section|article)>"), "\n")
                    .replace(TAG_REGEX, " ")
                    .let(::decodeBasicEntities)
                    .replace(MULTI_WS_REGEX, " ")
                    .replace(Regex(" *\\n *"), "\n")
                    .replace(MULTI_NL_REGEX, "\n\n")
                    .trim()
                    .take(MAX_TEXT_CHARS)

                FetchedWebpage(
                    url = finalUrl.toString(),
                    title = title,
                    readableText = stripped.ifBlank { "(No readable text extracted from page)" },
                )
            }
        }
    }

    private fun decodeBasicEntities(input: String): String =
        input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
}
