package com.example.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.CodeArtifactUtils
import com.example.core.ContextAndCostEstimator
import com.example.core.ContextCostMetrics
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
import com.example.data.model.GitHubContentEntry
import com.example.data.model.GitHubPendingPush
import com.example.data.model.GitHubRepoSummary
import com.example.data.model.GlobalLorebook
import com.example.data.model.LinkOpenBehavior
import com.example.data.model.LiveArtifactPreviewRequest
import com.example.data.model.McpServerConfig
import com.example.data.model.McpToolDef
import com.example.data.model.MessageSwipe
import com.example.data.model.ModelInfo
import com.example.data.model.OpenRouterActivityBreakdown
import com.example.data.model.OpenRouterAlertState
import com.example.data.model.OpenRouterCreditBalance
import com.example.data.model.OpenRouterUsageSnapshot
import com.example.data.model.PersonaCard
import com.example.data.model.ReasoningEffort
import com.example.data.model.SamplerPreset
import com.example.data.model.SlashPromptTemplate
import com.example.data.model.ThemeMode
import com.example.data.model.UrlCitation
import com.example.data.model.WebSearchEngine
import com.example.data.model.WebSearchMode
import com.example.data.repository.UsageRepository
import com.example.data.repository.VaultRepository
import com.example.network.GitHubRepository
import com.example.network.LiveWebFetcher
import com.example.network.McpJsonRpcClient
import com.example.network.OpenRouterApiException
import com.example.network.OpenRouterStreamClient
import com.example.network.OpenRouterStreamEvent
import com.example.network.ToolCallPayload
import com.example.ui.components.GitHubStudioUiState
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

data class CompareLaneState(
    val modelId: String = "",
    val content: String = "",
    val reasoningText: String = "",
    val reasoningDetailsJson: String = "",
    val citations: List<UrlCitation> = emptyList(),
    val isStreaming: Boolean = false,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val elapsedMs: Long = 0L,
    val error: String? = null,
)

data class ModelCompareUiState(
    val prompt: String = "",
    val modelBId: String = "",
    val laneA: CompareLaneState = CompareLaneState(),
    val laneB: CompareLaneState = CompareLaneState(),
) {
    val isRunning: Boolean
        get() = laneA.isStreaming || laneB.isStreaming
}

