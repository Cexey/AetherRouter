package com.example.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED;

    companion object {
        fun fromString(raw: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

enum class WebSearchMode {
    /**
     * Billing-safe default for tool-capable models:
     * sends openrouter:web_search + openrouter:web_fetch server tools so OpenRouter
     * only charges search credits when the model actually invokes live search.
     */
    AGENTIC_TOOL,

    /**
     * Forced grounding via OpenRouter's `plugins: [{ "id": "web" }]` on every turn
     * (useful for non-tool models or guaranteed live grounding).
     */
    ALWAYS_ON_PLUGIN;

    companion object {
        fun fromString(raw: String?): WebSearchMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AGENTIC_TOOL
    }
}

enum class WebSearchEngine(val wireValue: String, val label: String) {
    AUTO("auto", "Auto"),
    NATIVE("native", "Native"),
    EXA("exa", "Exa");

    companion object {
        fun fromWire(raw: String?): WebSearchEngine =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: AUTO
    }
}

enum class ReasoningEffort(val wireValue: String, val label: String) {
    NONE("none", "Off"),
    MINIMAL("minimal", "Minimal"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    MAX("max", "Max");

    companion object {
        fun fromWire(raw: String?): ReasoningEffort =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: MEDIUM
    }
}

enum class LinkOpenBehavior(val label: String) {
    IN_APP("Open Inside App"),
    ASK("Ask Every Time"),
    EXTERNAL("External Browser");

    companion object {
        fun fromString(raw: String?): LinkOpenBehavior =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: IN_APP
    }
}

enum class ModelSortMode(val label: String) {
    A_Z("A–Z"),
    COMPANY("Company"),
    RELEASE_DATE("Release Date");

    companion object {
        fun fromString(raw: String?): ModelSortMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: RELEASE_DATE
    }
}

enum class AttachmentKind {
    IMAGE,
    GIF,
    STICKER,
    PDF,
    DOCUMENT,
    WEBPAGE;

    companion object {
        fun fromString(raw: String?): AttachmentKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DOCUMENT
    }
}

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    MARKDOWN("md", "text/markdown", "Markdown (.md)"),
    PLAIN_TEXT("txt", "text/plain", "Plain Text (.txt)"),
    JSON("json", "application/json", "Chat JSON (.json)"),
    ENCRYPTED_VAULT("orvault", "application/octet-stream", "Encrypted Vault (.orvault)"),
}

data class ChatAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val localFilePath: String = "",
    val previewImagePath: String = "",
    val extractedText: String = "",
    val sizeBytes: Long = 0L,
) {
    fun formattedSize(): String = when {
        sizeBytes >= 1_048_576L -> String.format("%.1f MB", sizeBytes / 1_048_576.0)
        sizeBytes >= 1_024L -> "${sizeBytes / 1_024L} KB"
        sizeBytes > 0L -> "$sizeBytes B"
        else -> ""
    }

    fun estimatedTokens(): Int = (extractedText.length / 4).coerceAtLeast(0)
}

data class CustomSticker(
    val id: String,
    val label: String,
    val localFilePath: String,
    val mimeType: String,
    val packName: String = "My Stickers",
    val createdAt: Long = System.currentTimeMillis(),
)

data class CreatorProfile(
    val displayName: String = "",
    val email: String = "",
    val githubRepoUrl: String = "",
    val websiteUrl: String = "",
    val customLinkLabel: String = "",
    val customLinkUrl: String = "",
    val bioNotes: String = "",
) {
    val hasAnyInfo: Boolean
        get() = displayName.isNotBlank() ||
            email.isNotBlank() ||
            githubRepoUrl.isNotBlank() ||
            websiteUrl.isNotBlank() ||
            customLinkUrl.isNotBlank() ||
            bioNotes.isNotBlank()
}

data class AppearanceConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val customAccentHex: String = "",
    val userBubbleHex: String = "",
    val assistantSurfaceHex: String = "",
    val selectedFontKey: String = "plus_jakarta_sans",
    val customFontPath: String = "",
    val customFontName: String = "",
    val zoomScale: Float = 1.0f,
    val lineSpacingMultiplier: Float = 1.38f,
    val paragraphSpacingDp: Float = 8f,
    val bubbleCornerRadiusDp: Float = 18f,
    val compactDensity: Boolean = false,
)

data class UrlCitation(
    val url: String,
    val title: String,
    val snippet: String = "",
)

