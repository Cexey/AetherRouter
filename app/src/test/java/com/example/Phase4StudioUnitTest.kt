package com.example

import com.example.core.CodeArtifactUtils
import com.example.data.model.CreditsErrorSubCode
import com.example.data.model.OpenRouterAlertState
import com.example.data.repository.UsageRepository
import com.example.network.GitHubRepository
import com.example.network.OpenRouterErrorParser
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4StudioUnitTest {

    @Test
    fun `OpenRouterErrorParser classifies 402 subcodes accurately`() {
        val creditsEx = OpenRouterErrorParser.parse(
            httpCode = 402,
            responseBody = """{"error":{"code":"openrouter_credits","message":"Insufficient credits"}}""",
        )
        val creditsAlert = creditsEx.alert as? OpenRouterAlertState.InsufficientCredits402
        assertNotNull(creditsAlert)
        assertEquals(CreditsErrorSubCode.OPENROUTER_CREDITS, creditsAlert?.subCode)

        val keyLimitEx = OpenRouterErrorParser.parse(
            httpCode = 402,
            responseBody = """{"error":{"type":"openrouter_key_limit","message":"API key spending limit reached"}}""",
        )
        val keyLimitAlert = keyLimitEx.alert as? OpenRouterAlertState.InsufficientCredits402
        assertNotNull(keyLimitAlert)
        assertEquals(CreditsErrorSubCode.OPENROUTER_KEY_LIMIT, keyLimitAlert?.subCode)

        val inFlightEx = OpenRouterErrorParser.parse(
            httpCode = 402,
            responseBody = """{"error":{"metadata":{"reason":"openrouter_in_flight_budget"},"message":"Too many concurrent requests"}}""",
        )
        val inFlightAlert = inFlightEx.alert as? OpenRouterAlertState.InsufficientCredits402
        assertNotNull(inFlightAlert)
        assertEquals(CreditsErrorSubCode.OPENROUTER_IN_FLIGHT_BUDGET, inFlightAlert?.subCode)
    }

    @Test
    fun `OpenRouterErrorParser classifies 403 guardrail and 429 rate limit with reset timestamp`() {
        val forbiddenEx = OpenRouterErrorParser.parse(
            httpCode = 403,
            responseBody = """{"error":{"message":"Blocked by workspace budget guardrail policy"}}""",
        )
        val forbiddenAlert = forbiddenEx.alert as? OpenRouterAlertState.GuardrailForbidden403
        assertNotNull(forbiddenAlert)
        assertTrue(forbiddenAlert!!.headline.contains("Guardrail"))

        val fixedNow = 1_750_000_000_000L
        val resetMillisExpected = 1_750_000_060_000L

        // Epoch ms header
        val rateLimitMs = OpenRouterErrorParser.parse(
            httpCode = 429,
            responseBody = """{"error":{"message":"Rate limit exceeded"}}""",
            headers = mapOf("X-RateLimit-Reset" to resetMillisExpected.toString()),
            nowMillis = fixedNow,
        )
        val alertMs = rateLimitMs.alert as? OpenRouterAlertState.RateLimited429
        assertNotNull(alertMs)
        assertEquals(resetMillisExpected, alertMs?.resetEpochMillis)

        // Epoch seconds header
        val epochSec = 1_750_000_060L
        assertEquals(
            epochSec * 1_000L,
            OpenRouterErrorParser.parseRateLimitResetMillis(epochSec.toString(), null, fixedNow),
        )

        // Relative delta seconds via Retry-After fallback
        assertEquals(
            fixedNow + 45_000L,
            OpenRouterErrorParser.parseRateLimitResetMillis(null, "45", fixedNow),
        )
    }

    @Test
    fun `UsageRepository parses key limits, effective budgets, and account credits`() {
        val keyJson = """
            {
              "data": {
                "label": "sk-or-v1-prod",
                "usage": 18.42,
                "usage_daily": 1.25,
                "usage_weekly": 6.10,
                "usage_monthly": 14.80,
                "limit": 50.0,
                "limit_remaining": 31.58,
                "limit_reset": "monthly",
                "is_free_tier": false,
                "effective_budget": {
                  "name": "Monthly Workspace Cap",
                  "limit": 40.0,
                  "usage": 14.80,
                  "remaining": 25.20,
                  "reset": "monthly"
                },
                "budgets": [
                  {
                    "name": "Daily Guardrail",
                    "limit": 5.0,
                    "usage": 1.25,
                    "remaining": 3.75,
                    "interval": "daily"
                  }
                ]
              }
            }
        """.trimIndent()

        val creditsJson = """
            {
              "data": {
                "total_credits": 100.0,
                "total_usage": 42.5
              }
            }
        """.trimIndent()

        val snapshot = UsageRepository.parseKeyAndCreditsJson(
            keyBody = keyJson,
            creditsBody = creditsJson,
            nowMillis = 1_750_000_000_000L,
        )

        assertEquals("sk-or-v1-prod", snapshot.label)
        assertEquals(18.42, snapshot.usageUsd, 0.0001)
        assertEquals(1.25, snapshot.usageDailyUsd, 0.0001)
        assertEquals(6.10, snapshot.usageWeeklyUsd, 0.0001)
        assertEquals(14.80, snapshot.usageMonthlyUsd, 0.0001)
        assertEquals(50.0, snapshot.limitUsd!!, 0.0001)
        assertEquals(31.58, snapshot.limitRemainingUsd!!, 0.0001)
        assertEquals("monthly", snapshot.limitReset)
        assertEquals(40.0, snapshot.effectiveBudgetUsd!!, 0.0001)
        assertEquals(2, snapshot.budgets.size)
        assertEquals(100.0, snapshot.totalCreditsUsd!!, 0.0001)
        assertEquals(42.5, snapshot.totalAccountUsageUsd!!, 0.0001)
        assertEquals(31.58, snapshot.displayRemainingUsd!!, 0.0001)
        assertEquals(50.0, snapshot.effectiveCapUsd!!, 0.0001)
        assertNotNull(snapshot.limitProgressFraction)

        val unlimitedKeySnapshot = UsageRepository.parseKeyAndCreditsJson(
            keyBody = """{"data":{"usage":10.0,"limit":null,"limit_remaining":null}}""",
            creditsBody = creditsJson,
            nowMillis = 1_750_000_000_000L,
        )
        assertEquals(57.5, unlimitedKeySnapshot.displayRemainingUsd!!, 0.0001)
    }

    @Test
    fun `UsageRepository groups 30-day activity by model and date`() {
        val activityJson = """
            {
              "data": [
                {
                  "date": "2026-11-24",
                  "model": "anthropic/claude-3.7-sonnet",
                  "usage": 2.40,
                  "requests": 12,
                  "prompt_tokens": 20000,
                  "completion_tokens": 5000
                },
                {
                  "date": "2026-11-25",
                  "model": "anthropic/claude-3.7-sonnet",
                  "usage": 1.60,
                  "requests": 8,
                  "prompt_tokens": 12000,
                  "completion_tokens": 3000
                },
                {
                  "date": "2026-11-25",
                  "model": "openai/gpt-4o",
                  "usage": 0.90,
                  "requests": 5,
                  "prompt_tokens": 8000,
                  "completion_tokens": 2000
                }
              ]
            }
        """.trimIndent()

        val breakdown = UsageRepository.parseActivityJson(activityJson, nowMillis = 1_750_000_000_000L)
        assertEquals(4.90, breakdown.totalSpend30dUsd, 0.0001)
        assertEquals(25, breakdown.totalRequests30d)
        assertEquals(2, breakdown.byModel.size)
        assertEquals("anthropic/claude-3.7-sonnet", breakdown.byModel[0].key)
        assertEquals(4.00, breakdown.byModel[0].usageUsd, 0.0001)
        assertEquals(2, breakdown.byDate.size)
        assertEquals("2026-11-25", breakdown.byDate[0].key)
        assertEquals(2.50, breakdown.byDate[0].usageUsd, 0.0001)
    }

    @Test
    fun `CodeArtifactUtils infers extensions, detects previewable HTML, and sanitizes GitHub paths`() {
        val cases = mapOf(
            "kotlin" to "kt",
            "py" to "py",
            "javascript" to "js",
            "html" to "html",
            "css" to "css",
            "json" to "json",
            "markdown" to "md",
            "bash" to "sh",
            "unknownlang" to "txt",
        )
        cases.forEach { (lang, expectedExt) ->
            val spec = CodeArtifactUtils.buildCodeExportSpec(lang, "sample", "artifact")
            assertEquals("Expected .$expectedExt for $lang", "artifact.$expectedExt", spec.suggestedFileName)
            assertEquals(expectedExt, spec.extension)
        }

        assertTrue(CodeArtifactUtils.isPreviewableHtmlArtifact("html", "<h1>Hello</h1>"))
        assertTrue(CodeArtifactUtils.isPreviewableHtmlArtifact("", "<!DOCTYPE html><html><body>Hi</body></html>"))
        assertFalse(CodeArtifactUtils.isPreviewableHtmlArtifact("kotlin", "fun main() {}"))

        val wrapped = CodeArtifactUtils.wrapArtifactForOfflinePreview("html", "<div>Hi</div>")
        assertTrue(wrapped.contains("<!DOCTYPE html>"))
        assertTrue(wrapped.contains("<div>Hi</div>"))

        assertTrue(CodeArtifactUtils.isValidGitHubIdentifier("octocat"))
        assertTrue(CodeArtifactUtils.isValidGitHubIdentifier("aether-router_v4"))
        assertFalse(CodeArtifactUtils.isValidGitHubIdentifier("../secret"))
        assertFalse(CodeArtifactUtils.isValidGitHubIdentifier("bad/repo"))

        assertEquals("src/ui/Main.kt", CodeArtifactUtils.sanitizeGitHubPath("/src/ui/Main.kt/"))
        assertEquals("", CodeArtifactUtils.sanitizeGitHubPath("   "))
        assertNull(CodeArtifactUtils.sanitizeGitHubPath("../etc/passwd"))
        assertNull(CodeArtifactUtils.sanitizeGitHubPath("src\\win\\file.kt"))
        assertNull(CodeArtifactUtils.sanitizeGitHubPath("src/a//b.kt"))
    }

    @Test
    fun `UsageRepository and GitHubRepository clear memory caches cleanly`() {
        val client = OkHttpClient()
        val usageRepo = UsageRepository(client)
        val ghRepo = GitHubRepository(client)

        usageRepo.clearMemoryCache()
        ghRepo.clearMemoryCache()

        assertNull(usageRepo.usageSnapshot.value)
        assertNull(usageRepo.activityBreakdown.value)
        assertNull(ghRepo.authenticatedUser.value)
        assertTrue(ghRepo.cachedRepos.value.isEmpty())
    }
}