data class ChatWorkspaceUiState(
    val preferences: AppPreferences = AppPreferences(),
    val clock: LiveClockSnapshot = LiveClockProvider.captureSnapshot(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderFilterId: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val drawerSearchQuery: String = "",
    val activeConversation: Conversation? = null,
    val activeModel: ModelInfo? = null,
    val allModels: List<ModelInfo> = emptyList(),
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
    val contextCostMetrics: ContextCostMetrics = ContextCostMetrics(),
    val creditBalance: OpenRouterCreditBalance? = null,
    val isLoadingCredits: Boolean = false,
    val globalLorebooks: List<GlobalLorebook> = emptyList(),
    val samplerPresets: List<SamplerPreset> = emptyList(),
    val slashTemplates: List<SlashPromptTemplate> = emptyList(),
    val isCompressingContext: Boolean = false,
    val compareState: ModelCompareUiState = ModelCompareUiState(),
    val lastMatchedLorebookCount: Int = 0,
    val usageSnapshot: OpenRouterUsageSnapshot? = null,
    val activityBreakdown: OpenRouterActivityBreakdown? = null,
    val isLoadingActivity: Boolean = false,
    val openRouterAlert: OpenRouterAlertState? = null,
    val liveArtifactPreview: LiveArtifactPreviewRequest? = null,
    val gitHubState: GitHubStudioUiState = GitHubStudioUiState(),
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
    private val usageRepository: UsageRepository,
    private val gitHubRepository: GitHubRepository,
) : ViewModel() {

    private data class DraftConversationConfig(
        val modelId: String? = null,
        val folderId: String? = null,
        val systemPromptOverride: String = "",
        val temperature: Float = 0.7f,
        val topP: Float = 1.0f,
        val maxTokens: Int = 4096,
        val contextWindowLimit: Int = 128_000,
        val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
        val webSearchEnabled: Boolean? = null,
        val webSearchMode: WebSearchMode? = null,
        val webSearchEngine: WebSearchEngine? = null,
        val webMaxResults: Int? = null,
        val webMaxTotalResults: Int? = null,
        val fallbackModelIds: List<String> = emptyList(),
    )

    private val activeConversationId = MutableStateFlow<String?>(null)
    private val draftConfig = MutableStateFlow(DraftConversationConfig())
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
    private val creditBalance = MutableStateFlow<OpenRouterCreditBalance?>(null)
    private val isLoadingCredits = MutableStateFlow(false)
    private val isLoadingActivity = MutableStateFlow(false)
    private val openRouterAlert = MutableStateFlow<OpenRouterAlertState?>(null)
    private val liveArtifactPreview = MutableStateFlow<LiveArtifactPreviewRequest?>(null)
    private val gitHubState = MutableStateFlow(GitHubStudioUiState())
    private val isCompressingContext = MutableStateFlow(false)
    private val compareState = MutableStateFlow(ModelCompareUiState())
    private val lastMatchedLorebookCount = MutableStateFlow(0)

    private var activeStreamJob: Job? = null
    private var compareJobA: Job? = null
    private var compareJobB: Job? = null
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
        val nonEmpty = allConvs.filter { it.messageCount > 0 || it.isPinned || it.personaId != null }
        val byFolder = if (folderFilter == null) nonEmpty else nonEmpty.filter { it.folderId == folderFilter }
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
        Tuple5(prefs, clock, convs, activeConv, models)
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
        isCompressingContext,
        compareState,
    ) { snack, browserUrl, choiceUrl, compressing, compare ->
        Tuple5(snack, browserUrl, choiceUrl, compressing, compare)
    }

    private val extrasFlow = combine(
        vaultRepository.observeGlobalLorebooks(),
        vaultRepository.observeSamplerPresets(),
        vaultRepository.observeSlashTemplates(),
        creditBalance,
        isLoadingCredits,
    ) { lorebooks, presets, templates, credits, loadingCredits ->
        Tuple5(lorebooks, presets, templates, credits, loadingCredits)
    }

    private val phase4UsageFlow = combine(
        usageRepository.usageSnapshot,
        usageRepository.activityBreakdown,
        isLoadingActivity,
        openRouterAlert,
        combine(liveArtifactPreview, gitHubState) { preview, gh -> preview to gh },
    ) { usageSnap, activity, loadingAct, alert, previewAndGh ->
        Tuple5(usageSnap, activity, loadingAct, alert, previewAndGh)
    }

    private val powerToolsFlow = combine(
        vaultRepository.observePersonas(),
        vaultRepository.observeMcpServers(),
        isSyncingMcpServerId,
        voiceEngineManager.state,
        combine(extrasFlow, lastMatchedLorebookCount, phase4UsageFlow) { ext, loreCount, p4 ->
            Triple(ext, loreCount, p4)
        },
    ) { personas, mcpServers, syncingId, voice, extTriple ->
        Tuple5(personas, mcpServers, syncingId, voice, extTriple)
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
        val allModels = base.e
        val modelId = activeConv?.modelId ?: prefs.defaultModelId
        val modelById = allModels.associateBy { it.id }
        val activeModel = modelById[modelId]
        val msgs = interaction.a
        val stream = interaction.b
        val activePersona = activeConv?.personaId?.let { pid -> power.a.firstOrNull { it.id == pid } }
        val (extras, matchedLoreCount, p4) = power.e
        val enabledLorebooks = if (activeConv != null && activeConv.enabledLorebookIds.isNotEmpty()) {
            extras.a.filter { it.id in activeConv.enabledLorebookIds }
        } else {
            emptyList()
        }
        val projectInstructions = activeConv?.folderId?.let { fid ->
            mediaFolder.a.firstOrNull { it.id == fid }?.projectInstructions.orEmpty()
        }.orEmpty()
        val metrics = ContextAndCostEstimator.computeConversationMetrics(
            conversation = activeConv,
            messages = msgs,
            stagedAttachments = mediaFolder.c,
            activeModel = activeModel,
            modelById = modelById,
            activePersona = activePersona,
            activeGlobalLorebooks = enabledLorebooks,
            projectInstructions = projectInstructions,
        )
        val smartReplies = if (prefs.smartRepliesEnabled && !stream.isStreaming) {
            SmartReplyGenerator.generateSuggestions(msgs)
        } else {
            emptyList()
        }
        val (artifactPreview, ghUi) = p4.e
        ChatWorkspaceUiState(
            preferences = prefs,
            clock = base.b,
            folders = mediaFolder.a,
            selectedFolderFilterId = mediaFolder.b,
            conversations = base.c,
            drawerSearchQuery = interaction.d,
            activeConversation = activeConv,
            activeModel = activeModel,
            allModels = allModels,
            messages = msgs,
            streaming = stream,
            composerText = interaction.c,
            stagedAttachments = mediaFolder.c,
            customStickers = mediaFolder.d,
            isProcessingAttachment = mediaFolder.e,
            isFetchingWebpage = interaction.e,
            snackbarMessage = overlay.a,
            inAppBrowserUrl = overlay.b,
            pendingLinkChoiceUrl = overlay.c,
            personas = power.a,
            activePersona = activePersona,
            mcpServers = power.b,
            isSyncingMcpServerId = power.c,
            voiceState = power.d,
            smartReplies = smartReplies,
            contextCostMetrics = metrics,
            creditBalance = extras.d ?: p4.a?.toLegacyCreditBalance(),
            isLoadingCredits = extras.e,
            globalLorebooks = extras.a,
            samplerPresets = extras.b,
            slashTemplates = extras.c,
            isCompressingContext = overlay.d,
            compareState = overlay.e,
            lastMatchedLorebookCount = matchedLoreCount,
            usageSnapshot = p4.a,
            activityBreakdown = p4.b,
            isLoadingActivity = p4.c,
            openRouterAlert = p4.d,
            liveArtifactPreview = artifactPreview,
            gitHubState = ghUi,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatWorkspaceUiState(),
    )

    init {
        viewModelScope.launch {
            vaultRepository.ensureStarterModelsSeeded()
            vaultRepository.ensureStarterPresetsSeeded()
            vaultRepository.pruneEmptyDraftConversations()
            val existing = vaultRepository.observeConversations().first()
            if (activeConversationId.value == null) {
                val resumable = existing.firstOrNull { it.messageCount > 0 } ?: existing.firstOrNull()
                if (resumable != null) {
                    activeConversationId.value = resumable.id
                }
            }
            val savedKey = preferencesRepository.getDecryptedApiKey()
            if (savedKey.isNotBlank()) {
                refreshCreditBalance(silent = true)
            }
            val savedMgmt = preferencesRepository.getDecryptedManagementApiKey()
            if (savedMgmt.isNotBlank()) {
                refreshActivityBreakdown(silent = true)
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
        openRouterAlert.value = null
        lastMatchedLorebookCount.value = 0
    }

    fun startNewConversation() {
        if (streamingState.value.isStreaming) stopStreaming()
        activeConversationId.value = null
        draftConfig.value = DraftConversationConfig(folderId = selectedFolderFilterId.value)
        composerText.value = ""
        stagedAttachments.value = emptyList()
        openRouterAlert.value = null
        lastMatchedLorebookCount.value = 0
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            vaultRepository.deleteConversation(conversationId)
            if (activeConversationId.value == conversationId) {
                val remaining = vaultRepository.observeConversations().first()
                    .filter { it.messageCount > 0 || it.isPinned || it.personaId != null }
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
            } else {
                draftConfig.value = draftConfig.value.copy(
                    modelId = model.id,
                    contextWindowLimit = model.contextLength.coerceAtLeast(8_192),
                )
            }
            showSnackbar("Switched model to ${model.name}")
        }
    }

    fun toggleQuickWebSearch() {
        viewModelScope.launch {
            val current = activeConversationId.value?.let { vaultRepository.getConversation(it) }
            val prefs = preferencesState.value
            if (current != null) {
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
            } else {
                val nextEnabled = !(draftConfig.value.webSearchEnabled ?: prefs.defaultWebSearchEnabled)
                val mode = draftConfig.value.webSearchMode ?: prefs.defaultWebSearchMode
                val engine = draftConfig.value.webSearchEngine ?: prefs.defaultWebSearchEngine
                val maxRes = draftConfig.value.webMaxResults ?: prefs.defaultWebMaxResults
                val maxTotal = draftConfig.value.webMaxTotalResults ?: prefs.defaultWebMaxTotalResults
                draftConfig.value = draftConfig.value.copy(webSearchEnabled = nextEnabled)
                preferencesRepository.setDefaultWebConfig(
                    enabled = nextEnabled,
                    mode = mode,
                    engine = engine,
                    maxResults = maxRes,
                    maxTotalResults = maxTotal,
                )
                val modeLabel = if (mode == WebSearchMode.AGENTIC_TOOL) "Agentic" else "Always-On"
                showSnackbar(if (nextEnabled) "Live Web enabled ($modeLabel)" else "Live Web paused")
            }
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
        fallbackModelIds: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            val current = activeConversationId.value?.let { vaultRepository.getConversation(it) }
            val activeModelId = current?.modelId ?: draftConfig.value.modelId ?: preferencesState.value.defaultModelId
            val cleanFallbacks = fallbackModelIds.filter { it.isNotBlank() && it != activeModelId }.distinct().take(4)
            if (current != null) {
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
                        fallbackModelIds = cleanFallbacks,
                    ),
                )
            } else {
                draftConfig.value = draftConfig.value.copy(
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
                    fallbackModelIds = cleanFallbacks,
                )
            }
            preferencesRepository.setDefaultWebConfig(
                enabled = webSearchEnabled,
                mode = webSearchMode,
                engine = webSearchEngine,
                maxResults = webMaxResults,
                maxTotalResults = webMaxTotalResults,
            )
            showSnackbar("Chat, Fallback & Web settings saved")
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

            val seedTitle = text.ifBlank { attachmentsToSend.firstOrNull()?.displayName.orEmpty() }
            val autoTitle = seedTitle.replace('\n', ' ').take(48).trim().ifBlank { "New Conversation" }

            var conv = ensureActiveConversation(initialTitle = autoTitle)
            val existingMessages = vaultRepository.getMessagesOnce(conv.id)
            if (existingMessages.isEmpty() && conv.title == "New Conversation" && autoTitle != "New Conversation") {
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
            // Keep this message in place so previous regenerations remain swipeable (< 1 / N >),
            // and prune any later turns that depended on the old branch.
            vaultRepository.deleteMessagesAfter(conv.id, message.createdAt)
            launchCompletionStream(
                apiKey = apiKey,
                conversation = conv,
                replaceSwipeTargetMessage = message,
            )
        }
    }

    fun selectMessageSwipe(message: ChatMessage, newIndex: Int) {
        if (message.swipes.isEmpty() || newIndex !in message.swipes.indices) return
        val chosen = message.swipes[newIndex]
        viewModelScope.launch {
            vaultRepository.saveMessage(
                message.copy(
                    content = chosen.content,
                    reasoningContent = chosen.reasoningContent,
                    reasoningDetailsJson = chosen.reasoningDetailsJson,
                    thinkingDurationMs = chosen.thinkingDurationMs,
                    citations = chosen.citations,
                    modelUsed = chosen.modelUsed.ifBlank { message.modelUsed },
                    promptTokens = chosen.promptTokens,
                    completionTokens = chosen.completionTokens,
                    activeSwipeIndex = newIndex,
                ),
            )
        }
    }

    fun forkConversationAtMessage(message: ChatMessage) {
        if (streamingState.value.isStreaming) stopStreaming()
        viewModelScope.launch {
            val conv = vaultRepository.getConversation(message.conversationId) ?: return@launch
            val forked = vaultRepository.forkConversationAtMessage(conv, message)
            activeConversationId.value = forked.id
            showSnackbar("Branched chat: ${forked.title}")
        }
    }

    fun clearActiveConversationMessages() {
        if (streamingState.value.isStreaming) stopStreaming()
        viewModelScope.launch {
            val conv = activeConversationId.value?.let { vaultRepository.getConversation(it) } ?: return@launch
            vaultRepository.clearConversationMessages(conv.id)
            vaultRepository.updateConversation(
                conv.copy(contextSummary = "", summarizedUpToTimestamp = 0L),
            )
            lastMatchedLorebookCount.value = 0
            showSnackbar("Cleared conversation messages")
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
        replaceSwipeTargetMessage: ChatMessage? = null,
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
            val extraLoreEntries = if (conversation.enabledLorebookIds.isNotEmpty()) {
                vaultRepository.getGlobalLorebooksOnce()
                    .filter { it.id in conversation.enabledLorebookIds }
                    .flatMap { it.entries }
            } else {
                emptyList()
            }
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
                    val rawHistory = vaultRepository.getMessagesOnce(conversation.id)
                    val history = if (replaceSwipeTargetMessage != null) {
                        rawHistory.filterNot { it.id == replaceSwipeTargetMessage.id }
                    } else {
                        rawHistory
                    }
                    val resolvedPersona = if (activePersona != null || extraLoreEntries.isNotEmpty()) {
                        personaCardEngine.buildResolvedPrompt(
                            persona = activePersona,
                            history = history,
                            userDisplayName = prefs.creatorProfile.displayName,
                            clockSnapshot = clockSnapshot,
                            use24HourClock = prefs.use24HourClock,
                            extraLorebookEntries = extraLoreEntries,
                        ).also {
                            lastMatchedLorebookCount.value = it.matchedLorebookCount
                        }
                    } else {
                        lastMatchedLorebookCount.value = 0
                        null
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

                        val finalContent = completed.fullContent.ifBlank {
                            if (completed.fullReasoning.isNotBlank() || completed.reasoningDetailsJson.isNotBlank()) {
                                "(Model returned reasoning without final text)"
                            } else {
                                "(Empty response)"
                            }
                        }
                        val newSwipe = MessageSwipe(
                            content = finalContent,
                            reasoningContent = completed.fullReasoning,
                            reasoningDetailsJson = completed.reasoningDetailsJson,
                            thinkingDurationMs = duration,
                            citations = completed.citations,
                            modelUsed = conversation.modelId,
                            promptTokens = completed.promptTokens,
                            completionTokens = completed.completionTokens,
                            createdAt = System.currentTimeMillis(),
                        )

                        val savedAssistant = if (replaceSwipeTargetMessage != null) {
                            val existingSwipes = replaceSwipeTargetMessage.swipes.ifEmpty {
                                listOf(
                                    MessageSwipe(
                                        content = replaceSwipeTargetMessage.content,
                                        reasoningContent = replaceSwipeTargetMessage.reasoningContent,
                                        reasoningDetailsJson = replaceSwipeTargetMessage.reasoningDetailsJson,
                                        thinkingDurationMs = replaceSwipeTargetMessage.thinkingDurationMs,
                                        citations = replaceSwipeTargetMessage.citations,
                                        modelUsed = replaceSwipeTargetMessage.modelUsed,
                                        promptTokens = replaceSwipeTargetMessage.promptTokens,
                                        completionTokens = replaceSwipeTargetMessage.completionTokens,
                                        createdAt = replaceSwipeTargetMessage.createdAt,
                                    ),
                                )
                            }
                            val nextSwipes = existingSwipes + newSwipe
                            replaceSwipeTargetMessage.copy(
                                content = newSwipe.content,
                                reasoningContent = newSwipe.reasoningContent,
                                reasoningDetailsJson = newSwipe.reasoningDetailsJson,
                                thinkingDurationMs = newSwipe.thinkingDurationMs,
                                citations = newSwipe.citations,
                                modelUsed = newSwipe.modelUsed,
                                promptTokens = newSwipe.promptTokens,
                                completionTokens = newSwipe.completionTokens,
                                swipes = nextSwipes,
                                activeSwipeIndex = nextSwipes.lastIndex,
                            )
                        } else {
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                conversationId = conversation.id,
                                role = "assistant",
                                content = newSwipe.content,
                                reasoningContent = newSwipe.reasoningContent,
                                reasoningDetailsJson = newSwipe.reasoningDetailsJson,
                                thinkingDurationMs = newSwipe.thinkingDurationMs,
                                citations = newSwipe.citations,
                                modelUsed = newSwipe.modelUsed,
                                promptTokens = newSwipe.promptTokens,
                                completionTokens = newSwipe.completionTokens,
                                swipes = listOf(newSwipe),
                                activeSwipeIndex = 0,
                                createdAt = System.currentTimeMillis(),
                            )
                        }
                        vaultRepository.saveMessage(savedAssistant)
                        openRouterAlert.value = null
                        // Confirmed cadence: auto-refresh live OpenRouter spend & credits after every completed reply
                        viewModelScope.launch { refreshCreditBalance(silent = true) }
                        if (autoSpeakNextResponse) {
                            autoSpeakNextResponse = false
                            voiceEngineManager.speakText(savedAssistant.id, savedAssistant.content)
                        }
                    }
                    break
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OpenRouterApiException) {
                autoSpeakNextResponse = false
                if (e.alert != null) {
                    openRouterAlert.value = e.alert
                    showSnackbar(e.alert.headline)
                } else {
                    showSnackbar(e.message)
                }
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
                creditBalance.value = null
                usageRepository.clearMemoryCache()
                showSnackbar("API key cleared from encrypted vault")
                return@launch
            }
            showSnackbar("API key encrypted in Android Keystore. Syncing models…")
            launch { refreshCreditBalance(silent = true) }
            vaultRepository.refreshModelsFromOpenRouter(trimmed).fold(
                onSuccess = { count -> showSnackbar("Connected! Synced $count OpenRouter models") },
                onFailure = { err -> showSnackbar("Key saved; catalog sync failed: ${err.message}") },
            )
        }
    }

    fun refreshCreditBalance(silent: Boolean = false) {
        viewModelScope.launch {
            val apiKey = preferencesRepository.getDecryptedApiKey()
            if (apiKey.isBlank()) {
                creditBalance.value = null
                usageRepository.clearMemoryCache()
                if (!silent) showSnackbar("Add your OpenRouter API key first")
                return@launch
            }
            isLoadingCredits.value = true
            usageRepository.refreshUsage(apiKey).fold(
                onSuccess = { snapshot ->
                    creditBalance.value = snapshot.toLegacyCreditBalance()
                    if (!silent) showSnackbar("Updated OpenRouter spend & credits")
                },
                onFailure = { err ->
                    if (err is OpenRouterApiException && err.alert != null) {
                        openRouterAlert.value = err.alert
                    }
                    if (!silent) showSnackbar(err.message ?: "Could not fetch OpenRouter usage")
                },
            )
            isLoadingCredits.value = false
        }
    }

    fun refreshUsageAndActivity(silent: Boolean = false) {
        refreshCreditBalance(silent = silent)
        viewModelScope.launch {
            val mgmtKey = preferencesRepository.getDecryptedManagementApiKey()
            if (mgmtKey.isNotBlank()) {
                refreshActivityBreakdown(silent = silent)
            }
        }
    }

    fun refreshActivityBreakdown(silent: Boolean = false) {
        viewModelScope.launch {
            val mgmtKey = preferencesRepository.getDecryptedManagementApiKey()
            if (mgmtKey.isBlank()) {
                if (!silent) showSnackbar("Add your OpenRouter Management API key in Settings first")
                return@launch
            }
            isLoadingActivity.value = true
            usageRepository.refreshActivity(mgmtKey).fold(
                onSuccess = { breakdown ->
                    if (!silent) showSnackbar("Loaded ${breakdown.items.size} activity records (30d)")
                },
                onFailure = { err ->
                    if (err is OpenRouterApiException && err.alert != null) {
                        openRouterAlert.value = err.alert
                    }
                    if (!silent) showSnackbar(err.message ?: "Could not load 30-day activity")
                },
            )
            isLoadingActivity.value = false
        }
    }

    fun saveManagementApiKeyAndSync(rawKey: String) {
        viewModelScope.launch {
            preferencesRepository.saveManagementApiKey(rawKey)
            if (rawKey.trim().isEmpty()) {
                usageRepository.clearMemoryCache()
                launch { refreshCreditBalance(silent = true) }
                showSnackbar("Management API key cleared")
                return@launch
            }
            showSnackbar("Management key encrypted in Android Keystore. Loading 30d activity…")
            refreshActivityBreakdown(silent = false)
        }
    }

    fun dismissOpenRouterAlert() {
        openRouterAlert.value = null
    }

    // --- Phase 4: Live HTML/CSS/JS Artifact Preview & Code File Export ---

    fun openLiveArtifactPreview(language: String, code: String) {
        val spec = CodeArtifactUtils.buildCodeExportSpec(language, code, baseName = "artifact")
        liveArtifactPreview.value = LiveArtifactPreviewRequest(
            language = language,
            code = code,
            suggestedFileName = spec.suggestedFileName,
        )
    }

    fun dismissLiveArtifactPreview() {
        liveArtifactPreview.value = null
    }

    // --- Phase 4: GitHub Repo Connector & Commit Studio ---

    fun saveGitHubPatAndConnect(rawToken: String) {
        viewModelScope.launch {
            preferencesRepository.saveGitHubPat(rawToken)
            val trimmed = rawToken.trim()
            if (trimmed.isEmpty()) {
                gitHubRepository.clearMemoryCache()
                gitHubState.value = GitHubStudioUiState()
                showSnackbar("GitHub PAT cleared from encrypted vault")
                return@launch
            }
            gitHubState.value = gitHubState.value.copy(isLoading = true, errorMessage = null)
            gitHubRepository.verifyToken(trimmed).fold(
                onSuccess = { login ->
                    gitHubState.value = gitHubState.value.copy(
                        authenticatedUser = login,
                        isLoading = false,
                        errorMessage = null,
                    )
                    showSnackbar("GitHub connected as @$login")
                    refreshGitHubRepos()
                },
                onFailure = { err ->
                    gitHubState.value = gitHubState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "GitHub token verification failed",
                    )
                    showSnackbar(err.message ?: "GitHub token verification failed")
                },
            )
        }
    }

    fun startGitHubPushForCode(language: String, code: String) {
        val spec = CodeArtifactUtils.buildCodeExportSpec(language, code, baseName = "snippet")
        gitHubState.value = gitHubState.value.copy(
            pendingPush = GitHubPendingPush(
                code = code,
                language = language,
                suggestedFileName = spec.suggestedFileName,
            ),
            commitPreview = null,
            lastCommitResult = null,
            errorMessage = null,
        )
    }

    fun clearGitHubPendingPush() {
        gitHubState.value = gitHubState.value.copy(
            pendingPush = null,
            commitPreview = null,
        )
    }

    fun clearGitHubCommitPreview() {
        gitHubState.value = gitHubState.value.copy(commitPreview = null)
    }

    fun refreshGitHubRepos() {
        viewModelScope.launch {
            val pat = preferencesRepository.getDecryptedGitHubPat()
            if (pat.isBlank()) {
                gitHubState.value = GitHubStudioUiState(errorMessage = "Add a GitHub Personal Access Token in Settings")
                return@launch
            }
            gitHubState.value = gitHubState.value.copy(isLoading = true, errorMessage = null)
            val login = gitHubState.value.authenticatedUser ?: gitHubRepository.verifyToken(pat).getOrNull()
            gitHubRepository.listRepositories(pat).fold(
                onSuccess = { repos ->
                    gitHubState.value = gitHubState.value.copy(
                        isLoading = false,
                        authenticatedUser = login,
                        repos = repos,
                        errorMessage = null,
                    )
                },
                onFailure = { err ->
                    gitHubState.value = gitHubState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to load repositories",
                    )
                },
            )
        }
    }

    fun selectGitHubRepo(repo: GitHubRepoSummary) {
        viewModelScope.launch {
            val pat = preferencesRepository.getDecryptedGitHubPat()
            if (pat.isBlank()) return@launch
            gitHubState.value = gitHubState.value.copy(
                selectedRepo = repo,
                selectedBranch = repo.defaultBranch,
                currentPath = "",
                contents = emptyList(),
                branches = emptyList(),
                commitPreview = null,
                lastCommitResult = null,
                isLoading = true,
                errorMessage = null,
            )
            val branches = gitHubRepository.listBranches(pat, repo.owner, repo.name).getOrDefault(emptyList())
            val contentsRes = gitHubRepository.listContents(pat, repo.owner, repo.name, "", repo.defaultBranch)
            contentsRes.fold(
                onSuccess = { entries ->
                    gitHubState.value = gitHubState.value.copy(
                        branches = branches,
                        contents = entries,
                        isLoading = false,
                        errorMessage = null,
                    )
                },
                onFailure = { err ->
                    gitHubState.value = gitHubState.value.copy(
                        branches = branches,
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to browse repository",
                    )
                },
            )
        }
    }

    fun backToGitHubRepos() {
        gitHubState.value = gitHubState.value.copy(
            selectedRepo = null,
            branches = emptyList(),
            selectedBranch = "",
            currentPath = "",
            contents = emptyList(),
            commitPreview = null,
            errorMessage = null,
        )
    }

    fun selectGitHubBranch(branch: String) {
        val repo = gitHubState.value.selectedRepo ?: return
        viewModelScope.launch {
            val pat = preferencesRepository.getDecryptedGitHubPat()
            if (pat.isBlank()) return@launch
            gitHubState.value = gitHubState.value.copy(
                selectedBranch = branch,
                commitPreview = null,
                isLoading = true,
                errorMessage = null,
            )
            gitHubRepository.listContents(pat, repo.owner, repo.name, gitHubState.value.currentPath, branch).fold(
                onSuccess = { entries ->
                    gitHubState.value = gitHubState.value.copy(
                        contents = entries,
                        isLoading = false,
                    )
                },
                onFailure = { err ->
                    // If folder doesn't exist on the newly selected branch, fall back to root
                    val rootEntries = gitHubRepository.listContents(pat, repo.owner, repo.name, "", branch)
                        .getOrNull()
                    if (rootEntries != null) {
                        gitHubState.value = gitHubState.value.copy(
                            currentPath = "",
                            contents = rootEntries,
                            isLoading = false,
                        )
                    } else {
                        gitHubState.value = gitHubState.value.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to load branch contents",
                        )
                    }
                },
            )
        }
    }

    fun navigateGitHubPath(path: String) {
        val repo = gitHubState.value.selectedRepo ?: return
        val branch = gitHubState.value.selectedBranch.ifBlank { repo.defaultBranch }
        viewModelScope.launch {
            val pat = preferencesRepository.getDecryptedGitHubPat()
            if (pat.isBlank()) return@launch
            gitHubState.value = gitHubState.value.copy(isLoading = true, errorMessage = null)
            gitHubRepository.listContents(pat, repo.owner, repo.name, path, branch).fold(
                onSuccess = { entries ->
                    gitHubState.value = gitHubState.value.copy(
                        currentPath = path,
                        contents = entries,
                        isLoading = false,
                    )
                },
                onFailure = { err ->
                    gitHubState.value = gitHubState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to open folder",
                    )
                },
            )
        }
    }

    fun attachGitHubFileToComposer(entry: GitHubContentEntry, onAttached: () -> Unit = {}) {
        val repo = gitHubState.value.selectedRepo ?: return
        val branch = gitHubState.value.selectedBranch.ifBlank { repo.defaultBranch }
        viewModelScope.launch {
            val pat = preferencesRepository.getDecryptedGitHubPat()
            if (pat.isBlank()) return@launch
            gitHubState.value = gitHubState.value.copy(isAttachingFile = true, errorMessage = null)
            gitHubRepository.fetchFileContent(pat, repo.owner, repo.name, entry.path, branch).fold(
                onSuccess = { file ->
                    val ext = file.name.substringAfterLast('.', "txt")
                    val (_, mime) = CodeArtifactUtils.inferExtensionAndMime(ext)
                    val att = ChatAttachment(
                        id = UUID.randomUUID().toString(),
                        displayName = "${repo.name}/${file.path}",
                        mimeType = mime,
                        kind = AttachmentKind.DOCUMENT,
                        extractedText = buildString {
                            append("GitHub Repository: ${repo.fullName} (branch: $branch)\n")
                            append("File Path: ${file.path}\n")
                            append("SHA: ${file.sha}\n\n")
                            append(file.contentText)
                        },
                        sizeBytes = file.sizeBytes,
                    )
                    stagedAttachments.value = stagedAttachments.value + att
                    gitHubState.value = gitHubState.value.copy(isAttachingFile = false)
                    showSnackbar("Attached ${file.path} from ${repo.name}")
                    onAttached()
                },
                onFailure = { err ->
                    gitHubState.value = gitHubState.value.copy(
                        isAttachingFile = false,
                        errorMessage = err.message ?: "Could not read GitHub file",
                    )
                },
            )
        }
    }

    fun prepareGitHubCommitPreview(branch: String, targetPath: String, commitMessage: String) {
        val repo = gitHubState.value.selectedRepo ?: return
        val pending = gitHubState.value.pendingPush ?: return
        viewModelScope.launch {
            val pat = preferencesRepository.getDecryptedGitHubPat()
            if (pat.isBlank()) return@launch
            gitHubState.value = gitHubState.value.copy(isLoading = true, errorMessage = null)
            gitHubRepository.prepareCommitPreview(
                pat = pat,
                owner = repo.owner,
                repo = repo.name,
                branch = branch,
                rawPath = targetPath,
                content = pending.code,
                commitMessage = commitMessage,
            ).fold(
                onSuccess = { preview ->
                    gitHubState.value = gitHubState.value.copy(
                        isLoading = false,
                        commitPreview = preview,
                        errorMessage = null,
                    )
                },
                onFailure = { err ->
                    gitHubState.value = gitHubState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Could not prepare commit preview",
                    )
                },
            )
        }
    }

    fun confirmGitHubCommit() {
        val preview = gitHubState.value.commitPreview ?: return
        viewModelScope.launch {
            val pat = preferencesRepository.getDecryptedGitHubPat()
            if (pat.isBlank()) return@launch
            gitHubState.value = gitHubState.value.copy(isCommitting = true, errorMessage = null)
            gitHubRepository.commitFile(pat, preview).fold(
                onSuccess = { result ->
                    gitHubState.value = gitHubState.value.copy(
                        isCommitting = false,
                        commitPreview = null,
                        pendingPush = null,
                        lastCommitResult = result,
                        errorMessage = null,
                    )
                    val verb = if (result.isUpdate) "Updated" else "Created"
                    showSnackbar("$verb ${result.path} on ${result.branch} (${result.commitSha})")
                    // Refresh current folder listing to reflect new/updated file
                    navigateGitHubPath(gitHubState.value.currentPath)
                },
                onFailure = { err ->
                    gitHubState.value = gitHubState.value.copy(
                        isCommitting = false,
                        errorMessage = err.message ?: "Commit failed",
                    )
                },
            )
        }
    }

    /**
     * Wipes all volatile tokens/snapshots/GitHub repo state from ViewModel memory
     * immediately when Biometric Vault Lock engages.
     */
    fun wipeSensitiveMemoryState() {
        usageRepository.clearMemoryCache()
        gitHubRepository.clearMemoryCache()
        creditBalance.value = null
        openRouterAlert.value = null
        liveArtifactPreview.value = null
        gitHubState.value = GitHubStudioUiState()
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

    // --- Power Additions: Persona V2 Export, Global Lorebooks, Sampler Presets, Slash Templates, Context Compression & Compare ---

    fun preparePersonaV2Export(
        persona: PersonaCard,
        onReady: (suggestedName: String, mimeType: String, content: String) -> Unit,
    ) {
        val json = personaCardEngine.exportAsSillyTavernV2Json(persona)
        val safeName = persona.name
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "persona" }
        onReady("${safeName}_v2.json", "application/json", json)
    }

    fun sharePersonaV2Json(persona: PersonaCard, onIntentReady: (Intent) -> Unit) {
        preparePersonaV2Export(persona) { fileName, mimeType, content ->
            viewModelScope.launch {
                exportManager.createShareIntentForFile(fileName, mimeType, content).fold(
                    onSuccess = onIntentReady,
                    onFailure = { err -> showSnackbar("Share failed: ${err.message}") },
                )
            }
        }
    }

    fun saveGlobalLorebook(book: GlobalLorebook) {
        if (book.name.isBlank()) return
        viewModelScope.launch {
            vaultRepository.upsertGlobalLorebook(book)
            showSnackbar("Saved shared lorebook: ${book.name}")
        }
    }

    fun deleteGlobalLorebook(bookId: String) {
        viewModelScope.launch {
            val conv = activeConversationId.value?.let { vaultRepository.getConversation(it) }
            if (conv != null && bookId in conv.enabledLorebookIds) {
                vaultRepository.updateConversation(
                    conv.copy(enabledLorebookIds = conv.enabledLorebookIds.filterNot { it == bookId }),
                )
            }
            vaultRepository.deleteGlobalLorebook(bookId)
            showSnackbar("Shared lorebook deleted")
        }
    }

    fun toggleGlobalLorebookForActiveChat(bookId: String) {
        viewModelScope.launch {
            val conv = ensureActiveConversation()
            val next = if (bookId in conv.enabledLorebookIds) {
                conv.enabledLorebookIds.filterNot { it == bookId }
            } else {
                conv.enabledLorebookIds + bookId
            }
            vaultRepository.updateConversation(conv.copy(enabledLorebookIds = next))
            showSnackbar(if (bookId in next) "Lorebook active in this chat" else "Lorebook detached from chat")
        }
    }

    fun saveSamplerPreset(
        name: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
        reasoningEffort: ReasoningEffort,
        systemPromptSnippet: String,
    ) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            vaultRepository.upsertSamplerPreset(
                SamplerPreset(
                    id = UUID.randomUUID().toString(),
                    name = clean,
                    temperature = temperature.coerceIn(0f, 2f),
                    topP = topP.coerceIn(0.05f, 1f),
                    maxTokens = maxTokens.coerceIn(256, 65536),
                    reasoningEffort = reasoningEffort,
                    systemPromptSnippet = systemPromptSnippet.trim(),
                    isBuiltIn = false,
                ),
            )
            showSnackbar("Saved sampler preset: $clean")
        }
    }

    fun deleteSamplerPreset(presetId: String) {
        viewModelScope.launch {
            vaultRepository.deleteSamplerPreset(presetId)
            showSnackbar("Preset deleted")
        }
    }

    fun saveSlashTemplate(command: String, title: String, body: String) {
        val cleanCmd = command.trim().lowercase().removePrefix("/")
        val cleanBody = body.trim()
        if (cleanCmd.isEmpty() || cleanBody.isEmpty()) return
        viewModelScope.launch {
            vaultRepository.upsertSlashTemplate(
                SlashPromptTemplate(
                    id = UUID.randomUUID().toString(),
                    command = cleanCmd,
                    title = title.trim().ifBlank { cleanCmd },
                    promptBody = cleanBody,
                ),
            )
            showSnackbar("Saved /$cleanCmd template")
        }
    }

    fun deleteSlashTemplate(templateId: String) {
        viewModelScope.launch {
            vaultRepository.deleteSlashTemplate(templateId)
            showSnackbar("Slash template deleted")
        }
    }

    fun compressActiveConversationContext() {
        if (isCompressingContext.value || streamingState.value.isStreaming) return
        viewModelScope.launch {
            val apiKey = preferencesRepository.getDecryptedApiKey()
            if (apiKey.isBlank()) {
                showSnackbar("Add your OpenRouter API key to compress context")
                return@launch
            }
            val conv = activeConversationId.value?.let { vaultRepository.getConversation(it) }
            if (conv == null) {
                showSnackbar("No active conversation to compress")
                return@launch
            }
            val allMsgs = vaultRepository.getMessagesOnce(conv.id)
            val uncompressed = allMsgs.filter { it.createdAt > conv.summarizedUpToTimestamp }
            val keepRecentCount = 4
            if (uncompressed.size <= keepRecentCount + 1) {
                showSnackbar("Need at least ${keepRecentCount + 2} recent turns to compress")
                return@launch
            }
            val toSummarize = uncompressed.dropLast(keepRecentCount)
            val newCutoff = toSummarize.last().createdAt

            isCompressingContext.value = true
            streamClient.summarizeForContextCompression(
                apiKey = apiKey,
                modelId = conv.modelId,
                existingSummary = conv.contextSummary,
                messagesToSummarize = toSummarize,
            ).fold(
                onSuccess = { summary ->
                    vaultRepository.updateConversation(
                        conv.copy(
                            contextSummary = summary,
                            summarizedUpToTimestamp = newCutoff,
                        ),
                    )
                    showSnackbar("Compressed ${toSummarize.size} older turns into rolling memory")
                },
                onFailure = { err ->
                    if (err is OpenRouterApiException && err.alert != null) {
                        openRouterAlert.value = err.alert
                    }
                    showSnackbar(err.message ?: "Context compression failed")
                },
            )
            isCompressingContext.value = false
        }
    }

    fun clearContextSummary() {
        viewModelScope.launch {
            val conv = activeConversationId.value?.let { vaultRepository.getConversation(it) } ?: return@launch
            vaultRepository.updateConversation(
                conv.copy(contextSummary = "", summarizedUpToTimestamp = 0L),
            )
            showSnackbar("Restored full message history to model context")
        }
    }

    fun setCompareModelB(modelId: String) {
        compareState.value = compareState.value.copy(modelBId = modelId)
    }

    fun stopSideBySideCompare() {
        compareJobA?.cancel()
        compareJobB?.cancel()
        compareJobA = null
        compareJobB = null
        val cur = compareState.value
        compareState.value = cur.copy(
            laneA = cur.laneA.copy(isStreaming = false),
            laneB = cur.laneB.copy(isStreaming = false),
        )
    }

    fun runSideBySideCompare(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return
        stopSideBySideCompare()
        viewModelScope.launch {
            val apiKey = preferencesRepository.getDecryptedApiKey()
            if (apiKey.isBlank()) {
                showSnackbar("Add your OpenRouter API key in Settings first")
                return@launch
            }
            val conv = ensureActiveConversation()
            val prefs = preferencesState.value
            val allModels = vaultRepository.observeModels().first()
            val modelAId = conv.modelId
            val modelBId = compareState.value.modelBId.ifBlank {
                allModels.firstOrNull { it.id != modelAId }?.id ?: "openai/gpt-4.1"
            }

            val baseHistory = vaultRepository.getMessagesOnce(conv.id)
            val tempUserMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                role = "user",
                content = cleanPrompt,
                createdAt = System.currentTimeMillis(),
            )
            val combinedHistory = baseHistory + tempUserMsg

            val clockSnapshot = liveClockProvider.refreshNow()
            val folderInstructions = conv.folderId?.let { fId ->
                vaultRepository.observeFolders().first().firstOrNull { it.id == fId }?.projectInstructions.orEmpty()
            }.orEmpty()
            val activePersona = conv.personaId?.let { vaultRepository.getPersonaById(it) }
            val extraLore = if (conv.enabledLorebookIds.isNotEmpty()) {
                vaultRepository.getGlobalLorebooksOnce()
                    .filter { it.id in conv.enabledLorebookIds }
                    .flatMap { it.entries }
            } else emptyList()
            val resolvedPersona = if (activePersona != null || extraLore.isNotEmpty()) {
                personaCardEngine.buildResolvedPrompt(
                    persona = activePersona,
                    history = combinedHistory,
                    userDisplayName = prefs.creatorProfile.displayName,
                    clockSnapshot = clockSnapshot,
                    use24HourClock = prefs.use24HourClock,
                    extraLorebookEntries = extraLore,
                )
            } else null

            compareState.value = ModelCompareUiState(
                prompt = cleanPrompt,
                modelBId = modelBId,
                laneA = CompareLaneState(modelId = modelAId, isStreaming = true),
                laneB = CompareLaneState(modelId = modelBId, isStreaming = true),
            )

            fun launchLane(isLaneA: Boolean, targetModelId: String): Job = viewModelScope.launch {
                val startedAt = System.currentTimeMillis()
                val laneConv = conv.copy(modelId = targetModelId, fallbackModelIds = emptyList())
                val supportsTools = allModels.firstOrNull { it.id == targetModelId }?.supportsTools ?: true
                try {
                    streamClient.streamChatCompletion(
                        apiKey = apiKey,
                        conversation = laneConv,
                        history = combinedHistory,
                        modelSupportsTools = supportsTools,
                        clockSnapshot = clockSnapshot,
                        use24HourClock = prefs.use24HourClock,
                        projectInstructions = folderInstructions,
                        personaSystemBlock = resolvedPersona?.systemBlock.orEmpty(),
                        postHistoryInstructions = resolvedPersona?.postHistoryInstructions.orEmpty(),
                        includeLocalWebFetchTool = false,
                    ).collect { event ->
                        val elapsed = System.currentTimeMillis() - startedAt
                        val cur = compareState.value
                        val lane = if (isLaneA) cur.laneA else cur.laneB
                        val updatedLane = when (event) {
                            is OpenRouterStreamEvent.ContentDelta ->
                                lane.copy(content = lane.content + event.text, elapsedMs = elapsed)
                            is OpenRouterStreamEvent.ReasoningDelta ->
                                lane.copy(reasoningText = lane.reasoningText + event.text, elapsedMs = elapsed)
                            is OpenRouterStreamEvent.ReasoningDetailsUpdated ->
                                lane.copy(reasoningDetailsJson = event.rawJsonArray, elapsedMs = elapsed)
                            is OpenRouterStreamEvent.CitationsUpdated ->
                                lane.copy(citations = event.citations, elapsedMs = elapsed)
                            is OpenRouterStreamEvent.UsageUpdated ->
                                lane.copy(promptTokens = event.promptTokens, completionTokens = event.completionTokens, elapsedMs = elapsed)
                            is OpenRouterStreamEvent.Completed ->
                                lane.copy(
                                    content = event.fullContent.ifBlank { lane.content },
                                    reasoningText = event.fullReasoning.ifBlank { lane.reasoningText },
                                    reasoningDetailsJson = event.reasoningDetailsJson.ifBlank { lane.reasoningDetailsJson },
                                    citations = event.citations.ifEmpty { lane.citations },
                                    promptTokens = event.promptTokens,
                                    completionTokens = event.completionTokens,
                                    isStreaming = false,
                                    elapsedMs = elapsed,
                                )
                            is OpenRouterStreamEvent.ToolCallsRequested -> lane
                        }
                        compareState.value = if (isLaneA) cur.copy(laneA = updatedLane) else cur.copy(laneB = updatedLane)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val cur = compareState.value
                    val lane = (if (isLaneA) cur.laneA else cur.laneB).copy(
                        isStreaming = false,
                        error = e.message ?: "Request failed",
                    )
                    compareState.value = if (isLaneA) cur.copy(laneA = lane) else cur.copy(laneB = lane)
                } finally {
                    val cur = compareState.value
                    val lane = if (isLaneA) cur.laneA else cur.laneB
                    if (lane.isStreaming) {
                        val done = lane.copy(isStreaming = false)
                        compareState.value = if (isLaneA) cur.copy(laneA = done) else cur.copy(laneB = done)
                    }
                }
            }

            compareJobA = launchLane(isLaneA = true, targetModelId = modelAId)
            compareJobB = launchLane(isLaneA = false, targetModelId = modelBId)
        }
    }

    fun commitCompareWinner(chooseModelA: Boolean) {
        val state = compareState.value
        val winner = if (chooseModelA) state.laneA else state.laneB
        val runnerUp = if (chooseModelA) state.laneB else state.laneA
        if (state.prompt.isBlank() || winner.content.isBlank()) return
        stopSideBySideCompare()

        viewModelScope.launch {
            var conv = ensureActiveConversation()
            val existing = vaultRepository.getMessagesOnce(conv.id)
            if (existing.isEmpty() && conv.title == "New Conversation") {
                conv = conv.copy(title = state.prompt.replace('\n', ' ').take(48).trim().ifBlank { "New Conversation" })
                vaultRepository.updateConversation(conv)
            }
            val now = System.currentTimeMillis()
            vaultRepository.saveMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conv.id,
                    role = "user",
                    content = state.prompt,
                    createdAt = now,
                ),
            )
            val winnerSwipe = MessageSwipe(
                content = winner.content,
                reasoningContent = winner.reasoningText,
                reasoningDetailsJson = winner.reasoningDetailsJson,
                citations = winner.citations,
                modelUsed = winner.modelId,
                promptTokens = winner.promptTokens,
                completionTokens = winner.completionTokens,
                createdAt = now + 1L,
            )
            val swipes = buildList {
                add(winnerSwipe)
                if (runnerUp.content.isNotBlank()) {
                    add(
                        MessageSwipe(
                            content = runnerUp.content,
                            reasoningContent = runnerUp.reasoningText,
                            reasoningDetailsJson = runnerUp.reasoningDetailsJson,
                            citations = runnerUp.citations,
                            modelUsed = runnerUp.modelId,
                            promptTokens = runnerUp.promptTokens,
                            completionTokens = runnerUp.completionTokens,
                            createdAt = now + 2L,
                        ),
                    )
                }
            }
            vaultRepository.saveMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conv.id,
                    role = "assistant",
                    content = winnerSwipe.content,
                    reasoningContent = winnerSwipe.reasoningContent,
                    reasoningDetailsJson = winnerSwipe.reasoningDetailsJson,
                    citations = winnerSwipe.citations,
                    modelUsed = winnerSwipe.modelUsed,
                    promptTokens = winnerSwipe.promptTokens,
                    completionTokens = winnerSwipe.completionTokens,
                    swipes = swipes,
                    activeSwipeIndex = 0,
                    createdAt = now + 1L,
                ),
            )
            compareState.value = ModelCompareUiState(modelBId = state.modelBId)
            showSnackbar("Committed ${winner.modelId.substringAfter('/')} (runner-up saved as swipe #2)")
        }
    }

    fun showSnackbar(message: String) {
        snackbarMessage.value = message
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    private suspend fun ensureActiveConversation(initialTitle: String = "New Conversation"): Conversation {
        val currentId = activeConversationId.value
        if (currentId != null) {
            vaultRepository.getConversation(currentId)?.let { return it }
        }
        val prefs = preferencesState.value
        val draft = draftConfig.value
        val folderId = draft.folderId ?: selectedFolderFilterId.value
        val folder = folderId?.let { id ->
            vaultRepository.observeFolders().first().firstOrNull { it.id == id }
        }
        val chosenModelId = draft.modelId
            ?: folder?.defaultModelId?.takeIf { it.isNotBlank() }
            ?: prefs.defaultModelId
        val created = vaultRepository.createConversation(
            modelId = chosenModelId,
            title = initialTitle.ifBlank { "New Conversation" },
            folderId = folderId,
            fallbackModelIds = draft.fallbackModelIds,
            systemPromptOverride = draft.systemPromptOverride,
            temperature = draft.temperature,
            topP = draft.topP,
            maxTokens = draft.maxTokens,
            contextWindowLimit = draft.contextWindowLimit,
            reasoningEffort = draft.reasoningEffort,
            webSearchEnabled = draft.webSearchEnabled ?: prefs.defaultWebSearchEnabled,
            webSearchMode = draft.webSearchMode ?: prefs.defaultWebSearchMode,
            webSearchEngine = draft.webSearchEngine ?: prefs.defaultWebSearchEngine,
            webMaxResults = draft.webMaxResults ?: prefs.defaultWebMaxResults,
            webMaxTotalResults = draft.webMaxTotalResults ?: prefs.defaultWebMaxTotalResults,
        )
        draftConfig.value = DraftConversationConfig()
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
                usageRepository = container.usageRepository,
                gitHubRepository = container.gitHubRepository,
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
