package com.example.data.model

data class OpenRouterBudgetWindow(
    val name: String,
    val limitUsd: Double? = null,
    val usageUsd: Double? = null,
    val remainingUsd: Double? = null,
    val resetInterval: String? = null,
)

data class OpenRouterUsageSnapshot(
    val label: String = "",
    val usageUsd: Double = 0.0,
    val usageDailyUsd: Double = 0.0,
    val usageWeeklyUsd: Double = 0.0,
    val usageMonthlyUsd: Double = 0.0,
    val limitUsd: Double? = null,
    val limitRemainingUsd: Double? = null,
    val limitReset: String? = null,
    val isFreeTier: Boolean = false,
    val totalCreditsUsd: Double? = null,
    val totalAccountUsageUsd: Double? = null,
    val effectiveBudgetUsd: Double? = null,
    val budgets: List<OpenRouterBudgetWindow> = emptyList(),
    val fetchedAtMillis: Long = 0L,
) {
    /**
     * Best available remaining dollar figure for the compact pill:
     * prefers explicit key `limit_remaining`, then account `totalCredits - totalAccountUsage`,
     * then `effectiveBudget - usage`.
     */
    val displayRemainingUsd: Double?
        get() = when {
            limitRemainingUsd != null -> limitRemainingUsd.coerceAtLeast(0.0)
            totalCreditsUsd != null && totalAccountUsageUsd != null ->
                (totalCreditsUsd - totalAccountUsageUsd).coerceAtLeast(0.0)
            effectiveBudgetUsd != null -> (effectiveBudgetUsd - usageUsd).coerceAtLeast(0.0)
            else -> null
        }

    val effectiveCapUsd: Double?
        get() = limitUsd ?: effectiveBudgetUsd ?: totalCreditsUsd

    val limitProgressFraction: Float?
        get() {
            val cap = effectiveCapUsd ?: return null
            if (cap <= 0.0) return null
            val remaining = displayRemainingUsd ?: (cap - usageUsd).coerceAtLeast(0.0)
            val used = (cap - remaining).coerceIn(0.0, cap)
            return (used / cap).toFloat().coerceIn(0f, 1f)
        }

    fun toLegacyCreditBalance(): OpenRouterCreditBalance = OpenRouterCreditBalance(
        totalCreditsUsd = totalCreditsUsd ?: limitUsd,
        totalUsageUsd = totalAccountUsageUsd ?: usageUsd,
        remainingUsd = displayRemainingUsd,
        isFreeTier = isFreeTier,
        fetchedAt = fetchedAtMillis,
    )
}

data class OpenRouterActivityItem(
    val date: String,
    val model: String,
    val usageUsd: Double,
    val requests: Int,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
)

data class OpenRouterActivityGroup(
    val key: String,
    val usageUsd: Double,
    val requests: Int,
    val totalTokens: Long,
)

data class OpenRouterActivityBreakdown(
    val items: List<OpenRouterActivityItem> = emptyList(),
    val byModel: List<OpenRouterActivityGroup> = emptyList(),
    val byDate: List<OpenRouterActivityGroup> = emptyList(),
    val totalSpend30dUsd: Double = 0.0,
    val totalRequests30d: Int = 0,
    val fetchedAtMillis: Long = 0L,
)

enum class CreditsErrorSubCode(val wireKey: String, val label: String) {
    OPENROUTER_CREDITS("openrouter_credits", "Insufficient Account Credits"),
    OPENROUTER_KEY_LIMIT("openrouter_key_limit", "API Key Spend Limit Reached"),
    OPENROUTER_IN_FLIGHT_BUDGET("openrouter_in_flight_budget", "In-Flight Budget Limit Exceeded"),
    GENERIC_PAYMENT_REQUIRED("payment_required", "Payment Required (402)"),
}

sealed interface OpenRouterAlertState {
    val httpCode: Int
    val headline: String
    val detail: String

    data class InsufficientCredits402(
        val subCode: CreditsErrorSubCode,
        override val headline: String,
        override val detail: String,
        val rawMessage: String = "",
    ) : OpenRouterAlertState {
        override val httpCode: Int = 402
    }

    data class GuardrailForbidden403(
        override val headline: String,
        override val detail: String,
        val rawMessage: String = "",
    ) : OpenRouterAlertState {
        override val httpCode: Int = 403
    }

    data class RateLimited429(
        override val headline: String,
        override val detail: String,
        val resetEpochMillis: Long? = null,
        val rawMessage: String = "",
    ) : OpenRouterAlertState {
        override val httpCode: Int = 429
    }
}

data class LiveArtifactPreviewRequest(
    val language: String,
    val code: String,
    val suggestedFileName: String,
)

data class GitHubRepoSummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: String,
    val description: String = "",
    val defaultBranch: String = "main",
    val isPrivate: Boolean = false,
    val language: String = "",
    val htmlUrl: String = "",
    val updatedAt: String = "",
)

data class GitHubBranchSummary(
    val name: String,
    val isProtected: Boolean = false,
    val sha: String = "",
)

data class GitHubContentEntry(
    val name: String,
    val path: String,
    val type: String, // "file" | "dir"
    val sizeBytes: Long = 0L,
    val sha: String = "",
    val htmlUrl: String = "",
) {
    val isDirectory: Boolean get() = type == "dir"
}

data class GitHubCommitPreview(
    val owner: String,
    val repo: String,
    val branch: String,
    val path: String,
    val content: String,
    val commitMessage: String,
    val existingSha: String? = null,
    val lineCount: Int = 0,
    val byteSize: Int = 0,
) {
    val isUpdate: Boolean get() = !existingSha.isNullOrBlank()
}

data class GitHubCommitResult(
    val commitSha: String,
    val htmlUrl: String,
    val path: String,
    val branch: String,
    val isUpdate: Boolean,
)

data class GitHubPendingPush(
    val code: String,
    val language: String,
    val suggestedFileName: String,
)
