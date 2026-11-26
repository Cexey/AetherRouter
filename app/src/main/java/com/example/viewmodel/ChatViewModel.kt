package com.example.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.DocumentExtractor
import com.example.core.ExportManager
import com.example.core.LiveClockProvider
import com.example.core.LiveClockSnapshot
import com.example.core.PersonaCardEngine
import com.example.core.SmartReplyGenerator
import com.example.core.VoiceEngineManager
import com.example.core.VoiceUiState
import com.example.data.local.PreferencesRepository
import com.example.data.model.AppPreferences
import com.example.data.model.AppearanceConfig
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.CreatorProfile
import com.example.data.model.CustomSticker
import com.example.data.model.ExportFormat
import com.example.data.model.Folder
import com.example.data.model.LinkOpenBehavior
import com.example.data.model.McpServerConfig
import com.example.data.model.McpToolDef
import com.example.data.model.ModelInfo
import com.example.data.model.PersonaCard
import com.example.data.model.ReasoningEffort
import com.example.data.model.ThemeMode
import com.example.data.model.UrlCitation
import com.example.data.model.WebSearchEngine
import com.example.data.model.WebSearchMode
import com.example.data.repository.VaultRepository
import com.example.network.LiveWebFetcher
import com.example.network.McpJsonRpcClient
import com.example.network.OpenRouterStreamClient
import com.example.network.OpenRouterStreamEvent
import com.example.network.ToolCallPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class StreamingUiState(
    val isStreaming: Boolean = false,
    val statusNote: String = "",
    val activeToolName: String = "",
    val content: String = "",
    val reasoningText: String = "",
    val reasoningDetailsJson: String = "",
    val hasEncryptedSignature: Boolean = false,
    val citations: List<UrlCitation> = emptyList(),
    val thinkingStartedAtMs: Long = 0L,
    val thinkingElapsedMs: Long = 0L,
    val modelId: String = "",
)

