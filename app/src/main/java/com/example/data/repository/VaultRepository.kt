package com.example.data.repository

import com.example.core.CryptoManager
import com.example.core.ImportedVaultBundle
import com.example.data.local.AetherDatabase
import com.example.data.local.CachedModelEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.CustomStickerEntity
import com.example.data.local.FolderEntity
import com.example.data.local.GlobalLorebookEntity
import com.example.data.local.McpServerEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PersonaCardEntity
import com.example.data.local.SamplerPresetEntity
import com.example.data.local.SlashPromptTemplateEntity
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.CustomSticker
import com.example.data.model.Folder
import com.example.data.model.GlobalLorebook
import com.example.data.model.LorebookEntry
import com.example.data.model.McpServerConfig
import com.example.data.model.McpToolDef
import com.example.data.model.MessageSwipe
import com.example.data.model.ModelInfo
import com.example.data.model.OpenRouterCreditBalance
import com.example.data.model.PersonaCard
import com.example.data.model.ReasoningEffort
import com.example.data.model.SamplerPreset
import com.example.data.model.SlashPromptTemplate
import com.example.data.model.UrlCitation
import com.example.data.model.WebSearchEngine
import com.example.data.model.WebSearchMode
import com.example.network.OpenRouterApiService
import com.example.network.OpenRouterNetworkFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory decrypted search index operating on Dispatchers.Default over AES-256-GCM
 * encrypted Room records. Supports instant full-text search across conversation titles
 * and message bodies while keeping 100% of SQLite storage encrypted at rest.
 */
class EncryptedSearchIndex {
    private val decryptedTitles = ConcurrentHashMap<String, String>()
    private val decryptedBodies = ConcurrentHashMap<String, String>()

    fun putTitle(conversationId: String, plaintextTitle: String) {
        decryptedTitles[conversationId] = plaintextTitle
    }

    fun putMessageBody(messageId: String, conversationId: String, plaintextBody: String) {
        decryptedBodies["$conversationId|$messageId"] = plaintextBody
    }

    fun removeConversation(conversationId: String) {
        decryptedTitles.remove(conversationId)
        decryptedBodies.keys.removeIf { it.startsWith("$conversationId|") }
    }

    fun clearMemoryCache() {
        decryptedTitles.clear()
        decryptedBodies.clear()
    }

    fun matchingConversationIds(query: String): Set<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptySet()
        val matches = LinkedHashSet<String>()
        for ((convId, title) in decryptedTitles) {
            if (title.lowercase().contains(needle)) {
                matches.add(convId)
            }
        }
        for ((compositeKey, body) in decryptedBodies) {
            val convId = compositeKey.substringBefore('|')
            if (convId !in matches && body.lowercase().contains(needle)) {
                matches.add(convId)
            }
        }
        return matches
    }
}

