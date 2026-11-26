package com.example.network

import com.example.data.model.CreditsErrorSubCode
import com.example.data.model.OpenRouterAlertState
import org.json.JSONObject
import java.util.Locale

class OpenRouterApiException(
    val httpCode: Int,
    val alert: OpenRouterAlertState?,
    override val message: String,
) : IllegalStateException(message)

object OpenRouterErrorParser {

    /**
     * Parses OpenRouter HTTP error responses into typed human-readable alerts for
     * 402 (credits / key limit / in-flight budget), 403 (budget / guardrail),
     * and 429 (rate limit with X-RateLimit-Reset timestamp).
     */
    fun parse(
        httpCode: Int,
        responseBody: String,
        headers: Map<String, String> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
    ): OpenRouterApiException {
        val json = runCatching { JSONObject(responseBody) }.getOrNull()
        val errorObj = json?.optJSONObject("error")
        val rawMessage = errorObj?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: responseBody.trim().take(280)
        val metadata = errorObj?.optJSONObject("metadata")
        val typeOrCode = listOfNotNull(
            errorObj?.optString("type"),
            errorObj?.optString("code"),
            metadata?.optString("reason"),
            metadata?.optString("code"),
            metadata?.optString("limit_type"),
            metadata?.optString("error_type"),
        ).joinToString(" ").lowercase(Locale.US)
        val searchable = "$typeOrCode ${rawMessage.lowercase(Locale.US)} ${responseBody.lowercase(Locale.US)}"

        val normalizedHeaders = headers.entries.associate { (k, v) -> k.lowercase(Locale.US) to v.trim() }

        val alert: OpenRouterAlertState? = when (httpCode) {
            402 -> {
                val subCode = when {
                    searchable.contains("openrouter_in_flight_budget") ||
                        searchable.contains("in_flight_budget") ||
                        searchable.contains("in-flight") -> CreditsErrorSubCode.OPENROUTER_IN_FLIGHT_BUDGET
                    searchable.contains("openrouter_key_limit") ||
                        searchable.contains("key_limit") ||
                        searchable.contains("key limit") ||
                        searchable.contains("spending limit") -> CreditsErrorSubCode.OPENROUTER_KEY_LIMIT
                    searchable.contains("openrouter_credits") ||
                        searchable.contains("insufficient credits") ||
                        searchable.contains("credits") -> CreditsErrorSubCode.OPENROUTER_CREDITS
                    else -> CreditsErrorSubCode.GENERIC_PAYMENT_REQUIRED
                }
                val detail = when (subCode) {
                    CreditsErrorSubCode.OPENROUTER_CREDITS ->
                        "Your OpenRouter account balance is too low to complete this request. Top up credits on openrouter.ai or lower Max Output Tokens."
                    CreditsErrorSubCode.OPENROUTER_KEY_LIMIT ->
                        "This API key has reached its configured spending cap. Increase the key limit, wait for limit reset, or switch keys."
                    CreditsErrorSubCode.OPENROUTER_IN_FLIGHT_BUDGET ->
                        "Concurrent in-flight requests temporarily exhausted your remaining budget. Wait for active streams to finish and retry."
                    CreditsErrorSubCode.GENERIC_PAYMENT_REQUIRED ->
                        rawMessage.ifBlank { "OpenRouter returned HTTP 402 Payment Required. Check your account credits or API key limit." }
                }
                OpenRouterAlertState.InsufficientCredits402(
                    subCode = subCode,
                    headline = subCode.label,
                    detail = detail,
                    rawMessage = rawMessage,
                )
            }

            403 -> {
                val isGuardrail = searchable.contains("guardrail") ||
                    searchable.contains("budget") ||
                    searchable.contains("moderation") ||
                    searchable.contains("policy")
                val headline = if (isGuardrail) {
                    "Budget or Guardrail Restriction (403)"
                } else {
                    "Access Blocked by OpenRouter (403)"
                }
                val detail = rawMessage.ifBlank {
                    "This request was blocked by an OpenRouter workspace budget policy, model guardrail, or key permission rule."
                }
                OpenRouterAlertState.GuardrailForbidden403(
                    headline = headline,
                    detail = detail,
                    rawMessage = rawMessage,
                )
            }

            429 -> {
                val resetEpochMillis = parseRateLimitResetMillis(
                    resetHeader = normalizedHeaders["x-ratelimit-reset"]
                        ?: metadata?.optString("headers_x_ratelimit_reset")
                        ?: metadata?.optJSONObject("headers")?.optString("X-RateLimit-Reset")
                        ?: metadata?.optJSONObject("headers")?.optString("x-ratelimit-reset"),
                    retryAfterHeader = normalizedHeaders["retry-after"],
                    nowMillis = nowMillis,
                )
                val detail = rawMessage.ifBlank {
                    "Too many requests sent in the current window. Wait for the rate-limit timer to reset or pick a fallback model."
                }
                OpenRouterAlertState.RateLimited429(
                    headline = "OpenRouter Rate Limit Reached (429)",
                    detail = detail,
                    resetEpochMillis = resetEpochMillis,
                    rawMessage = rawMessage,
                )
            }

            else -> null
        }

        val summary = alert?.let { "${it.headline}: ${it.detail}" }
            ?: "OpenRouter API error ($httpCode): ${rawMessage.ifBlank { "Request failed" }}"
        return OpenRouterApiException(
            httpCode = httpCode,
            alert = alert,
            message = summary,
        )
    }

    /**
     * Parses `X-RateLimit-Reset` (supports Unix epoch milliseconds, Unix epoch seconds,
     * or small relative delta seconds) and falls back to `Retry-After` seconds.
     */
    fun parseRateLimitResetMillis(
        resetHeader: String?,
        retryAfterHeader: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val rawReset = resetHeader?.trim()?.takeIf { it.isNotEmpty() }
        if (rawReset != null) {
            val numeric = rawReset.toLongOrNull() ?: rawReset.toDoubleOrNull()?.toLong()
            if (numeric != null && numeric > 0L) {
                return when {
                    // Already epoch milliseconds (e.g. 1_730_000_000_000)
                    numeric >= 100_000_000_000L -> numeric
                    // Epoch seconds (e.g. 1_730_000_000)
                    numeric >= 1_000_000_000L -> numeric * 1_000L
                    // Relative delta seconds
                    else -> nowMillis + numeric * 1_000L
                }
            }
        }
        val rawRetry = retryAfterHeader?.trim()?.takeIf { it.isNotEmpty() }
        if (rawRetry != null) {
            val seconds = rawRetry.toLongOrNull()
            if (seconds != null && seconds > 0L) {
                return nowMillis + seconds * 1_000L
            }
        }
        return null
    }
}