data class MessageSwipe(
    val content: String,
    val reasoningContent: String = "",
    val reasoningDetailsJson: String = "",
    val thinkingDurationMs: Long = 0L,
    val citations: List<UrlCitation> = emptyList(),
    val modelUsed: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: String, // "user" | "assistant" | "tool"
    val content: String,
    val reasoningContent: String = "",
    /**
     * Exact serialized JSON array of OpenRouter `reasoning_details` objects
     * (including reasoning.encrypted / signatures) preserved verbatim for replay.
     */
    val reasoningDetailsJson: String = "",
    val thinkingDurationMs: Long = 0L,
    val citations: List<UrlCitation> = emptyList(),
    val attachments: List<ChatAttachment> = emptyList(),
    val reactions: Map<String, Int> = emptyMap(),
    val modelUsed: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val toolCallsJson: String = "",
    val toolCallId: String = "",
    val swipes: List<MessageSwipe> = emptyList(),
    val activeSwipeIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Conversation(
    val id: String,
    val folderId: String? = null,
    val personaId: String? = null,
    val enabledMcpServerIds: List<String> = emptyList(),
    val fallbackModelIds: List<String> = emptyList(),
    val enabledLorebookIds: List<String> = emptyList(),
    val contextSummary: String = "",
    val summarizedUpToTimestamp: Long = 0L,
    val title: String,
    val modelId: String,
    val systemPromptOverride: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 4096,
    val contextWindowLimit: Int = 128_000,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val webSearchEnabled: Boolean = true,
    val webSearchMode: WebSearchMode = WebSearchMode.AGENTIC_TOOL,
    val webSearchEngine: WebSearchEngine = WebSearchEngine.AUTO,
    val webMaxResults: Int = 5,
    val webMaxTotalResults: Int = 15,
    val isPinned: Boolean = false,
    val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class LorebookEntry(
    val id: String,
    val keys: List<String>,
    val content: String,
    val enabled: Boolean = true,
    val insertionOrder: Int = 100,
)

data class PersonaCard(
    val id: String,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val tags: List<String> = emptyList(),
    val lorebookEntries: List<LorebookEntry> = emptyList(),
    val avatarPath: String = "",
    val cardFormat: String = "custom",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class McpToolDef(
    val name: String,
    val description: String = "",
    val inputSchemaJson: String = """{"type":"object","properties":{}}""",
)

data class McpServerConfig(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val authHeaderName: String = "Authorization",
    val authHeaderValue: String = "",
    val isEnabled: Boolean = true,
    val tools: List<McpToolDef> = emptyList(),
    val lastSyncedAt: Long = 0L,
)

data class GlobalLorebook(
    val id: String,
    val name: String,
    val description: String = "",
    val entries: List<LorebookEntry> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SamplerPreset(
    val id: String,
    val name: String,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 4096,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val systemPromptSnippet: String = "",
    val isBuiltIn: Boolean = false,
)

data class SlashPromptTemplate(
    val id: String,
    val command: String, // e.g. "summarize" -> invoked as /summarize
    val title: String,
    val promptBody: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class OpenRouterCreditBalance(
    val totalCreditsUsd: Double? = null,
    val totalUsageUsd: Double = 0.0,
    val remainingUsd: Double? = null,
    val isFreeTier: Boolean = false,
    val fetchedAt: Long = 0L,
)

data class Folder(
    val id: String,
    val name: String,
    val colorHex: String = "#5B7CFA",
    val defaultModelId: String = "",
    val projectInstructions: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ModelInfo(
    val id: String,
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
) {
    fun formattedContext(): String = when {
        contextLength >= 1_000_000 -> "${contextLength / 1_000_000}M ctx"
        contextLength >= 1_000 -> "${contextLength / 1_000}K ctx"
        contextLength > 0 -> "$contextLength ctx"
        else -> "Standard ctx"
    }

    fun formattedPricing(): String = when {
        isFree || (promptPricingPerMillion <= 0.0 && completionPricingPerMillion <= 0.0) -> "Free"
        else -> String.format("$%.2f / $%.2f per 1M", promptPricingPerMillion, completionPricingPerMillion)
    }
}

data class ModelPickerMemory(
    val sortMode: ModelSortMode = ModelSortMode.RELEASE_DATE,
    val sortAscending: Boolean = false,
    val searchQuery: String = "",
    val selectedCompany: String = "",
    val filterFavorites: Boolean = false,
    val filterVision: Boolean = false,
    val filterReasoning: Boolean = false,
    val filterFree: Boolean = false,
    val filterTools: Boolean = false,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

data class AppPreferences(
    val appearance: AppearanceConfig = AppearanceConfig(),
    val creatorProfile: CreatorProfile = CreatorProfile(),
    val use24HourClock: Boolean = false,
    val showMessageTimestamps: Boolean = true,
    val linkOpenBehavior: LinkOpenBehavior = LinkOpenBehavior.IN_APP,
    val defaultModelId: String = "anthropic/claude-sonnet-4",
    val defaultWebSearchEnabled: Boolean = true,
    val defaultWebSearchMode: WebSearchMode = WebSearchMode.AGENTIC_TOOL,
    val defaultWebSearchEngine: WebSearchEngine = WebSearchEngine.AUTO,
    val defaultWebMaxResults: Int = 5,
    val defaultWebMaxTotalResults: Int = 15,
    val biometricLockEnabled: Boolean = false,
    val smartRepliesEnabled: Boolean = true,
    val hasApiKey: Boolean = false,
    val hasManagementApiKey: Boolean = false,
    val hasGitHubPat: Boolean = false,
) {
    val themeMode: ThemeMode get() = appearance.themeMode
    val useDynamicColor: Boolean get() = appearance.useDynamicColor
}
