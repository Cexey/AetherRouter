package com.example.data.repository

import com.example.data.model.OpenRouterActivityBreakdown
import com.example.data.model.OpenRouterActivityGroup
import com.example.data.model.OpenRouterActivityItem
import com.example.data.model.OpenRouterBudgetWindow
import com.example.data.model.OpenRouterUsageSnapshot
import com.example.network.OpenRouterErrorParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches live OpenRouter key usage/limits (`GET /api/v1/key`), account credit ledger
 * (`GET /api/v1/credits`), and Management Key 30-day activity breakdown (`GET /api/v1/activity`).
 *
 * Note: Read calls to `/api/v1/key` do not count toward key write rate limits.
 * In-memory caches are cleared immediately when Biometric Lock engages.
 */
class UsageRepository(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val KEY_URL = "https://openrouter.ai/api/v1/key"
        private const val AUTH_KEY_FALLBACK_URL = "https://openrouter.ai/api/v1/auth/key"
        private const val CREDITS_URL = "https://openrouter.ai/api/v1/credits"
        private const val ACTIVITY_URL = "https://openrouter.ai/api/v1/activity"

        internal fun parseKeyAndCreditsJson(
            keyBody: String,
            creditsBody: String? = null,
            nowMillis: Long = System.currentTimeMillis(),
        ): OpenRouterUsageSnapshot {
            val root = JSONObject(keyBody)
            val data = root.optJSONObject("data") ?: root

            val label = data.optString("label").orEmpty()
            val usage = data.optDoubleOrNull("usage") ?: 0.0
            val usageDaily = data.optDoubleOrNull("usage_daily") ?: 0.0
            val usageWeekly = data.optDoubleOrNull("usage_weekly") ?: 0.0
            val usageMonthly = data.optDoubleOrNull("usage_monthly") ?: 0.0
            val limit = data.optDoubleOrNull("limit")
            val limitRemaining = data.optDoubleOrNull("limit_remaining")
            val limitReset = data.optNullableString("limit_reset")
            val isFreeTier = data.optBoolean("is_free_tier", false)

            // Parse effective_budget (number or object with limit/remaining) and budgets array/map
            val effectiveBudgetObj = data.optJSONObject("effective_budget")
            val effectiveBudget = data.optDoubleOrNull("effective_budget")
                ?: effectiveBudgetObj?.optDoubleOrNull("limit")
                ?: effectiveBudgetObj?.optDoubleOrNull("amount")
                ?: effectiveBudgetObj?.optDoubleOrNull("max_usd")

            val parsedBudgets = mutableListOf<OpenRouterBudgetWindow>()
            if (effectiveBudgetObj != null) {
                parsedBudgets.add(
                    OpenRouterBudgetWindow(
                        name = effectiveBudgetObj.optNullableString("name") ?: "Effective Budget",
                        limitUsd = effectiveBudgetObj.optDoubleOrNull("limit")
                            ?: effectiveBudgetObj.optDoubleOrNull("amount"),
                        usageUsd = effectiveBudgetObj.optDoubleOrNull("usage")
                            ?: effectiveBudgetObj.optDoubleOrNull("spent"),
                        remainingUsd = effectiveBudgetObj.optDoubleOrNull("remaining")
                            ?: effectiveBudgetObj.optDoubleOrNull("limit_remaining"),
                        resetInterval = effectiveBudgetObj.optNullableString("reset")
                            ?: effectiveBudgetObj.optNullableString("limit_reset")
                            ?: effectiveBudgetObj.optNullableString("interval"),
                    ),
                )
            }

            val budgetsArray = data.optJSONArray("budgets")
            if (budgetsArray != null) {
                for (i in 0 until budgetsArray.length()) {
                    val b = budgetsArray.optJSONObject(i) ?: continue
                    parsedBudgets.add(
                        OpenRouterBudgetWindow(
                            name = b.optNullableString("name")
                                ?: b.optNullableString("scope")
                                ?: b.optNullableString("interval")
                                ?: "Budget #${i + 1}",
                            limitUsd = b.optDoubleOrNull("limit")
                                ?: b.optDoubleOrNull("amount")
                                ?: b.optDoubleOrNull("max_usd"),
                            usageUsd = b.optDoubleOrNull("usage")
                                ?: b.optDoubleOrNull("spent"),
                            remainingUsd = b.optDoubleOrNull("remaining")
                                ?: b.optDoubleOrNull("limit_remaining"),
                            resetInterval = b.optNullableString("reset")
                                ?: b.optNullableString("limit_reset")
                                ?: b.optNullableString("interval"),
                        ),
                    )
                }
            } else {
                val budgetsObj = data.optJSONObject("budgets")
                if (budgetsObj != null) {
                    val keys = budgetsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val b = budgetsObj.optJSONObject(k)
                        if (b != null) {
                            parsedBudgets.add(
                                OpenRouterBudgetWindow(
                                    name = b.optNullableString("name") ?: k.replace('_', ' ')
                                        .replaceFirstChar { it.uppercase() },
                                    limitUsd = b.optDoubleOrNull("limit") ?: b.optDoubleOrNull("amount"),
                                    usageUsd = b.optDoubleOrNull("usage") ?: b.optDoubleOrNull("spent"),
                                    remainingUsd = b.optDoubleOrNull("remaining")
                                        ?: b.optDoubleOrNull("limit_remaining"),
                                    resetInterval = b.optNullableString("reset")
                                        ?: b.optNullableString("interval")
                                        ?: k,
                                ),
                            )
                        } else {
                            budgetsObj.optDoubleOrNull(k)?.let { lim ->
                                parsedBudgets.add(
                                    OpenRouterBudgetWindow(
                                        name = k.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                        limitUsd = lim,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            var totalCredits: Double? = null
            var totalAccountUsage: Double? = null
            if (!creditsBody.isNullOrBlank()) {
                runCatching {
                    val cRoot = JSONObject(creditsBody)
                    val cData = cRoot.optJSONObject("data") ?: cRoot
                    totalCredits = cData.optDoubleOrNull("total_credits")
                    totalAccountUsage = cData.optDoubleOrNull("total_usage")
                }
            }

            return OpenRouterUsageSnapshot(
                label = label,
                usageUsd = usage,
                usageDailyUsd = usageDaily,
                usageWeeklyUsd = usageWeekly,
                usageMonthlyUsd = usageMonthly,
                limitUsd = limit,
                limitRemainingUsd = limitRemaining,
                limitReset = limitReset,
                isFreeTier = isFreeTier,
                totalCreditsUsd = totalCredits,
                totalAccountUsageUsd = totalAccountUsage,
                effectiveBudgetUsd = effectiveBudget,
                budgets = parsedBudgets,
                fetchedAtMillis = nowMillis,
            )
        }

        internal fun parseActivityJson(
            activityBody: String,
            nowMillis: Long = System.currentTimeMillis(),
        ): OpenRouterActivityBreakdown {
            val root = JSONObject(activityBody)
            val dataArray: JSONArray = root.optJSONArray("data") ?: JSONArray()
            val items = mutableListOf<OpenRouterActivityItem>()

            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val date = obj.optString("date").trim().take(10).ifBlank { "Unknown" }
                val model = obj.optString("model").trim()
                    .ifBlank { obj.optString("model_permaslug").trim() }
                    .ifBlank { "unknown" }
                val usage = obj.optDoubleOrNull("usage") ?: 0.0
                val requests = obj.optInt("requests", 0)
                val promptTokens = obj.optLong("prompt_tokens", 0L)
                val completionTokens = obj.optLong("completion_tokens", 0L)
                val reasoningTokens = obj.optLong("reasoning_tokens", 0L)
                items.add(
                    OpenRouterActivityItem(
                        date = date,
                        model = model,
                        usageUsd = usage,
                        requests = requests,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        reasoningTokens = reasoningTokens,
                    ),
                )
            }

            val byModel = items
                .groupBy { it.model }
                .map { (model, list) ->
                    OpenRouterActivityGroup(
                        key = model,
                        usageUsd = list.sumOf { it.usageUsd },
                        requests = list.sumOf { it.requests },
                        totalTokens = list.sumOf { it.promptTokens + it.completionTokens + it.reasoningTokens },
                    )
                }
                .sortedByDescending { it.usageUsd }

            val byDate = items
                .groupBy { it.date }
                .map { (date, list) ->
                    OpenRouterActivityGroup(
                        key = date,
                        usageUsd = list.sumOf { it.usageUsd },
                        requests = list.sumOf { it.requests },
                        totalTokens = list.sumOf { it.promptTokens + it.completionTokens + it.reasoningTokens },
                    )
                }
                .sortedByDescending { it.key }

            return OpenRouterActivityBreakdown(
                items = items,
                byModel = byModel,
                byDate = byDate,
                totalSpend30dUsd = items.sumOf { it.usageUsd },
                totalRequests30d = items.sumOf { it.requests },
                fetchedAtMillis = nowMillis,
            )
        }

        private fun JSONObject.optDoubleOrNull(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            val raw = opt(key)
            return when (raw) {
                is Number -> raw.toDouble().takeIf { !it.isNaN() && !it.isInfinite() }
                is String -> raw.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
                else -> null
            }
        }

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        }
    }

    private val _usageSnapshot = MutableStateFlow<OpenRouterUsageSnapshot?>(null)
    val usageSnapshot: StateFlow<OpenRouterUsageSnapshot?> = _usageSnapshot.asStateFlow()

    private val _activityBreakdown = MutableStateFlow<OpenRouterActivityBreakdown?>(null)
    val activityBreakdown: StateFlow<OpenRouterActivityBreakdown?> = _activityBreakdown.asStateFlow()

    suspend fun refreshUsage(apiKey: String): Result<OpenRouterUsageSnapshot> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("OpenRouter API key is missing"))
        }
        runCatching {
            val keyBody = fetchKeyBody(cleanKey)
            val creditsBody = fetchCreditsBodyBestEffort(cleanKey)
            val snapshot = parseKeyAndCreditsJson(
                keyBody = keyBody,
                creditsBody = creditsBody,
            )
            _usageSnapshot.value = snapshot
            snapshot
        }
    }

    suspend fun refreshActivity(managementApiKey: String): Result<OpenRouterActivityBreakdown> =
        withContext(Dispatchers.IO) {
            val cleanKey = managementApiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("OpenRouter Management API key is missing"))
            }
            runCatching {
                val request = buildGetRequest(ACTIVITY_URL, cleanKey)
                okHttpClient.newCall(request).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw OpenRouterErrorParser.parse(
                            httpCode = res.code,
                            responseBody = body,
                            headers = res.headers.toMap(),
                        )
                    }
                    val parsed = parseActivityJson(body)
                    _activityBreakdown.value = parsed
                    parsed
                }
            }
        }

    fun clearMemoryCache() {
        _usageSnapshot.value = null
        _activityBreakdown.value = null
    }

    private fun fetchKeyBody(apiKey: String): String {
        val primaryReq = buildGetRequest(KEY_URL, apiKey)
        okHttpClient.newCall(primaryReq).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.isSuccessful && body.isNotBlank()) return body
            if (res.code != 404) {
                throw OpenRouterErrorParser.parse(
                    httpCode = res.code,
                    responseBody = body,
                    headers = res.headers.toMap(),
                )
            }
        }
        val fallbackReq = buildGetRequest(AUTH_KEY_FALLBACK_URL, apiKey)
        okHttpClient.newCall(fallbackReq).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw OpenRouterErrorParser.parse(
                    httpCode = res.code,
                    responseBody = body,
                    headers = res.headers.toMap(),
                )
            }
            return body
        }
    }

    private fun fetchCreditsBodyBestEffort(apiKey: String): String? = runCatching {
        val req = buildGetRequest(CREDITS_URL, apiKey)
        okHttpClient.newCall(req).execute().use { res ->
            if (res.isSuccessful) res.body?.string() else null
        }
    }.getOrNull()

    private fun buildGetRequest(url: String, apiKey: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("HTTP-Referer", "https://aetherrouter.local")
            .header("X-Title", "AetherRouter")
            .get()
            .build()
}
