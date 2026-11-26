package com.example.core

import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.GlobalLorebook
import com.example.data.model.ModelInfo
import com.example.data.model.PersonaCard
import kotlin.math.ceil

data class ContextCostMetrics(
    val estimatedContextTokens: Int = 0,
    val contextWindowLimit: Int = 128_000,
    val usageFraction: Float = 0f,
    val estimatedChatCostUsd: Double = 0.0,
    val activeMessagesCount: Int = 0,
    val compressedMessagesCount: Int = 0,
) {
    fun formattedTokens(): String = "${formatCompactCount(estimatedContextTokens)} / ${formatCompactCount(contextWindowLimit)}"

    fun formattedCost(): String = formatUsd(estimatedChatCostUsd)

    companion object {
        fun formatCompactCount(tokens: Int): String = when {
            tokens >= 1_000_000 -> String.format("%.2fM", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
            else -> "$tokens"
        }

        fun formatUsd(usd: Double): String = when {
            usd <= 0.0 -> "$0.00"
            usd < 0.0001 -> "<$0.0001"
            usd < 0.01 -> String.format("$%.4f", usd)
            else -> String.format("$%.3f", usd)
        }
    }
}

/**
 * Pure Kotlin context window utilization, persona token counter, and live OpenRouter USD cost estimator.
 */
object ContextAndCostEstimator {

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return ceil(text.length / 4.0).toInt().coerceAtLeast(1)
    }

    fun estimatePersonaTokens(
        systemPrompt: String,
        description: String,
        personality: String,
        scenario: String,
        postHistory: String,
        lorebookTotalChars: Int,
    ): Pair<Int, Int> {
        val totalChars = systemPrompt.length +
            description.length +
            personality.length +
            scenario.length +
            postHistory.length +
            lorebookTotalChars
        val estTokens = if (totalChars == 0) 0 else ceil(totalChars / 4.0).toInt()
        return totalChars to estTokens
    }

    fun estimateMessageCostUsd(
        message: ChatMessage,
        modelById: Map<String, ModelInfo>,
        fallbackModel: ModelInfo?,
    ): Double {
        if (message.role != "assistant") return 0.0
        val model = modelById[message.modelUsed] ?: fallbackModel ?: return 0.0
        if (model.isFree) return 0.0
        val promptTok = message.promptTokens.takeIf { it > 0 } ?: 0
        val completionTok = message.completionTokens.takeIf { it > 0 } ?: estimateTokens(message.content)
        val promptCost = (promptTok / 1_000_000.0) * model.promptPricingPerMillion
        val completionCost = (completionTok / 1_000_000.0) * model.completionPricingPerMillion
        return (promptCost + completionCost).coerceAtLeast(0.0)
    }

    fun computeConversationMetrics(
        conversation: Conversation?,
        messages: List<ChatMessage>,
        stagedAttachments: List<ChatAttachment>,
        activeModel: ModelInfo?,
        modelById: Map<String, ModelInfo>,
        activePersona: PersonaCard?,
        activeGlobalLorebooks: List<GlobalLorebook>,
        projectInstructions: String,
    ): ContextCostMetrics {
        val limit = (activeModel?.contextLength ?: conversation?.contextWindowLimit ?: 128_000).coerceAtLeast(4_096)
        val cutoff = conversation?.summarizedUpToTimestamp ?: 0L

        var tokens = 48 // Temporal grounding & base framing
        if (conversation != null) {
            tokens += estimateTokens(conversation.systemPromptOverride)
            tokens += estimateTokens(conversation.contextSummary)
        }
        tokens += estimateTokens(projectInstructions)

        if (activePersona != null) {
            tokens += estimateTokens(activePersona.systemPrompt)
            tokens += estimateTokens(activePersona.description)
            tokens += estimateTokens(activePersona.personality)
            tokens += estimateTokens(activePersona.scenario)
            tokens += estimateTokens(activePersona.postHistoryInstructions)
            activePersona.lorebookEntries.filter { it.enabled }.forEach {
                tokens += estimateTokens(it.content)
            }
        }
        activeGlobalLorebooks.forEach { book ->
            book.entries.filter { it.enabled }.forEach {
                tokens += estimateTokens(it.content)
            }
        }

        var activeCount = 0
        var compressedCount = 0
        var totalCostUsd = 0.0

        for (msg in messages) {
            totalCostUsd += estimateMessageCostUsd(msg, modelById, activeModel)
            if (cutoff > 0L && msg.createdAt <= cutoff) {
                compressedCount++
            } else {
                activeCount++
                tokens += estimateTokens(msg.content) + 6
                msg.attachments.forEach { att ->
                    tokens += if (att.extractedText.isNotBlank()) {
                        att.estimatedTokens()
                    } else {
                        512 // Vision image / GIF token footprint estimate
                    }
                }
            }
        }

        stagedAttachments.forEach { att ->
            tokens += if (att.extractedText.isNotBlank()) att.estimatedTokens() else 512
        }

        val ratio = (tokens.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
        return ContextCostMetrics(
            estimatedContextTokens = tokens,
            contextWindowLimit = limit,
            usageFraction = ratio,
            estimatedChatCostUsd = totalCostUsd,
            activeMessagesCount = activeCount,
            compressedMessagesCount = compressedCount,
        )
    }
}