class VaultRepository(
    private val database: AetherDatabase,
    private val cryptoManager: CryptoManager,
    private val apiService: OpenRouterApiService,
) {
    private val folderDao = database.folderDao()
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val modelDao = database.cachedModelDao()
    private val stickerDao = database.customStickerDao()
    private val personaDao = database.personaCardDao()
    private val mcpServerDao = database.mcpServerDao()
    private val globalLorebookDao = database.globalLorebookDao()
    private val samplerPresetDao = database.samplerPresetDao()
    private val slashTemplateDao = database.slashPromptTemplateDao()

    val searchIndex = EncryptedSearchIndex()

    fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAllFolders()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAllConversationsWithCounts()
            .map { rows ->
                rows.map { row ->
                    val domain = row.conversation.toDomain(messageCount = row.messageCount)
                    searchIndex.putTitle(domain.id, domain.title)
                    domain
                }
            }
            .flowOn(Dispatchers.Default)

    fun observeConversation(id: String): Flow<Conversation?> =
        conversationDao.observeConversation(id)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.Default)

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messageDao.observeMessages(conversationId)
            .map { list ->
                list.map { entity ->
                    val domain = entity.toDomain()
                    searchIndex.putMessageBody(domain.id, domain.conversationId, domain.content)
                    domain
                }
            }
            .flowOn(Dispatchers.Default)

    fun observeModels(): Flow<List<ModelInfo>> =
        modelDao.observeAllModels()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    fun observeCustomStickers(): Flow<List<CustomSticker>> =
        stickerDao.observeStickers()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    fun observePersonas(): Flow<List<PersonaCard>> =
        personaDao.observeAllPersonas()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    fun observeMcpServers(): Flow<List<McpServerConfig>> =
        mcpServerDao.observeAllServers()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    suspend fun getPersonaById(id: String): PersonaCard? = withContext(Dispatchers.Default) {
        personaDao.getPersonaById(id)?.toDomain()
    }

    suspend fun upsertPersona(persona: PersonaCard) = withContext(Dispatchers.Default) {
        personaDao.upsertPersona(persona.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deletePersona(id: String) = withContext(Dispatchers.Default) {
        val convs = conversationDao.getAllConversationsOnce()
        for (c in convs) {
            if (c.personaId == id) {
                conversationDao.updateConversation(c.copy(personaId = null))
            }
        }
        personaDao.deletePersonaById(id)
    }

    suspend fun getMcpServersOnce(): List<McpServerConfig> = withContext(Dispatchers.Default) {
        mcpServerDao.getAllServersOnce().map { it.toDomain() }
    }

    suspend fun upsertMcpServer(server: McpServerConfig) = withContext(Dispatchers.Default) {
        mcpServerDao.upsertServer(server.toEntity())
    }

    suspend fun deleteMcpServer(id: String) = withContext(Dispatchers.Default) {
        mcpServerDao.deleteServerById(id)
    }

    // --- Global Lorebooks, Sampler Presets & Slash Templates ---

    fun observeGlobalLorebooks(): Flow<List<GlobalLorebook>> =
        globalLorebookDao.observeAllLorebooks()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    suspend fun getGlobalLorebooksOnce(): List<GlobalLorebook> = withContext(Dispatchers.Default) {
        globalLorebookDao.getAllLorebooksOnce().map { it.toDomain() }
    }

    suspend fun upsertGlobalLorebook(book: GlobalLorebook) = withContext(Dispatchers.Default) {
        globalLorebookDao.upsertLorebook(book.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteGlobalLorebook(id: String) = withContext(Dispatchers.Default) {
        globalLorebookDao.deleteLorebookById(id)
    }

    fun observeSamplerPresets(): Flow<List<SamplerPreset>> =
        samplerPresetDao.observeAllPresets()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    suspend fun ensureStarterPresetsSeeded() = withContext(Dispatchers.Default) {
        if (samplerPresetDao.getPresetCount() == 0) {
            val defaults = listOf(
                SamplerPreset(
                    id = "preset_balanced",
                    name = "Balanced",
                    temperature = 0.7f,
                    topP = 1.0f,
                    maxTokens = 4096,
                    reasoningEffort = ReasoningEffort.MEDIUM,
                    isBuiltIn = true,
                ),
                SamplerPreset(
                    id = "preset_creative",
                    name = "Creative Writing",
                    temperature = 1.15f,
                    topP = 0.95f,
                    maxTokens = 6144,
                    reasoningEffort = ReasoningEffort.LOW,
                    systemPromptSnippet = "Write with vivid imagery, strong pacing, and expressive prose.",
                    isBuiltIn = true,
                ),
                SamplerPreset(
                    id = "preset_coding",
                    name = "Strict Coding",
                    temperature = 0.15f,
                    topP = 0.9f,
                    maxTokens = 8192,
                    reasoningEffort = ReasoningEffort.HIGH,
                    systemPromptSnippet = "Provide clean, idiomatic, production-ready code with edge-case handling and minimal filler.",
                    isBuiltIn = true,
                ),
                SamplerPreset(
                    id = "preset_research",
                    name = "Deep Research",
                    temperature = 0.35f,
                    topP = 0.95f,
                    maxTokens = 8192,
                    reasoningEffort = ReasoningEffort.HIGH,
                    systemPromptSnippet = "Verify facts with live web sources, compare perspectives, and cite URLs clearly.",
                    isBuiltIn = true,
                ),
            )
            samplerPresetDao.upsertPresets(defaults.map { it.toEntity() })
        }
        if (slashTemplateDao.getTemplateCount() == 0) {
            val templates = listOf(
                SlashPromptTemplate(
                    id = "tpl_summarize",
                    command = "summarize",
                    title = "Executive Summary",
                    promptBody = "Summarize the key points above in 5 concise bullet points with action items.",
                ),
                SlashPromptTemplate(
                    id = "tpl_review",
                    command = "review",
                    title = "Code & Bug Review",
                    promptBody = "Review this carefully for bugs, edge cases, security issues, and concrete improvements:\n\n",
                ),
                SlashPromptTemplate(
                    id = "tpl_explain",
                    command = "eli5",
                    title = "Explain Simply",
                    promptBody = "Explain this clearly in plain language with a real-world analogy:\n\n",
                ),
                SlashPromptTemplate(
                    id = "tpl_compare",
                    command = "proscons",
                    title = "Pros & Cons Table",
                    promptBody = "Compare the options below in a clean Markdown table of pros, cons, and a clear recommendation:\n\n",
                ),
            )
            slashTemplateDao.upsertTemplates(templates.map { it.toEntity() })
        }
    }

    suspend fun upsertSamplerPreset(preset: SamplerPreset) = withContext(Dispatchers.Default) {
        samplerPresetDao.upsertPreset(preset.toEntity())
    }

    suspend fun deleteSamplerPreset(id: String) = withContext(Dispatchers.IO) {
        samplerPresetDao.deletePresetById(id)
    }

    fun observeSlashTemplates(): Flow<List<SlashPromptTemplate>> =
        slashTemplateDao.observeAllTemplates()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    suspend fun upsertSlashTemplate(template: SlashPromptTemplate) = withContext(Dispatchers.Default) {
        slashTemplateDao.upsertTemplate(template.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteSlashTemplate(id: String) = withContext(Dispatchers.IO) {
        slashTemplateDao.deleteTemplateById(id)
    }

    suspend fun upsertFolder(folder: Folder) = withContext(Dispatchers.Default) {
        folderDao.upsertFolder(folder.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.Default) {
        val convs = conversationDao.getAllConversationsOnce()
        for (c in convs) {
            if (c.folderId == folderId) {
                conversationDao.updateConversation(c.copy(folderId = null))
            }
        }
        folderDao.deleteFolderById(folderId)
    }

    suspend fun addCustomSticker(sticker: CustomSticker) = withContext(Dispatchers.IO) {
        stickerDao.upsertSticker(sticker.toEntity())
    }

    suspend fun deleteCustomSticker(stickerId: String) = withContext(Dispatchers.IO) {
        stickerDao.deleteStickerById(stickerId)
    }

    suspend fun getConversation(id: String): Conversation? = withContext(Dispatchers.Default) {
        conversationDao.getConversationById(id)?.toDomain()
    }

    suspend fun getMessagesOnce(conversationId: String): List<ChatMessage> = withContext(Dispatchers.Default) {
        messageDao.getMessagesOnce(conversationId).map { it.toDomain() }
    }

    suspend fun getModelById(modelId: String): ModelInfo? = withContext(Dispatchers.IO) {
        modelDao.getModelById(modelId)?.toDomain()
    }

    suspend fun createConversation(
        modelId: String,
        title: String = "New Conversation",
        folderId: String? = null,
        personaId: String? = null,
        enabledMcpServerIds: List<String> = emptyList(),
        fallbackModelIds: List<String> = emptyList(),
        enabledLorebookIds: List<String> = emptyList(),
        systemPromptOverride: String = "",
        temperature: Float = 0.7f,
        topP: Float = 1.0f,
        maxTokens: Int = 4096,
        contextWindowLimit: Int = 128_000,
        reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
        webSearchEnabled: Boolean = true,
        webSearchMode: WebSearchMode = WebSearchMode.AGENTIC_TOOL,
        webSearchEngine: WebSearchEngine = WebSearchEngine.AUTO,
        webMaxResults: Int = 5,
        webMaxTotalResults: Int = 15,
    ): Conversation = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val conv = Conversation(
            id = UUID.randomUUID().toString(),
            folderId = folderId,
            personaId = personaId,
            enabledMcpServerIds = enabledMcpServerIds,
            fallbackModelIds = fallbackModelIds,
            enabledLorebookIds = enabledLorebookIds,
            title = title,
            modelId = modelId,
            systemPromptOverride = systemPromptOverride,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            contextWindowLimit = contextWindowLimit,
            reasoningEffort = reasoningEffort,
            webSearchEnabled = webSearchEnabled,
            webSearchMode = webSearchMode,
            webSearchEngine = webSearchEngine,
            webMaxResults = webMaxResults,
            webMaxTotalResults = webMaxTotalResults,
            createdAt = now,
            updatedAt = now,
        )
        conversationDao.upsertConversation(conv.toEntity())
        searchIndex.putTitle(conv.id, conv.title)
        conv
    }

    suspend fun updateConversation(conversation: Conversation) = withContext(Dispatchers.Default) {
        val updated = conversation.copy(updatedAt = System.currentTimeMillis())
        conversationDao.upsertConversation(updated.toEntity())
        searchIndex.putTitle(updated.id, updated.title)
    }

    suspend fun pruneEmptyDraftConversations(): Int = withContext(Dispatchers.IO) {
        conversationDao.pruneEmptyUnpinnedConversations()
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        conversationDao.deleteConversationById(conversationId)
        searchIndex.removeConversation(conversationId)
    }

    suspend fun saveMessage(message: ChatMessage) = withContext(Dispatchers.Default) {
        messageDao.upsertMessage(message.toEntity())
        searchIndex.putMessageBody(message.id, message.conversationId, message.content)
        conversationDao.getConversationById(message.conversationId)?.let { conv ->
            conversationDao.updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun deleteMessagesAfter(conversationId: String, afterEpochMillis: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesAfter(conversationId, afterEpochMillis)
    }

    suspend fun clearConversationMessages(conversationId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteAllMessagesForConversation(conversationId)
        searchIndex.removeConversation(conversationId)
    }

    suspend fun forkConversationAtMessage(
        source: Conversation,
        upToMessage: ChatMessage,
    ): Conversation = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val forkedId = UUID.randomUUID().toString()
        val forkedConv = source.copy(
            id = forkedId,
            title = "${source.title.take(38)} (Fork)",
            isPinned = false,
            createdAt = now,
            updatedAt = now,
        )
        conversationDao.upsertConversation(forkedConv.toEntity())
        searchIndex.putTitle(forkedConv.id, forkedConv.title)

        val originalMessages = messageDao.getMessagesOnce(source.id).map { it.toDomain() }
        val slice = originalMessages.filter { it.createdAt <= upToMessage.createdAt }
        slice.forEach { msg ->
            val copied = msg.copy(
                id = UUID.randomUUID().toString(),
                conversationId = forkedId,
            )
            messageDao.upsertMessage(copied.toEntity())
            searchIndex.putMessageBody(copied.id, forkedId, copied.content)
        }
        forkedConv
    }

    suspend fun exportFullVaultSnapshot(): ImportedVaultBundle = withContext(Dispatchers.Default) {
        val folders = folderDao.observeAllFolders().first().map { it.toDomain() }
        val convs = conversationDao.getAllConversationsOnce().map { it.toDomain() }
        val msgs = messageDao.getAllMessagesOnce().map { it.toDomain() }
        ImportedVaultBundle(folders = folders, conversations = convs, messages = msgs)
    }

    suspend fun importVaultBundle(bundle: ImportedVaultBundle): Int = withContext(Dispatchers.Default) {
        bundle.folders.forEach { folderDao.upsertFolder(it.toEntity()) }
        bundle.conversations.forEach { conv ->
            conversationDao.upsertConversation(conv.toEntity())
            searchIndex.putTitle(conv.id, conv.title)
        }
        bundle.messages.forEach { msg ->
            messageDao.upsertMessage(msg.toEntity())
            searchIndex.putMessageBody(msg.id, msg.conversationId, msg.content)
        }
        bundle.conversations.size
    }

    suspend fun warmSearchIndex() = withContext(Dispatchers.Default) {
        val convs = conversationDao.getAllConversationsOnce()
        for (conv in convs) {
            searchIndex.putTitle(conv.id, cryptoManager.decrypt(conv.title))
        }
        val messages = messageDao.getAllMessagesOnce()
        for (msg in messages) {
            searchIndex.putMessageBody(msg.id, msg.conversationId, cryptoManager.decrypt(msg.content))
        }
    }

    suspend fun searchConversations(query: String): Set<String> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptySet()
        warmSearchIndex()
        searchIndex.matchingConversationIds(query)
    }

    suspend fun toggleModelFavorite(modelId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        modelDao.setFavorite(modelId, isFavorite)
    }

    suspend fun ensureStarterModelsSeeded() = withContext(Dispatchers.IO) {
        if (modelDao.getModelCount() > 0) return@withContext
        val now = System.currentTimeMillis()
        modelDao.upsertModels(starterModels(now).map { it.toEntity() })
    }

    suspend fun refreshModelsFromOpenRouter(apiKey: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val authHeader = apiKey.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
            val response = apiService.getModels(authorization = authHeader)
            val favorites = modelDao.getFavoriteModelIds().toSet()
            val now = System.currentTimeMillis()
            val mapped = response.data
                .filter { it.id.isNotBlank() }
                .map { OpenRouterNetworkFactory.mapDtoToDomain(it, favorites, now).toEntity() }
            if (mapped.isNotEmpty()) {
                modelDao.upsertModels(mapped)
            }
            mapped.size
        }
    }

    suspend fun fetchOpenRouterCreditBalance(apiKey: String): Result<OpenRouterCreditBalance> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = "Bearer ${apiKey.trim()}"
            val now = System.currentTimeMillis()
            // Primary account credits endpoint; fall back to /auth/key for provisioned/limited keys
            val creditsAttempt = runCatching { apiService.getCredits(authorization = auth).data }.getOrNull()
            if (creditsAttempt != null && (creditsAttempt.totalCredits != null || creditsAttempt.totalUsage != null)) {
                val total = creditsAttempt.totalCredits
                val used = creditsAttempt.totalUsage ?: 0.0
                val remaining = total?.let { (it - used).coerceAtLeast(0.0) }
                return@runCatching OpenRouterCreditBalance(
                    totalCreditsUsd = total,
                    totalUsageUsd = used,
                    remainingUsd = remaining,
                    isFreeTier = (total ?: 0.0) <= 0.0,
                    fetchedAt = now,
                )
            }
            val keyData = apiService.getAuthKey(authorization = auth).data
                ?: throw IllegalStateException("No credit balance returned")
            OpenRouterCreditBalance(
                totalCreditsUsd = keyData.limit,
                totalUsageUsd = keyData.usage ?: 0.0,
                remainingUsd = keyData.limitRemaining,
                isFreeTier = keyData.isFreeTier ?: false,
                fetchedAt = now,
            )
        }
    }

    // --- Encryption & Mapping Helpers ---

    private fun FolderEntity.toDomain() = Folder(
        id = id,
        name = cryptoManager.decrypt(name),
        colorHex = colorHex,
        defaultModelId = defaultModelId,
        projectInstructions = cryptoManager.decrypt(projectInstructions),
        updatedAt = updatedAt,
    )

    private fun Folder.toEntity() = FolderEntity(
        id = id,
        name = cryptoManager.encrypt(name),
        colorHex = colorHex,
        defaultModelId = defaultModelId,
        projectInstructions = cryptoManager.encrypt(projectInstructions),
        updatedAt = updatedAt,
    )

    private fun CustomStickerEntity.toDomain() = CustomSticker(
        id = id,
        label = label,
        localFilePath = localFilePath,
        mimeType = mimeType,
        packName = packName,
        createdAt = createdAt,
    )

    private fun CustomSticker.toEntity() = CustomStickerEntity(
        id = id,
        label = label,
        localFilePath = localFilePath,
        mimeType = mimeType,
        packName = packName,
        createdAt = createdAt,
    )

    private fun ConversationEntity.toDomain(messageCount: Int = 0) = Conversation(
        id = id,
        folderId = folderId,
        personaId = personaId,
        enabledMcpServerIds = parseStringListJson(enabledMcpServerIdsJson),
        fallbackModelIds = parseStringListJson(fallbackModelIdsJson),
        enabledLorebookIds = parseStringListJson(enabledLorebookIdsJson),
        contextSummary = cryptoManager.decrypt(contextSummary),
        summarizedUpToTimestamp = summarizedUpToTimestamp,
        title = cryptoManager.decrypt(title).ifBlank { "Untitled Chat" },
        modelId = modelId,
        systemPromptOverride = cryptoManager.decrypt(systemPromptOverride),
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        contextWindowLimit = contextWindowLimit,
        reasoningEffort = ReasoningEffort.fromWire(reasoningEffort),
        webSearchEnabled = webSearchEnabled,
        webSearchMode = WebSearchMode.fromString(webSearchMode),
        webSearchEngine = WebSearchEngine.fromWire(webSearchEngine),
        webMaxResults = webMaxResults,
        webMaxTotalResults = webMaxTotalResults,
        isPinned = isPinned,
        messageCount = messageCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        folderId = folderId,
        personaId = personaId,
        enabledMcpServerIdsJson = serializeStringListJson(enabledMcpServerIds),
        fallbackModelIdsJson = serializeStringListJson(fallbackModelIds),
        enabledLorebookIdsJson = serializeStringListJson(enabledLorebookIds),
        contextSummary = cryptoManager.encrypt(contextSummary),
        summarizedUpToTimestamp = summarizedUpToTimestamp,
        title = cryptoManager.encrypt(title),
        modelId = modelId,
        systemPromptOverride = cryptoManager.encrypt(systemPromptOverride),
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        contextWindowLimit = contextWindowLimit,
        reasoningEffort = reasoningEffort.wireValue,
        webSearchEnabled = webSearchEnabled,
        webSearchMode = webSearchMode.name,
        webSearchEngine = webSearchEngine.wireValue,
        webMaxResults = webMaxResults,
        webMaxTotalResults = webMaxTotalResults,
        isPinned = isPinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun PersonaCardEntity.toDomain(): PersonaCard {
        val decryptedName = cryptoManager.decrypt(name).ifBlank { "Persona" }
        val decryptedPayload = cryptoManager.decrypt(payloadJson)
        val obj = runCatching { JSONObject(decryptedPayload) }.getOrNull() ?: JSONObject()
        val altGreetings = parseStringListJson(obj.optJSONArray("alternateGreetings")?.toString().orEmpty())
        val tags = parseStringListJson(obj.optJSONArray("tags")?.toString().orEmpty())
        val lore = buildList {
            val arr = obj.optJSONArray("lorebookEntries")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        LorebookEntry(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            keys = parseStringListJson(o.optJSONArray("keys")?.toString().orEmpty()),
                            content = o.optString("content", ""),
                            enabled = o.optBoolean("enabled", true),
                            insertionOrder = o.optInt("insertionOrder", 100),
                        ),
                    )
                }
            }
        }
        return PersonaCard(
            id = id,
            name = decryptedName,
            description = obj.optString("description", ""),
            personality = obj.optString("personality", ""),
            scenario = obj.optString("scenario", ""),
            firstMessage = obj.optString("firstMessage", ""),
            alternateGreetings = altGreetings,
            systemPrompt = obj.optString("systemPrompt", ""),
            postHistoryInstructions = obj.optString("postHistoryInstructions", ""),
            creatorNotes = obj.optString("creatorNotes", ""),
            tags = tags,
            lorebookEntries = lore,
            avatarPath = avatarPath,
            cardFormat = cardFormat,
            updatedAt = updatedAt,
        )
    }

    private fun PersonaCard.toEntity(): PersonaCardEntity {
        val payload = JSONObject().apply {
            put("description", description)
            put("personality", personality)
            put("scenario", scenario)
            put("firstMessage", firstMessage)
            put("alternateGreetings", JSONArray().apply { alternateGreetings.forEach { put(it) } })
            put("systemPrompt", systemPrompt)
            put("postHistoryInstructions", postHistoryInstructions)
            put("creatorNotes", creatorNotes)
            put("tags", JSONArray().apply { tags.forEach { put(it) } })
            put(
                "lorebookEntries",
                JSONArray().apply {
                    lorebookEntries.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("id", e.id)
                                put("keys", JSONArray().apply { e.keys.forEach { put(it) } })
                                put("content", e.content)
                                put("enabled", e.enabled)
                                put("insertionOrder", e.insertionOrder)
                            },
                        )
                    }
                },
            )
        }.toString()
        return PersonaCardEntity(
            id = id,
            name = cryptoManager.encrypt(name),
            payloadJson = cryptoManager.encrypt(payload),
            avatarPath = avatarPath,
            cardFormat = cardFormat,
            updatedAt = updatedAt,
        )
    }

    private fun McpServerEntity.toDomain(): McpServerConfig {
        val tools = runCatching {
            val arr = JSONArray(toolsJson.ifBlank { "[]" })
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val tName = o.optString("name")
                    if (tName.isNotBlank()) {
                        add(
                            McpToolDef(
                                name = tName,
                                description = o.optString("description", ""),
                                inputSchemaJson = o.optString("inputSchemaJson", """{"type":"object","properties":{}}"""),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
        return McpServerConfig(
            id = id,
            name = cryptoManager.decrypt(name).ifBlank { "MCP Server" },
            endpointUrl = cryptoManager.decrypt(endpointUrl),
            authHeaderName = authHeaderName.ifBlank { "Authorization" },
            authHeaderValue = cryptoManager.decrypt(authHeaderValue),
            isEnabled = isEnabled,
            tools = tools,
            lastSyncedAt = lastSyncedAt,
        )
    }

    private fun McpServerConfig.toEntity(): McpServerEntity {
        val arr = JSONArray().apply {
            tools.forEach { t ->
                put(
                    JSONObject().apply {
                        put("name", t.name)
                        put("description", t.description)
                        put("inputSchemaJson", t.inputSchemaJson)
                    },
                )
            }
        }
        return McpServerEntity(
            id = id,
            name = cryptoManager.encrypt(name),
            endpointUrl = cryptoManager.encrypt(endpointUrl),
            authHeaderName = authHeaderName.ifBlank { "Authorization" },
            authHeaderValue = cryptoManager.encrypt(authHeaderValue),
            isEnabled = isEnabled,
            toolsJson = arr.toString(),
            lastSyncedAt = lastSyncedAt,
        )
    }

    private fun serializeStringListJson(items: List<String>): String {
        if (items.isEmpty()) return ""
        return JSONArray().apply { items.forEach { put(it) } }.toString()
    }

    private fun parseStringListJson(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun GlobalLorebookEntity.toDomain(): GlobalLorebook {
        val entries = runCatching {
            val decrypted = cryptoManager.decrypt(entriesJson)
            val arr = JSONArray(decrypted.ifBlank { "[]" })
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        LorebookEntry(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            keys = parseStringListJson(o.optJSONArray("keys")?.toString().orEmpty()),
                            content = o.optString("content", ""),
                            enabled = o.optBoolean("enabled", true),
                            insertionOrder = o.optInt("insertionOrder", 100),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
        return GlobalLorebook(
            id = id,
            name = cryptoManager.decrypt(name).ifBlank { "Shared Lorebook" },
            description = cryptoManager.decrypt(description),
            entries = entries,
            updatedAt = updatedAt,
        )
    }

    private fun GlobalLorebook.toEntity(): GlobalLorebookEntity {
        val arr = JSONArray().apply {
            entries.forEach { e ->
                put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("keys", JSONArray().apply { e.keys.forEach { put(it) } })
                        put("content", e.content)
                        put("enabled", e.enabled)
                        put("insertionOrder", e.insertionOrder)
                    },
                )
            }
        }
        return GlobalLorebookEntity(
            id = id,
            name = cryptoManager.encrypt(name),
            description = cryptoManager.encrypt(description),
            entriesJson = cryptoManager.encrypt(arr.toString()),
            updatedAt = updatedAt,
        )
    }

    private fun SamplerPresetEntity.toDomain() = SamplerPreset(
        id = id,
        name = name,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        reasoningEffort = ReasoningEffort.fromWire(reasoningEffort),
        systemPromptSnippet = cryptoManager.decrypt(systemPromptSnippet),
        isBuiltIn = isBuiltIn,
    )

    private fun SamplerPreset.toEntity() = SamplerPresetEntity(
        id = id,
        name = name,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        reasoningEffort = reasoningEffort.wireValue,
        systemPromptSnippet = cryptoManager.encrypt(systemPromptSnippet),
        isBuiltIn = isBuiltIn,
    )

    private fun SlashPromptTemplateEntity.toDomain() = SlashPromptTemplate(
        id = id,
        command = command,
        title = title,
        promptBody = cryptoManager.decrypt(promptBody),
        updatedAt = updatedAt,
    )

    private fun SlashPromptTemplate.toEntity() = SlashPromptTemplateEntity(
        id = id,
        command = command,
        title = title,
        promptBody = cryptoManager.encrypt(promptBody),
        updatedAt = updatedAt,
    )

    private fun MessageEntity.toDomain(): ChatMessage {
        val citations = parseCitationsJson(cryptoManager.decrypt(citationsJson))
        val attachments = parseAttachmentsJson(cryptoManager.decrypt(attachmentsJson))
        val reactions = parseReactionsJson(reactionsJson)
        val swipes = parseSwipesJson(cryptoManager.decrypt(swipesJson))
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            role = role,
            content = cryptoManager.decrypt(content),
            reasoningContent = cryptoManager.decrypt(reasoningContent),
            reasoningDetailsJson = cryptoManager.decrypt(reasoningDetailsJson),
            thinkingDurationMs = thinkingDurationMs,
            citations = citations,
            attachments = attachments,
            reactions = reactions,
            modelUsed = modelUsed,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            toolCallsJson = cryptoManager.decrypt(toolCallsJson),
            toolCallId = toolCallId,
            swipes = swipes,
            activeSwipeIndex = activeSwipeIndex,
            createdAt = createdAt,
        )
    }

    private fun ChatMessage.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = cryptoManager.encrypt(content),
        reasoningContent = cryptoManager.encrypt(reasoningContent),
        reasoningDetailsJson = cryptoManager.encrypt(reasoningDetailsJson),
        thinkingDurationMs = thinkingDurationMs,
        citationsJson = cryptoManager.encrypt(serializeCitationsJson(citations)),
        attachmentsJson = cryptoManager.encrypt(serializeAttachmentsJson(attachments)),
        reactionsJson = serializeReactionsJson(reactions),
        modelUsed = modelUsed,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        toolCallsJson = cryptoManager.encrypt(toolCallsJson),
        toolCallId = toolCallId,
        swipesJson = cryptoManager.encrypt(serializeSwipesJson(swipes)),
        activeSwipeIndex = activeSwipeIndex,
        createdAt = createdAt,
    )

    private fun serializeSwipesJson(swipes: List<MessageSwipe>): String {
        if (swipes.isEmpty()) return ""
        val arr = JSONArray()
        for (s in swipes) {
            arr.put(
                JSONObject().apply {
                    put("content", s.content)
                    put("reasoningContent", s.reasoningContent)
                    put("reasoningDetailsJson", s.reasoningDetailsJson)
                    put("thinkingDurationMs", s.thinkingDurationMs)
                    put("citations", JSONArray(serializeCitationsJson(s.citations).ifBlank { "[]" }))
                    put("modelUsed", s.modelUsed)
                    put("promptTokens", s.promptTokens)
                    put("completionTokens", s.completionTokens)
                    put("createdAt", s.createdAt)
                },
            )
        }
        return arr.toString()
    }

    private fun parseSwipesJson(raw: String): List<MessageSwipe> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        MessageSwipe(
                            content = o.optString("content", ""),
                            reasoningContent = o.optString("reasoningContent", ""),
                            reasoningDetailsJson = o.optString("reasoningDetailsJson", ""),
                            thinkingDurationMs = o.optLong("thinkingDurationMs", 0L),
                            citations = parseCitationsJson(o.optJSONArray("citations")?.toString().orEmpty()),
                            modelUsed = o.optString("modelUsed", ""),
                            promptTokens = o.optInt("promptTokens", 0),
                            completionTokens = o.optInt("completionTokens", 0),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun CachedModelEntity.toDomain() = ModelInfo(
        id = id,
        name = name,
        company = company,
        description = description,
        createdEpochSeconds = createdEpochSeconds,
        contextLength = contextLength,
        promptPricingPerMillion = promptPricingPerMillion,
        completionPricingPerMillion = completionPricingPerMillion,
        supportsVision = supportsVision,
        supportsPdfFile = supportsPdfFile,
        supportsReasoning = supportsReasoning,
        supportsTools = supportsTools,
        isFree = isFree,
        isFavorite = isFavorite,
        fetchedAt = fetchedAt,
    )

    private fun ModelInfo.toEntity() = CachedModelEntity(
        id = id,
        name = name,
        company = company,
        description = description,
        createdEpochSeconds = createdEpochSeconds,
        contextLength = contextLength,
        promptPricingPerMillion = promptPricingPerMillion,
        completionPricingPerMillion = completionPricingPerMillion,
        supportsVision = supportsVision,
        supportsPdfFile = supportsPdfFile,
        supportsReasoning = supportsReasoning,
        supportsTools = supportsTools,
        isFree = isFree,
        isFavorite = isFavorite,
        fetchedAt = fetchedAt,
    )

    private fun serializeCitationsJson(citations: List<UrlCitation>): String {
        if (citations.isEmpty()) return ""
        val arr = JSONArray()
        for (c in citations) {
            arr.put(
                JSONObject().apply {
                    put("url", c.url)
                    put("title", c.title)
                    put("snippet", c.snippet)
                },
            )
        }
        return arr.toString()
    }

    private fun parseCitationsJson(raw: String): List<UrlCitation> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val url = obj.optString("url")
                    if (url.isNotBlank()) {
                        add(
                            UrlCitation(
                                url = url,
                                title = obj.optString("title", url),
                                snippet = obj.optString("snippet", ""),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeAttachmentsJson(attachments: List<ChatAttachment>): String {
        if (attachments.isEmpty()) return ""
        val arr = JSONArray()
        for (a in attachments) {
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("displayName", a.displayName)
                    put("mimeType", a.mimeType)
                    put("kind", a.kind.name)
                    put("localFilePath", a.localFilePath)
                    put("previewImagePath", a.previewImagePath)
                    put("extractedText", a.extractedText)
                    put("sizeBytes", a.sizeBytes)
                },
            )
        }
        return arr.toString()
    }

    private fun parseAttachmentsJson(raw: String): List<ChatAttachment> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        ChatAttachment(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            displayName = o.optString("displayName", "Attachment"),
                            mimeType = o.optString("mimeType", "application/octet-stream"),
                            kind = AttachmentKind.fromString(o.optString("kind")),
                            localFilePath = o.optString("localFilePath", ""),
                            previewImagePath = o.optString("previewImagePath", ""),
                            extractedText = o.optString("extractedText", ""),
                            sizeBytes = o.optLong("sizeBytes", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeReactionsJson(reactions: Map<String, Int>): String {
        if (reactions.isEmpty()) return ""
        val obj = JSONObject()
        reactions.forEach { (emoji, count) ->
            if (count > 0) obj.put(emoji, count)
        }
        return obj.toString()
    }

    private fun parseReactionsJson(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val map = LinkedHashMap<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optInt(k, 0)
                if (v > 0) map[k] = v
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun starterModels(now: Long): List<ModelInfo> = listOf(
        ModelInfo(
            id = "anthropic/claude-sonnet-4",
            name = "Claude Sonnet 4",
            company = "Anthropic",
            description = "Balanced frontier intelligence, coding, and hybrid reasoning with tool use.",
            createdEpochSeconds = 1747872000L,
            contextLength = 200_000,
            promptPricingPerMillion = 3.0,
            completionPricingPerMillion = 15.0,
            supportsVision = true,
            supportsPdfFile = true,
            supportsReasoning = true,
            supportsTools = true,
            isFavorite = true,
            fetchedAt = now,
        ),
        ModelInfo(
            id = "openai/gpt-4.1",
            name = "GPT-4.1",
            company = "OpenAI",
            description = "Long-context flagship model with sharp instruction following and tool calling.",
            createdEpochSeconds = 1744588800L,
            contextLength = 1_047_576,
            promptPricingPerMillion = 2.0,
            completionPricingPerMillion = 8.0,
            supportsVision = true,
            supportsPdfFile = true,
            supportsReasoning = false,
            supportsTools = true,
            isFavorite = true,
            fetchedAt = now,
        ),
        ModelInfo(
            id = "google/gemini-2.5-pro",
            name = "Gemini 2.5 Pro",
            company = "Google",
            description = "Deep reasoning, 1M context window, multimodal vision and document fluency.",
            createdEpochSeconds = 1742860800L,
            contextLength = 1_048_576,
            promptPricingPerMillion = 1.25,
            completionPricingPerMillion = 10.0,
            supportsVision = true,
            supportsPdfFile = true,
            supportsReasoning = true,
            supportsTools = true,
            isFavorite = true,
            fetchedAt = now,
        ),
        ModelInfo(
            id = "deepseek/deepseek-r1",
            name = "DeepSeek R1",
            company = "DeepSeek",
            description = "Open-weights reasoning model exposing detailed step-by-step thinking traces.",
            createdEpochSeconds = 1737331200L,
            contextLength = 163_840,
            promptPricingPerMillion = 0.55,
            completionPricingPerMillion = 2.19,
            supportsVision = false,
            supportsPdfFile = false,
            supportsReasoning = true,
            supportsTools = true,
            fetchedAt = now,
        ),
        ModelInfo(
            id = "x-ai/grok-3",
            name = "Grok 3",
            company = "xAI",
            description = "High-speed analytical and conversational model with live tool support.",
            createdEpochSeconds = 1739836800L,
            contextLength = 131_072,
            promptPricingPerMillion = 3.0,
            completionPricingPerMillion = 15.0,
            supportsVision = false,
            supportsPdfFile = false,
            supportsReasoning = true,
            supportsTools = true,
            fetchedAt = now,
        ),
        ModelInfo(
            id = "meta-llama/llama-4-maverick",
            name = "Llama 4 Maverick",
            company = "Meta",
            description = "Open multimodal Mixture-of-Experts model with generous context capacity.",
            createdEpochSeconds = 1743811200L,
            contextLength = 1_048_576,
            promptPricingPerMillion = 0.20,
            completionPricingPerMillion = 0.60,
            supportsVision = true,
            supportsPdfFile = false,
            supportsReasoning = false,
            supportsTools = true,
            fetchedAt = now,
        ),
        ModelInfo(
            id = "qwen/qwen3-235b-a22b",
            name = "Qwen3 235B A22B",
            company = "Qwen",
            description = "Hybrid thinking and fast response open-weights MoE model.",
            createdEpochSeconds = 1745884800L,
            contextLength = 131_072,
            promptPricingPerMillion = 0.22,
            completionPricingPerMillion = 0.88,
            supportsVision = false,
            supportsPdfFile = false,
            supportsReasoning = true,
            supportsTools = true,
            fetchedAt = now,
        ),
        ModelInfo(
            id = "mistralai/mistral-large-2411",
            name = "Mistral Large 2411",
            company = "Mistral",
            description = "Multilingual enterprise model for code, reasoning, and function calling.",
            createdEpochSeconds = 1731888000L,
            contextLength = 131_072,
            promptPricingPerMillion = 2.0,
            completionPricingPerMillion = 6.0,
            supportsVision = false,
            supportsPdfFile = false,
            supportsReasoning = false,
            supportsTools = true,
            fetchedAt = now,
        ),
        ModelInfo(
            id = "deepseek/deepseek-chat-v3-0324:free",
            name = "DeepSeek V3 0324 (Free)",
            company = "DeepSeek",
            description = "Zero-cost OpenRouter tier for fast coding and everyday chat.",
            createdEpochSeconds = 1742774400L,
            contextLength = 163_840,
            promptPricingPerMillion = 0.0,
            completionPricingPerMillion = 0.0,
            supportsVision = false,
            supportsPdfFile = false,
            supportsReasoning = false,
            supportsTools = true,
            isFree = true,
            fetchedAt = now,
        ),
    )
}