data class ChatWorkspaceUiState(
    val preferences: AppPreferences = AppPreferences(),
    val clock: LiveClockSnapshot = LiveClockProvider.captureSnapshot(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderFilterId: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val drawerSearchQuery: String = "",
    val activeConversation: Conversation? = null,
    val activeModel: ModelInfo? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streaming: StreamingUiState = StreamingUiState(),
    val composerText: String = "",
    val stagedAttachments: List<ChatAttachment> = emptyList(),
    val customStickers: List<CustomSticker> = emptyList(),
    val isProcessingAttachment: Boolean = false,
    val isFetchingWebpage: Boolean = false,
    val snackbarMessage: String? = null,
    val inAppBrowserUrl: String? = null,
    val pendingLinkChoiceUrl: String? = null,
    val personas: List<PersonaCard> = emptyList(),
    val activePersona: PersonaCard? = null,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val isSyncingMcpServerId: String? = null,
    val voiceState: VoiceUiState = VoiceUiState(),
    val smartReplies: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val vaultRepository: VaultRepository,
    private val preferencesRepository: PreferencesRepository,
    private val streamClient: OpenRouterStreamClient,
    private val liveWebFetcher: LiveWebFetcher,
    private val liveClockProvider: LiveClockProvider,
    private val documentExtractor: DocumentExtractor,
    private val exportManager: ExportManager,
    private val personaCardEngine: PersonaCardEngine,
    private val mcpClient: McpJsonRpcClient,
    private val voiceEngineManager: VoiceEngineManager,
) : ViewModel() {

    private val activeConversationId = MutableStateFlow<String?>(null)
    private val selectedFolderFilterId = MutableStateFlow<String?>(null)
    private val drawerSearchQuery = MutableStateFlow("")
    private val searchMatchingIds = MutableStateFlow<Set<String>>(emptySet())
    private val streamingState = MutableStateFlow(StreamingUiState())
    private val composerText = MutableStateFlow("")
    private val stagedAttachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    private val isProcessingAttachment = MutableStateFlow(false)
    private val isFetchingWebpage = MutableStateFlow(false)
    private val isSyncingMcpServerId = MutableStateFlow<String?>(null)
    private val snackbarMessage = MutableStateFlow<String?>(null)
    private val inAppBrowserUrl = MutableStateFlow<String?>(null)
    private val pendingLinkChoiceUrl = MutableStateFlow<String?>(null)

    private var activeStreamJob: Job? = null
    private var searchJob: Job? = null
    private var autoSpeakNextResponse: Boolean = false

    private val activeConversationFlow = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(null) else vaultRepository.observeConversation(id)
    }

    private val activeMessagesFlow = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else vaultRepository.observeMessages(id)
    }

    val preferencesState: StateFlow<AppPreferences> = preferencesRepository.appPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    private val filteredConversationsFlow = combine(
        vaultRepository.observeConversations(),
        selectedFolderFilterId,
        drawerSearchQuery,
        searchMatchingIds,
    ) { allConvs, folderFilter, query, matches ->
        val byFolder = if (folderFilter == null) allConvs else allConvs.filter { it.folderId == folderFilter }
        val bySearch = if (query.isBlank()) byFolder else byFolder.filter { it.id in matches }
        bySearch.sortedByDescending { it.isPinned }
    }

    private val baseDataFlow = combine(
        preferencesState,
        liveClockProvider.clockState,
        filteredConversationsFlow,
        activeConversationFlow,
        vaultRepository.observeModels(),
    ) { prefs, clock, convs, activeConv, models ->
        val modelId = activeConv?.modelId ?: prefs.defaultModelId
        val activeModel = models.firstOrNull { it.id == modelId }
        Tuple5(prefs, clock, convs, activeConv, activeModel)
    }

    private val interactionFlow = combine(
        activeMessagesFlow,
        streamingState,
        composerText,
        drawerSearchQuery,
        isFetchingWebpage,
    ) { msgs, stream, text, search, fetching ->
        Tuple5(msgs, stream, text, search, fetching)
    }

    private val mediaAndFolderFlow = combine(
        vaultRepository.observeFolders(),
        selectedFolderFilterId,
        stagedAttachments,
        vaultRepository.observeCustomStickers(),
        isProcessingAttachment,
    ) { folders, folderFilter, staged, stickers, processing ->
        Tuple5(folders, folderFilter, staged, stickers, processing)
    }

    private val overlayFlow = combine(
        snackbarMessage,
        inAppBrowserUrl,
        pendingLinkChoiceUrl,
    ) { snack, browserUrl, choiceUrl ->
        Triple(snack, browserUrl, choiceUrl)
    }

    private val powerToolsFlow = combine(
        vaultRepository.observePersonas(),
        vaultRepository.observeMcpServers(),
        isSyncingMcpServerId,
        voiceEngineManager.state,
    ) { personas, mcpServers, syncingId, voice ->
        Tuple4(personas, mcpServers, syncingId, voice)
    }

    val uiState: StateFlow<ChatWorkspaceUiState> = combine(
        baseDataFlow,
        interactionFlow,
        mediaAndFolderFlow,
        overlayFlow,
        powerToolsFlow,
    ) { base, interaction, mediaFolder, overlay, power ->
        val prefs = base.a
        val activeConv = base.d
        val msgs = interaction.a
        val stream = interaction.b
        val activePersona = activeConv?.personaId?.let { pid -> power.a.firstOrNull { it.id == pid } }
        val smartReplies = if (prefs.smartRepliesEnabled && !stream.isStreaming) {
            SmartReplyGenerator.generateSuggestions(msgs)
        } else {
            emptyList()
        }
        ChatWorkspaceUiState(
            preferences = prefs,
            clock = base.b,
            folders = mediaFolder.a,
            selectedFolderFilterId = mediaFolder.b,
            conversations = base.c,
            drawerSearchQuery = interaction.d,
            activeConversation = activeConv,
            activeModel = base.e,
            messages = msgs,
            streaming = stream,
            composerText = interaction.c,
            stagedAttachments = mediaFolder.c,
            customStickers = mediaFolder.d,
            isProcessingAttachment = mediaFolder.e,
            isFetchingWebpage = interaction.e,
            snackbarMessage = overlay.first,
            inAppBrowserUrl = overlay.second,
            pendingLinkChoiceUrl = overlay.third,
            personas = power.a,
            activePersona = activePersona,
            mcpServers = power.b,
            isSyncingMcpServerId = power.c,
            voiceState = power.d,
            smartReplies = smartReplies,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatWorkspaceUiState(),
    )

    init {
        viewModelScope.launch {
            vaultRepository.ensureStarterModelsSeeded()
            val existing = vaultRepository.observeConversations().first()
            if (existing.isNotEmpty() && activeConversationId.value == null) {
                activeConversationId.value = existing.first().id
            }
        }
    }

    fun updateComposerText(text: String) {
        composerText.value = text
    }

    fun updateDrawerSearch(query: String) {
        drawerSearchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            searchMatchingIds.value = emptySet()
            return
        }
        searchJob = viewModelScope.launch {
            searchMatchingIds.value = vaultRepository.searchConversations(query)
        }
    }

    fun selectConversation(conversationId: String) {
        if (streamingState.value.isStreaming) stopStreaming()
        activeConversationId.value = conversationId
    }

    fun startNewConversation() {
        if (streamingState.value.isStreaming) stopStreaming()
        viewModelScope.launch {
            val prefs = preferencesState.value
            val folderId = selectedFolderFilterId.value
            val folder = folderId?.let { id ->
                vaultRepository.observeFolders().first().firstOrNull { it.id == id }
            }
            val modelId = folder?.defaultModelId?.takeIf { it.isNotBlank() } ?: prefs.defaultModelId
            val created = vaultRepository.createConversation(
                modelId = modelId,
                folderId = folderId,
                webSearchEnabled = prefs.defaultWebSearchEnabled,
                webSearchMode = prefs.defaultWebSearchMode,
                webSearchEngine = prefs.defaultWebSearchEngine,
                webMaxResults = prefs.defaultWebMaxResults,
                webMaxTotalResults = prefs.defaultWebMaxTotalResults,
            )
            activeConversationId.value = created.id
            composerText.value = ""
            stagedAttachments.value = emptyList()
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            vaultRepository.deleteConversation(conversationId)
            if (activeConversationId.value == conversationId) {
                val remaining = vaultRepository.observeConversations().first()
                activeConversationId.value = remaining.firstOrNull()?.id
            }
            showSnackbar("Conversation deleted")
        }
    }

    fun selectModelForActiveConversation(model: ModelInfo) {
        viewModelScope.launch {
            preferencesRepository.setDefaultModelId(model.id)
            val current = activeConversationId.value?.let { vaultRepository.getConversation(it) }
            if (current != null) {
                vaultRepository.updateConversation(
                    current.copy(
                        modelId = model.id,
                        contextWindowLimit = model.contextLength.coerceAtLeast(8_192),
                    ),
                )
            }
            showSnackbar("Switched model to ${model.name}")
        }
    }

    fun toggleQuickWebSearch() {
        viewModelScope.launch {
            val current = ensureActiveConversation()
            val nextEnabled = !current.webSearchEnabled
            vaultRepository.updateConversation(current.copy(webSearchEnabled = nextEnabled))
            preferencesRepository.setDefaultWebConfig(
                enabled = nextEnabled,
                mode = current.webSearchMode,
                engine = current.webSearchEngine,
                maxResults = current.webMaxResults,
                maxTotalResults = current.webMaxTotalResults,
            )
            val modeLabel = if (current.webSearchMode == WebSearchMode.AGENTIC_TOOL) "Agentic" else "Always-On"
            showSnackbar(if (nextEnabled) "Live Web enabled ($modeLabel)" else "Live Web paused")
        }
    }

    fun updateConversationSettings(
        systemPrompt: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
        reasoningEffort: ReasoningEffort,
        webSearchEnabled: Boolean,
        webSearchMode: WebSearchMode,
        webSearchEngine: WebSearchEngine,
        webMaxResults: Int,
        webMaxTotalResults: Int,
    ) {
        viewModelScope.launch {
            val current = ensureActiveConversation()
            vaultRepository.updateConversation(
                current.copy(
                    systemPromptOverride = systemPrompt.trim(),
                    temperature = temperature.coerceIn(0f, 2f),
                    topP = topP.coerceIn(0.05f, 1f),
                    maxTokens = maxTokens.coerceIn(256, 65536),
                    reasoningEffort = reasoningEffort,
                    webSearchEnabled = webSearchEnabled,
                    webSearchMode = webSearchMode,
                    webSearchEngine = webSearchEngine,
                    webMaxResults = webMaxResults.coerceIn(1, 10),
                    webMaxTotalResults = webMaxTotalResults.coerceIn(5, 30),
                ),
            )
            preferencesRepository.setDefaultWebConfig(
                enabled = webSearchEnabled,
                mode = webSearchMode,
                engine = webSearchEngine,
                maxResults = webMaxResults,
                maxTotalResults = webMaxTotalResults,
            )
            showSnackbar("Chat & Web settings saved")
        }
    }

    fun sendMessage(overrideText: String? = null) {
        val text = (overrideText ?: composerText.value).trim()
        val attachmentsToSend = if (overrideText == null) stagedAttachments.value else emptyList()
        if ((text.isEmpty() && attachmentsToSend.isEmpty()) || streamingState.value.isStreaming) return

        viewModelScope.launch {
            val apiKey = preferencesRepository.getDecryptedApiKey()
            if (apiKey.isBlank()) {
                showSnackbar("Add your OpenRouter API key in Settings to send messages")
                return@launch
            }

            var conv = ensureActiveConversation()
            val existingMessages = vaultRepository.getMessagesOnce(conv.id)
            if (existingMessages.isEmpty() && conv.title == "New Conversation") {
                val seedTitle = text.ifBlank { attachmentsToSend.firstOrNull()?.displayName.orEmpty() }
                val autoTitle = seedTitle.replace('\n', ' ').take(48).trim().ifBlank { "New Conversation" }
                conv = conv.copy(title = autoTitle)
                vaultRepository.updateConversation(conv)
            }

            val now = System.currentTimeMillis()
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                role = "user",
                content = text,
                attachments = attachmentsToSend,
                createdAt = now,
            )
            vaultRepository.saveMessage(userMsg)
            if (overrideText == null) {
                composerText.value = ""
                stagedAttachments.value = emptyList()
            }
            launchCompletionStream(apiKey = apiKey, conversation = conv)
        }
    }

    fun regenerateAssistantMessage(message: ChatMessage) {
        if (streamingState.value.isStreaming) return
        viewModelScope.launch {
            val apiKey = preferencesRepository.getDecryptedApiKey()
            if (apiKey.isBlank()) {
                showSnackbar("Add your OpenRouter API key first")
                return@launch
            }
            val conv = vaultRepository.getConversation(message.conversationId) ?: return@launch
            vaultRepository.deleteMessagesAfter(conv.id, message.createdAt - 1L)
            launchCompletionStream(apiKey = apiKey, conversation = conv)
        }
    }

    fun editMessage(message: ChatMessage, updatedContent: String, reRunIfUser: Boolean = false) {
        val clean = updatedContent.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            vaultRepository.saveMessage(message.copy(content = clean))
            if (reRunIfUser && message.role == "user") {
                val apiKey = preferencesRepository.getDecryptedApiKey()
                if (apiKey.isBlank()) {
                    showSnackbar("Message updated (add API key to regenerate)")
                    return@launch
                }
                val conv = vaultRepository.getConversation(message.conversationId) ?: return@launch
                vaultRepository.deleteMessagesAfter(conv.id, message.createdAt)
                launchCompletionStream(apiKey = apiKey, conversation = conv)
            } else {
                showSnackbar("Message updated")
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            vaultRepository.deleteMessage(messageId)
            showSnackbar("Message deleted")
        }
    }

    fun stopStreaming() {
        val current = streamingState.value
        activeStreamJob?.cancel()
        activeStreamJob = null
        if (current.isStreaming && (current.content.isNotBlank() || current.reasoningText.isNotBlank())) {
            val convId = activeConversationId.value
            if (convId != null) {
                viewModelScope.launch {
                    val partial = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = current.content.ifBlank { "(Stopped before final response)" },
                        reasoningContent = current.reasoningText,
                        reasoningDetailsJson = current.reasoningDetailsJson,
                        thinkingDurationMs = current.thinkingElapsedMs,
                        citations = current.citations,
                        modelUsed = current.modelId,
                        createdAt = System.currentTimeMillis(),
                    )
                    vaultRepository.saveMessage(partial)
                }
            }
        }
        streamingState.value = StreamingUiState()
    }

    private fun launchCompletionStream(
        apiKey: String,
        conversation: Conversation,
        maxToolDepth: Int = 3,
    ) {
        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            val prefs = preferencesState.value
            val modelInfo = vaultRepository.getModelById(conversation.modelId)
            val supportsTools = modelInfo?.supportsTools ?: true
            val clockSnapshot = liveClockProvider.refreshNow()

            val initialStatus = when {
                !conversation.webSearchEnabled -> "Connecting to OpenRouter…"
                conversation.webSearchMode == WebSearchMode.ALWAYS_ON_PLUGIN || !supportsTools ->
                    "Searching live web & generating…"
                else -> "Thinking (Live Web tools ready)…"
            }

            streamingState.value = StreamingUiState(
                isStreaming = true,
                statusNote = initialStatus,
                modelId = conversation.modelId,
            )

            val folderInstructions = conversation.folderId?.let { fId ->
                vaultRepository.observeFolders().first().firstOrNull { it.id == fId }?.projectInstructions.orEmpty()
            }.orEmpty()

            val activePersona = conversation.personaId?.let { vaultRepository.getPersonaById(it) }
            val enabledServers = vaultRepository.getMcpServersOnce()
                .filter { it.isEnabled && it.id in conversation.enabledMcpServerIds && it.tools.isNotEmpty() }
            val mcpToolLookup = LinkedHashMap<String, Pair<McpServerConfig, McpToolDef>>()
            val mcpWireTools = buildList {
                for (server in enabledServers) {
                    for (tool in server.tools) {
                        val wireName = mcpClient.makeWireToolName(server.id, tool.name)
                        mcpToolLookup[wireName] = server to tool
                        add(wireName to tool)
                    }
                }
            }

            try {
                var depth = 0
                while (depth <= maxToolDepth) {
                    val history = vaultRepository.getMessagesOnce(conversation.id)
                    val resolvedPersona = activePersona?.let { persona ->
                        personaCardEngine.buildResolvedPrompt(
                            persona = persona,
                            history = history,
                            userDisplayName = prefs.creatorProfile.displayName,
                            clockSnapshot = clockSnapshot,
                            use24HourClock = prefs.use24HourClock,
                        )
                    }
                    var pendingToolCalls: Pair<List<ToolCallPayload>, String>? = null
                    var finalResult: OpenRouterStreamEvent.Completed? = null

                    streamClient.streamChatCompletion(
                        apiKey = apiKey,
                        conversation = conversation,
                        history = history,
                        modelSupportsTools = supportsTools,
                        clockSnapshot = clockSnapshot,
                        use24HourClock = prefs.use24HourClock,
                        projectInstructions = folderInstructions,
                        personaSystemBlock = resolvedPersona?.systemBlock.orEmpty(),
                        postHistoryInstructions = resolvedPersona?.postHistoryInstructions.orEmpty(),
                        mcpWireTools = mcpWireTools,
                        includeLocalWebFetchTool = true,
                    ).collect { event ->
                        when (event) {
                            is OpenRouterStreamEvent.ContentDelta -> {
                                val prev = streamingState.value
                                val elapsed = if (prev.thinkingStartedAtMs > 0L && prev.thinkingElapsedMs == 0L) {
                                    (System.currentTimeMillis() - prev.thinkingStartedAtMs).coerceAtLeast(100L)
                                } else {
                                    prev.thinkingElapsedMs
                                }
                                streamingState.value = prev.copy(
                                    statusNote = "",
                                    activeToolName = "",
                                    content = prev.content + event.text,
                                    thinkingElapsedMs = elapsed,
                                )
                            }
                            is OpenRouterStreamEvent.ReasoningDelta -> {
                                val prev = streamingState.value
                                val started = if (prev.thinkingStartedAtMs == 0L) System.currentTimeMillis() else prev.thinkingStartedAtMs
                                streamingState.value = prev.copy(
                                    statusNote = "Reasoning…",
                                    reasoningText = prev.reasoningText + event.text,
                                    thinkingStartedAtMs = started,
                                    thinkingElapsedMs = (System.currentTimeMillis() - started).coerceAtLeast(100L),
                                )
                            }
                            is OpenRouterStreamEvent.ReasoningDetailsUpdated -> {
                                val prev = streamingState.value
                                val started = if (prev.thinkingStartedAtMs == 0L) System.currentTimeMillis() else prev.thinkingStartedAtMs
                                streamingState.value = prev.copy(
                                    reasoningDetailsJson = event.rawJsonArray,
                                    hasEncryptedSignature = prev.hasEncryptedSignature || event.hasEncryptedSignature,
                                    thinkingStartedAtMs = started,
                                )
                            }
                            is OpenRouterStreamEvent.CitationsUpdated -> {
                                streamingState.value = streamingState.value.copy(citations = event.citations)
                            }
                            is OpenRouterStreamEvent.ToolCallsRequested -> {
                                pendingToolCalls = event.calls to event.rawToolCallsJson
                            }
                            is OpenRouterStreamEvent.UsageUpdated -> Unit
                            is OpenRouterStreamEvent.Completed -> {
                                finalResult = event
                            }
                        }
                    }

                    val completed = finalResult
                    val callsPair = pendingToolCalls
                    if (callsPair != null && depth < maxToolDepth) {
                        val (calls, rawJson) = callsPair
                        // Persist intermediate assistant message WITH intact reasoning_details for lossless replay!
                        val assistantToolMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversation.id,
                            role = "assistant",
                            content = completed?.fullContent.orEmpty(),
                            reasoningContent = completed?.fullReasoning.orEmpty(),
                            reasoningDetailsJson = completed?.reasoningDetailsJson.orEmpty(),
                            thinkingDurationMs = streamingState.value.thinkingElapsedMs,
                            citations = completed?.citations.orEmpty(),
                            modelUsed = conversation.modelId,
                            toolCallsJson = rawJson,
                            createdAt = System.currentTimeMillis(),
                        )
                        vaultRepository.saveMessage(assistantToolMsg)

                        // Execute local web fetch or remote MCP JSON-RPC tools
                        for (call in calls) {
                            val mcpEntry = mcpToolLookup[call.name]
                            val toolLabel = if (mcpEntry != null) {
                                "${mcpEntry.first.name} • ${mcpEntry.second.name}"
                            } else {
                                call.name
                            }
                            streamingState.value = streamingState.value.copy(
                                statusNote = "Running $toolLabel…",
                                activeToolName = toolLabel,
                            )
                            val toolOutput = executeLocalToolCall(call, mcpToolLookup)
                            val toolMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                conversationId = conversation.id,
                                role = "tool",
                                content = "[Tool: $toolLabel]\n$toolOutput",
                                toolCallId = call.id,
                                createdAt = System.currentTimeMillis(),
                            )
                            vaultRepository.saveMessage(toolMsg)
                        }

                        // Reset visible streaming buffer for follow-up synthesis turn
                        streamingState.value = StreamingUiState(
                            isStreaming = true,
                            statusNote = "Synthesizing tool results…",
                            activeToolName = "",
                            modelId = conversation.modelId,
                        )
                        depth++
                        continue
                    }

                    if (completed != null) {
                        val duration = streamingState.value.thinkingElapsedMs.takeIf { it > 0L }
                            ?: if (streamingState.value.thinkingStartedAtMs > 0L) {
                                (System.currentTimeMillis() - streamingState.value.thinkingStartedAtMs).coerceAtLeast(100L)
                            } else 0L

                        val savedAssistant = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversation.id,
                            role = "assistant",
                            content = completed.fullContent.ifBlank {
                                if (completed.fullReasoning.isNotBlank() || completed.reasoningDetailsJson.isNotBlank()) {
                                    "(Model returned reasoning without final text)"
                                } else {
                                    "(Empty response)"
                                }
                            },
                            reasoningContent = completed.fullReasoning,
                            reasoningDetailsJson = completed.reasoningDetailsJson,
                            thinkingDurationMs = duration,
                            citations = completed.citations,
                            modelUsed = conversation.modelId,
                            promptTokens = completed.promptTokens,
                            completionTokens = completed.completionTokens,
                            createdAt = System.currentTimeMillis(),
                        )
                        vaultRepository.saveMessage(savedAssistant)
                        if (autoSpeakNextResponse) {
                            autoSpeakNextResponse = false
                            voiceEngineManager.speakText(savedAssistant.id, savedAssistant.content)
                        }
                    }
                    break
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                autoSpeakNextResponse = false
                showSnackbar(e.message ?: "Request failed")
            } finally {
                streamingState.value = StreamingUiState()
            }
        }
    }

    private suspend fun executeLocalToolCall(
        call: ToolCallPayload,
        mcpToolLookup: Map<String, Pair<McpServerConfig, McpToolDef>>,
    ): String {
        val mcpTarget = mcpToolLookup[call.name]
        if (mcpTarget != null) {
            val (server, tool) = mcpTarget
            return mcpClient.callTool(server, tool.name, call.argumentsJson).fold(
                onSuccess = { it },
                onFailure = { err -> "MCP tool error: ${err.message ?: "Unable to reach server"}" },
            )
        }
        return when (call.name) {
            "fetch_webpage" -> {
                val url = runCatching { JSONObject(call.argumentsJson).optString("url") }.getOrDefault("")
                if (url.isBlank()) {
                    "Error: Missing url parameter."
                } else {
                    liveWebFetcher.fetchPage(url).fold(
                        onSuccess = { page ->
                            "Title: ${page.title}\nURL: ${page.url}\n\n${page.readableText}"
                        },
                        onFailure = { err ->
                            "Error fetching webpage: ${err.message ?: "Unable to connect"}"
                        },
                    )
                }
            }
            else -> "Tool '${call.name}' is not registered locally."
        }
    }

    fun attachWebpageToComposer(rawUrl: String) {
        if (isFetchingWebpage.value) return
        viewModelScope.launch {
            isFetchingWebpage.value = true
            liveWebFetcher.fetchPage(rawUrl).fold(
                onSuccess = { page ->
                    val att = ChatAttachment(
                        id = UUID.randomUUID().toString(),
                        displayName = page.title.ifBlank { page.url }.take(64),
                        mimeType = "text/html",
                        kind = AttachmentKind.WEBPAGE,
                        extractedText = "URL: ${page.url}\nTitle: ${page.title}\n\n${page.readableText}",
                        sizeBytes = page.readableText.length.toLong(),
                    )
                    stagedAttachments.value = stagedAttachments.value + att
                    inAppBrowserUrl.value = null
                    pendingLinkChoiceUrl.value = null
                    showSnackbar("Attached live webpage: ${page.title}")
                },
                onFailure = { err ->
                    showSnackbar("Failed to read page: ${err.message ?: "Network error"}")
                },
            )
            isFetchingWebpage.value = false
        }
    }

    // --- Rich Media, Documents, Stickers & Emoji Reactions ---

    fun addAttachmentsFromUris(uris: List<Uri>, forcedKind: AttachmentKind? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isProcessingAttachment.value = true
            var added = 0
            var lastError: String? = null
            for (uri in uris) {
                documentExtractor.ingestUri(uri, forcedKind).fold(
                    onSuccess = { att ->
                        stagedAttachments.value = stagedAttachments.value + att
                        added++
                    },
                    onFailure = { err ->
                        lastError = err.message
                    },
                )
            }
            isProcessingAttachment.value = false
            when {
                added > 0 && lastError == null ->
                    showSnackbar(if (added == 1) "Attached 1 file" else "Attached $added files")
                added > 0 ->
                    showSnackbar("Attached $added file(s); one skipped: $lastError")
                else ->
                    showSnackbar("Attachment failed: ${lastError ?: "Unsupported file"}")
            }
        }
    }

    fun removeStagedAttachment(id: String) {
        stagedAttachments.value = stagedAttachments.value.filterNot { it.id == id }
    }

    fun attachCustomSticker(sticker: CustomSticker) {
        val kind = if (sticker.mimeType.equals("image/gif", ignoreCase = true)) {
            AttachmentKind.GIF
        } else {
            AttachmentKind.STICKER
        }
        val att = ChatAttachment(
            id = UUID.randomUUID().toString(),
            displayName = sticker.label,
            mimeType = sticker.mimeType,
            kind = kind,
            localFilePath = sticker.localFilePath,
            previewImagePath = sticker.localFilePath,
        )
        stagedAttachments.value = stagedAttachments.value + att
    }

    fun importCustomStickerFromUri(uri: Uri) {
        viewModelScope.launch {
            isProcessingAttachment.value = true
            documentExtractor.ingestUri(uri).fold(
                onSuccess = { att ->
                    val sticker = CustomSticker(
                        id = UUID.randomUUID().toString(),
                        label = att.displayName.substringBeforeLast('.').take(24).ifBlank { "Sticker" },
                        localFilePath = att.localFilePath,
                        mimeType = att.mimeType,
                    )
                    vaultRepository.addCustomSticker(sticker)
                    showSnackbar("Saved sticker: ${sticker.label}")
                },
                onFailure = { err ->
                    showSnackbar("Could not import sticker: ${err.message}")
                },
            )
            isProcessingAttachment.value = false
        }
    }

    fun deleteCustomSticker(stickerId: String) {
        viewModelScope.launch {
            vaultRepository.deleteCustomSticker(stickerId)
            showSnackbar("Sticker removed")
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            val next = LinkedHashMap(message.reactions)
            val current = next[emoji] ?: 0
            if (current > 0) {
                next.remove(emoji)
            } else {
                next[emoji] = 1
            }
            vaultRepository.saveMessage(message.copy(reactions = next))
        }
    }

    // --- Projects & Folders ---

    fun selectFolderFilter(folderId: String?) {
        selectedFolderFilterId.value = folderId
    }

    fun saveFolder(
        id: String?,
        name: String,
        colorHex: String,
        defaultModelId: String,
        instructions: String,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val folder = Folder(
                id = id ?: UUID.randomUUID().toString(),
                name = cleanName,
                colorHex = colorHex.ifBlank { "#5B7CFA" },
                defaultModelId = defaultModelId.trim(),
                projectInstructions = instructions.trim(),
            )
            vaultRepository.upsertFolder(folder)
            showSnackbar(if (id == null) "Created project: $cleanName" else "Updated project: $cleanName")
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            vaultRepository.deleteFolder(folderId)
            if (selectedFolderFilterId.value == folderId) {
                selectedFolderFilterId.value = null
            }
            showSnackbar("Project deleted (chats moved to Unfiled)")
        }
    }

    fun moveConversationToFolder(conversationId: String, folderId: String?) {
        viewModelScope.launch {
            val conv = vaultRepository.getConversation(conversationId) ?: return@launch
            vaultRepository.updateConversation(conv.copy(folderId = folderId))
            showSnackbar(if (folderId == null) "Moved to All Chats" else "Moved to project")
        }
    }

    fun togglePinConversation(conversationId: String) {
        viewModelScope.launch {
            val conv = vaultRepository.getConversation(conversationId) ?: return@launch
            val pinned = !conv.isPinned
            vaultRepository.updateConversation(conv.copy(isPinned = pinned))
            showSnackbar(if (pinned) "Pinned conversation" else "Unpinned conversation")
        }
    }

    // --- Link Handling & In-App Browser ---

    fun handleUrlClick(rawUrl: String, onLaunchExternal: (String) -> Unit) {
        val clean = rawUrl.trim()
        if (!liveWebFetcher.isSafeExternalUrl(clean)) {
            showSnackbar("Blocked unsupported or unsafe URL")
            return
        }
        when (preferencesState.value.linkOpenBehavior) {
            LinkOpenBehavior.IN_APP -> inAppBrowserUrl.value = clean
            LinkOpenBehavior.ASK -> pendingLinkChoiceUrl.value = clean
            LinkOpenBehavior.EXTERNAL -> onLaunchExternal(clean)
        }
    }

    fun openInAppBrowserDirectly(url: String) {
        pendingLinkChoiceUrl.value = null
        if (liveWebFetcher.isSafeExternalUrl(url)) {
            inAppBrowserUrl.value = url
        }
    }

    fun dismissInAppBrowser() {
        inAppBrowserUrl.value = null
    }

    fun dismissLinkChooser() {
        pendingLinkChoiceUrl.value = null
    }

    // --- Settings & API Key Actions ---

    fun saveApiKeyAndSync(rawKey: String) {
        viewModelScope.launch {
            preferencesRepository.saveApiKey(rawKey)
            val trimmed = rawKey.trim()
            if (trimmed.isEmpty()) {
                showSnackbar("API key cleared from encrypted vault")
                return@launch
            }
            showSnackbar("API key encrypted in Android Keystore. Syncing models…")
            vaultRepository.refreshModelsFromOpenRouter(trimmed).fold(
                onSuccess = { count -> showSnackbar("Connected! Synced $count OpenRouter models") },
                onFailure = { err -> showSnackbar("Key saved; catalog sync failed: ${err.message}") },
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseDynamicColor(enabled) }
    }

    fun setUse24HourClock(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUse24HourClock(enabled) }
    }

    fun setShowMessageTimestamps(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowMessageTimestamps(enabled) }
    }

    fun setLinkOpenBehavior(behavior: LinkOpenBehavior) {
        viewModelScope.launch { preferencesRepository.setLinkOpenBehavior(behavior) }
    }

    // --- Appearance Studio, Custom Fonts & Creator Profile ---

    fun updateAppearance(transform: (AppearanceConfig) -> AppearanceConfig) {
        viewModelScope.launch { preferencesRepository.updateAppearance(transform) }
    }

    fun resetAppearanceDefaults() {
        viewModelScope.launch {
            preferencesRepository.updateAppearance { AppearanceConfig() }
            showSnackbar("Appearance reset to defaults")
        }
    }

    fun installCustomFontFromUri(uri: Uri) {
        viewModelScope.launch {
            documentExtractor.installCustomFont(uri).fold(
                onSuccess = { (path, displayName) ->
                    preferencesRepository.updateAppearance {
                        it.copy(
                            selectedFontKey = "custom",
                            customFontPath = path,
                            customFontName = displayName,
                        )
                    }
                    showSnackbar("Installed custom font: $displayName")
                },
                onFailure = { err ->
                    showSnackbar(err.message ?: "Could not load font file")
                },
            )
        }
    }

    fun saveCreatorProfile(profile: CreatorProfile) {
        viewModelScope.launch {
            preferencesRepository.saveCreatorProfile(profile)
            showSnackbar("Creator profile saved")
        }
    }

    // --- SAF Export (Google Drive / Local) & Share Sheet ---

    fun prepareChatExport(
        format: ExportFormat,
        onReady: (suggestedName: String, mimeType: String, content: String) -> Unit,
    ) {
        viewModelScope.launch {
            val convId = activeConversationId.value
            val conv = convId?.let { vaultRepository.getConversation(it) }
            if (conv == null) {
                showSnackbar("No active conversation to export")
                return@launch
            }
            val msgs = vaultRepository.getMessagesOnce(conv.id)
            val use24h = preferencesState.value.use24HourClock
            val content = when (format) {
                ExportFormat.MARKDOWN -> exportManager.formatConversationAsMarkdown(conv, msgs, use24h)
                ExportFormat.PLAIN_TEXT -> exportManager.formatConversationAsPlainText(conv, msgs, use24h)
                ExportFormat.JSON -> exportManager.formatConversationAsJson(conv, msgs)
                ExportFormat.ENCRYPTED_VAULT -> {
                    val snapshot = vaultRepository.exportFullVaultSnapshot()
                    exportManager.buildEncryptedVaultPayload(snapshot.folders, snapshot.conversations, snapshot.messages)
                }
            }
            val name = exportManager.suggestedFilename(conv.title, format)
            onReady(name, format.mimeType, content)
        }
    }

    fun prepareVaultExport(
        passphrase: String,
        onReady: (suggestedName: String, mimeType: String, content: String) -> Unit,
    ) {
        viewModelScope.launch {
            val snapshot = vaultRepository.exportFullVaultSnapshot()
            val payload = exportManager.buildEncryptedVaultPayload(
                folders = snapshot.folders,
                conversations = snapshot.conversations,
                messages = snapshot.messages,
                passphrase = passphrase.trim(),
            )
            val name = exportManager.suggestedFilename("aetherrouter_vault", ExportFormat.ENCRYPTED_VAULT)
            onReady(name, ExportFormat.ENCRYPTED_VAULT.mimeType, payload)
        }
    }

    fun writeExportToSaf(uri: Uri, content: String) {
        viewModelScope.launch {
            exportManager.writeTextToSafUri(uri, content).fold(
                onSuccess = { showSnackbar("Saved export to storage / cloud drive") },
                onFailure = { err -> showSnackbar("Export failed: ${err.message}") },
            )
        }
    }

    fun shareActiveChatFile(format: ExportFormat, onIntentReady: (Intent) -> Unit) {
        prepareChatExport(format) { fileName, mimeType, content ->
            viewModelScope.launch {
                exportManager.createShareIntentForFile(fileName, mimeType, content).fold(
                    onSuccess = onIntentReady,
                    onFailure = { err -> showSnackbar("Share failed: ${err.message}") },
                )
            }
        }
    }

    fun shareVaultBackupFile(passphrase: String, onIntentReady: (Intent) -> Unit) {
        prepareVaultExport(passphrase) { fileName, mimeType, content ->
            viewModelScope.launch {
                exportManager.createShareIntentForFile(fileName, mimeType, content).fold(
                    onSuccess = onIntentReady,
                    onFailure = { err -> showSnackbar("Share failed: ${err.message}") },
                )
            }
        }
    }

    fun importBackupFromSaf(uri: Uri, passphrase: String = "") {
        viewModelScope.launch {
            exportManager.readAndParseImportUri(uri, passphrase.trim()).fold(
                onSuccess = { bundle ->
                    val count = vaultRepository.importVaultBundle(bundle)
                    showSnackbar("Imported $count conversation(s) into encrypted vault")
                },
                onFailure = { err ->
                    showSnackbar(err.message ?: "Import failed")
                },
            )
        }
    }

    // --- Phase 3: Persona & SillyTavern V2 Studio ---

    fun savePersona(persona: PersonaCard) {
        viewModelScope.launch {
            vaultRepository.upsertPersona(persona)
            showSnackbar("Saved persona: ${persona.name}")
        }
    }

    fun deletePersona(personaId: String) {
        viewModelScope.launch {
            vaultRepository.deletePersona(personaId)
            showSnackbar("Persona deleted")
        }
    }

    fun importPersonaFromUri(uri: Uri) {
        viewModelScope.launch {
            personaCardEngine.importFromUri(uri).fold(
                onSuccess = { card ->
                    vaultRepository.upsertPersona(card)
                    val loreInfo = if (card.lorebookEntries.isNotEmpty()) {
                        " (${card.lorebookEntries.size} lorebook entries)"
                    } else {
                        ""
                    }
                    showSnackbar("Imported persona: ${card.name}$loreInfo")
                },
                onFailure = { err ->
                    showSnackbar("Persona import failed: ${err.message ?: "Unsupported format"}")
                },
            )
        }
    }

    fun applyPersonaToActiveChat(persona: PersonaCard, greetingText: String? = null) {
        viewModelScope.launch {
            val conv = ensureActiveConversation()
            val updatedTitle = if (conv.title == "New Conversation") persona.name else conv.title
            vaultRepository.updateConversation(
                conv.copy(
                    personaId = persona.id,
                    title = updatedTitle,
                ),
            )
            if (!greetingText.isNullOrBlank()) {
                val prefs = preferencesState.value
                val clock = liveClockProvider.refreshNow()
                val resolvedGreeting = personaCardEngine.resolveMacros(
                    raw = greetingText,
                    charName = persona.name,
                    userDisplayName = prefs.creatorProfile.displayName,
                    clockSnapshot = clock,
                    use24HourClock = prefs.use24HourClock,
                )
                val greetingMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conv.id,
                    role = "assistant",
                    content = resolvedGreeting,
                    modelUsed = persona.name,
                    createdAt = System.currentTimeMillis(),
                )
                vaultRepository.saveMessage(greetingMsg)
            }
            showSnackbar("Active persona: ${persona.name}")
        }
    }

    fun detachPersonaFromActiveChat() {
        viewModelScope.launch {
            val conv = activeConversationId.value?.let { vaultRepository.getConversation(it) } ?: return@launch
            vaultRepository.updateConversation(conv.copy(personaId = null))
            showSnackbar("Persona detached from chat")
        }
    }

    // --- Phase 3: MCP Tools Hub ---

    fun saveMcpServer(
        id: String?,
        name: String,
        endpointUrl: String,
        authHeaderName: String,
        authHeaderValue: String,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            showSnackbar("Enter a name for the MCP server")
            return
        }
        val validated = mcpClient.validateEndpointUrl(endpointUrl)
        if (validated.isFailure) {
            showSnackbar(validated.exceptionOrNull()?.message ?: "Invalid MCP URL")
            return
        }
        viewModelScope.launch {
            val existing = id?.let { sid -> vaultRepository.getMcpServersOnce().firstOrNull { it.id == sid } }
            val server = McpServerConfig(
                id = id ?: UUID.randomUUID().toString(),
                name = cleanName,
                endpointUrl = validated.getOrThrow(),
                authHeaderName = authHeaderName.trim().ifBlank { "Authorization" },
                authHeaderValue = authHeaderValue.trim(),
                isEnabled = existing?.isEnabled ?: true,
                tools = existing?.tools ?: emptyList(),
                lastSyncedAt = existing?.lastSyncedAt ?: 0L,
            )
            vaultRepository.upsertMcpServer(server)
            showSnackbar("Saved MCP server: $cleanName")
            syncMcpServerTools(server)
        }
    }

    fun deleteMcpServer(serverId: String) {
        viewModelScope.launch {
            val conv = activeConversationId.value?.let { vaultRepository.getConversation(it) }
            if (conv != null && serverId in conv.enabledMcpServerIds) {
                vaultRepository.updateConversation(
                    conv.copy(enabledMcpServerIds = conv.enabledMcpServerIds.filterNot { it == serverId }),
                )
            }
            vaultRepository.deleteMcpServer(serverId)
            showSnackbar("MCP server removed")
        }
    }

    fun syncMcpServerTools(server: McpServerConfig) {
        viewModelScope.launch {
            isSyncingMcpServerId.value = server.id
            mcpClient.discoverTools(server).fold(
                onSuccess = { tools ->
                    vaultRepository.upsertMcpServer(
                        server.copy(
                            tools = tools,
                            lastSyncedAt = System.currentTimeMillis(),
                        ),
                    )
                    showSnackbar("Synced ${tools.size} tool(s) from ${server.name}")
                },
                onFailure = { err ->
                    showSnackbar("MCP sync failed: ${err.message ?: "Unreachable"}")
                },
            )
            isSyncingMcpServerId.value = null
        }
    }

    fun toggleMcpServerForActiveChat(serverId: String) {
        viewModelScope.launch {
            val conv = ensureActiveConversation()
            val nextIds = if (serverId in conv.enabledMcpServerIds) {
                conv.enabledMcpServerIds.filterNot { it == serverId }
            } else {
                conv.enabledMcpServerIds + serverId
            }
            vaultRepository.updateConversation(conv.copy(enabledMcpServerIds = nextIds))
            val enabledNow = serverId in nextIds
            showSnackbar(if (enabledNow) "MCP tools enabled for this chat" else "MCP tools disabled for this chat")
        }
    }

    // --- Phase 3: On-Device Voice Mode & TTS ---

    fun startVoiceListening(autoSend: Boolean, autoSpeakReply: Boolean) {
        voiceEngineManager.startListening { finalTranscript ->
            if (finalTranscript.isBlank()) return@startListening
            if (autoSend) {
                autoSpeakNextResponse = autoSpeakReply
                sendMessage(overrideText = finalTranscript)
            } else {
                composerText.value = if (composerText.value.isBlank()) {
                    finalTranscript
                } else {
                    "${composerText.value.trimEnd()} $finalTranscript"
                }
            }
        }
    }

    fun stopVoiceListening() {
        voiceEngineManager.stopListening()
    }

    fun clearVoiceTranscript() {
        voiceEngineManager.clearTranscript()
    }

    fun sendVoiceTranscriptNow(transcript: String, autoSpeakReply: Boolean) {
        val clean = transcript.trim()
        if (clean.isEmpty()) return
        autoSpeakNextResponse = autoSpeakReply
        voiceEngineManager.clearTranscript()
        sendMessage(overrideText = clean)
    }

    fun toggleSpeakMessage(message: ChatMessage) {
        val current = voiceEngineManager.state.value
        if (current.isSpeaking && current.speakingMessageId == message.id) {
            voiceEngineManager.stopSpeaking()
        } else {
            voiceEngineManager.speakText(message.id, message.content)
        }
    }

    fun stopSpeaking() {
        voiceEngineManager.stopSpeaking()
    }

    // --- Phase 3: Biometric Vault Lock & Smart Replies ---

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricLockEnabled(enabled)
            showSnackbar(if (enabled) "Vault biometric lock enabled" else "Vault biometric lock disabled")
        }
    }

    fun setSmartRepliesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSmartRepliesEnabled(enabled)
        }
    }

    fun showSnackbar(message: String) {
        snackbarMessage.value = message
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    private suspend fun ensureActiveConversation(): Conversation {
        val currentId = activeConversationId.value
        if (currentId != null) {
            vaultRepository.getConversation(currentId)?.let { return it }
        }
        val prefs = preferencesState.value
        val folderId = selectedFolderFilterId.value
        val folder = folderId?.let { id ->
            vaultRepository.observeFolders().first().firstOrNull { it.id == id }
        }
        val created = vaultRepository.createConversation(
            modelId = folder?.defaultModelId?.takeIf { it.isNotBlank() } ?: prefs.defaultModelId,
            folderId = folderId,
            webSearchEnabled = prefs.defaultWebSearchEnabled,
            webSearchMode = prefs.defaultWebSearchMode,
            webSearchEngine = prefs.defaultWebSearchEngine,
            webMaxResults = prefs.defaultWebMaxResults,
            webMaxTotalResults = prefs.defaultWebMaxTotalResults,
        )
        activeConversationId.value = created.id
        return created
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                vaultRepository = container.vaultRepository,
                preferencesRepository = container.preferencesRepository,
                streamClient = container.streamClient,
                liveWebFetcher = container.liveWebFetcher,
                liveClockProvider = container.liveClockProvider,
                documentExtractor = container.documentExtractor,
                exportManager = container.exportManager,
                personaCardEngine = container.personaCardEngine,
                mcpClient = container.mcpClient,
                voiceEngineManager = container.voiceEngineManager,
            ) as T
        }
    }
}

private data class Tuple4<A, B, C, D>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
)

private data class Tuple5<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
)
