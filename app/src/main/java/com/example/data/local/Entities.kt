package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String, // Encrypted via CryptoManager
    val colorHex: String = "#5B7CFA",
    val defaultModelId: String = "",
    val projectInstructions: String = "", // Encrypted via CryptoManager
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["folderId"]), Index(value = ["updatedAt"])],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val folderId: String? = null,
    val personaId: String? = null,
    val enabledMcpServerIdsJson: String = "",
    val fallbackModelIdsJson: String = "",
    val enabledLorebookIdsJson: String = "",
    val contextSummary: String = "", // Encrypted rolling memory summary
    val summarizedUpToTimestamp: Long = 0L,
    val title: String, // Encrypted via CryptoManager
    val modelId: String,
    val systemPromptOverride: String = "", // Encrypted via CryptoManager
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 4096,
    val contextWindowLimit: Int = 128_000,
    val reasoningEffort: String = "medium",
    val webSearchEnabled: Boolean = true,
    val webSearchMode: String = "AGENTIC_TOOL",
    val webSearchEngine: String = "auto",
    val webMaxResults: Int = 5,
    val webMaxTotalResults: Int = 15,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user" | "assistant" | "tool"
    val content: String, // Encrypted via CryptoManager
    val reasoningContent: String = "", // Encrypted human-readable reasoning text
    val reasoningDetailsJson: String = "", // Encrypted raw OpenRouter reasoning_details array for lossless replay
    val thinkingDurationMs: Long = 0L,
    val citationsJson: String = "", // Encrypted JSON array of UrlCitation
    val attachmentsJson: String = "", // Encrypted attachments metadata (Phase 2)
    val reactionsJson: String = "", // Emoji reactions (Phase 2)
    val modelUsed: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val toolCallsJson: String = "", // Encrypted tool calls payload
    val toolCallId: String = "",
    val swipesJson: String = "", // Encrypted JSON array of MessageSwipe variants
    val activeSwipeIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cached_models",
    indices = [Index(value = ["company"]), Index(value = ["createdEpochSeconds"])],
)
data class CachedModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val company: String,
    val description: String = "",
    val createdEpochSeconds: Long = 0L,
    val contextLength: Int = 128_000,
    val promptPricingPerMillion: Double = 0.0,
    val completionPricingPerMillion: Double = 0.0,
    val supportsVision: Boolean = false,
    val supportsPdfFile: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsTools: Boolean = true,
    val isFree: Boolean = false,
    val isFavorite: Boolean = false,
    val fetchedAt: Long = 0L,
)

@Entity(tableName = "custom_stickers")
data class CustomStickerEntity(
    @PrimaryKey val id: String,
    val label: String,
    val localFilePath: String,
    val mimeType: String,
    val packName: String = "My Stickers",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "persona_cards")
data class PersonaCardEntity(
    @PrimaryKey val id: String,
    val name: String, // Encrypted via CryptoManager
    val payloadJson: String, // Encrypted JSON containing full V1/V2 card & lorebook
    val avatarPath: String = "",
    val cardFormat: String = "custom",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String, // Encrypted via CryptoManager
    val endpointUrl: String, // Encrypted via CryptoManager
    val authHeaderName: String = "Authorization",
    val authHeaderValue: String = "", // Encrypted via CryptoManager
    val isEnabled: Boolean = true,
    val toolsJson: String = "", // JSON array of discovered McpToolDef
    val lastSyncedAt: Long = 0L,
)

@Entity(tableName = "global_lorebooks")
data class GlobalLorebookEntity(
    @PrimaryKey val id: String,
    val name: String, // Encrypted via CryptoManager
    val description: String = "", // Encrypted via CryptoManager
    val entriesJson: String = "", // Encrypted JSON array of LorebookEntry
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sampler_presets")
data class SamplerPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 4096,
    val reasoningEffort: String = "medium",
    val systemPromptSnippet: String = "", // Encrypted via CryptoManager
    val isBuiltIn: Boolean = false,
)

@Entity(tableName = "slash_prompt_templates")
data class SlashPromptTemplateEntity(
    @PrimaryKey val id: String,
    val command: String,
    val title: String,
    val promptBody: String, // Encrypted via CryptoManager
    val updatedAt: Long = System.currentTimeMillis(),
)
