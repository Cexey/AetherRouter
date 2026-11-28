package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import com.example.core.CodeArtifactUtils
import com.example.core.ContextAndCostEstimator
import com.example.core.LiveClockProvider
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.Folder
import com.example.data.model.WebSearchMode
import com.example.ui.components.AetherCrystalEmblem
import com.example.ui.components.aetherCardBorder
import com.example.ui.components.aetherSlateButtonColors
import com.example.ui.components.aetherTextFieldColors
import com.example.ui.components.AppearanceStudioOverlay
import com.example.ui.components.AttachWebpageUrlDialog
import com.example.ui.components.ChatComposerPanel
import com.example.ui.components.ChatDrawerBody
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.CompressedContextBanner
import com.example.ui.components.ContextAndCostBar
import com.example.ui.components.DayDividerHeader
import com.example.ui.components.GitHubStudioOverlay
import com.example.ui.components.InAppBrowserOverlay
import com.example.ui.components.LinkActionChooserSheet
import com.example.ui.components.LiveArtifactPreviewOverlay
import com.example.ui.components.McpToolsHubOverlay
import com.example.ui.components.ModelCompareOverlay
import com.example.ui.components.ModelPickerOverlay
import com.example.ui.components.MoveToFolderDialog
import com.example.ui.components.PersonaStudioOverlay
import com.example.ui.components.ProjectFolderDialog
import com.example.ui.components.SlashCommandEntry
import com.example.ui.components.SlashTemplatesManagerDialog
import com.example.ui.components.StreamingAssistantBubble
import com.example.ui.components.UsageAndCreditsStudioOverlay
import com.example.ui.components.VaultAndPreferencesSheet
import com.example.ui.components.VoiceModeOverlay
import com.example.ui.components.WebAndSamplerSheet
import com.example.ui.theme.LiveWebActiveColor
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.ChatWorkspaceUiState
import com.example.viewmodel.ModelPickerViewModel
import kotlinx.coroutines.launch

private sealed interface TimelineItem {
    val key: String
    data class DayHeader(val dayKey: Int, val label: String) : TimelineItem {
        override val key: String = "day_$dayKey"
    }
    data class MessageRow(val message: ChatMessage) : TimelineItem {
        override val key: String = "msg_${message.id}"
    }
}

private class CreateDocumentWithMimeContract : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent {
        val (fileName, mimeType) = input
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
fun ChatWorkspaceScreen(
    chatViewModel: ChatViewModel,
    modelPickerViewModel: ModelPickerViewModel,
    animatedImageLoader: ImageLoader,
) {
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val pickerState by modelPickerViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showModelPicker by remember { mutableStateOf(false) }
    var showModelCompare by remember { mutableStateOf(false) }
    var showAppearanceStudio by remember { mutableStateOf(false) }
    var showPersonaStudio by remember { mutableStateOf(false) }
    var showMcpHub by remember { mutableStateOf(false) }
    var showVoiceMode by remember { mutableStateOf(false) }
    var showUsageStudio by remember { mutableStateOf(false) }
    var showGitHubStudio by remember { mutableStateOf(false) }
    var showVaultSheet by remember { mutableStateOf(false) }
    var showWebSamplerSheet by remember { mutableStateOf(false) }
    var showAttachUrlDialog by remember { mutableStateOf(false) }
    var showSlashTemplatesDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingFolderDialog by remember { mutableStateOf<Pair<Boolean, Folder?>>(false to null) }
    var movingConversation by remember { mutableStateOf<Conversation?>(null) }

    var pendingSafExportContent by remember { mutableStateOf<String?>(null) }
    var pendingImportPassphrase by remember { mutableStateOf("") }

    // Zero-Permission Photo Picker (Photos, GIFs & Stickers)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8),
    ) { uris ->
        if (uris.isNotEmpty()) chatViewModel.addAttachmentsFromUris(uris)
    }

    val stickerImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) chatViewModel.importCustomStickerFromUri(uri)
    }

    // SAF Multi-Document Picker (PDF, DOCX, ODT, PPTX, XLSX, EPUB, RTF, CSV, JSON, Code, Text)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) chatViewModel.addAttachmentsFromUris(uris)
    }

    // SAF Custom Font Picker (.ttf / .otf)
    val customFontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) chatViewModel.installCustomFontFromUri(uri)
    }

    // SAF CreateDocument for Google Drive / Cloud / Local Save
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = CreateDocumentWithMimeContract(),
    ) { uri ->
        val content = pendingSafExportContent
        pendingSafExportContent = null
        if (uri != null && content != null) {
            chatViewModel.writeExportToSaf(uri, content)
        }
    }

    // SAF OpenDocument for .orvault / .json Backup Restore
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            chatViewModel.importBackupFromSaf(uri, pendingImportPassphrase)
        }
    }

    // SAF OpenDocument for SillyTavern V1/V2 JSON, PNG character cards, and .txt/.md prompts
    val importPersonaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            chatViewModel.importPersonaFromUri(uri)
        }
    }

    // Runtime RECORD_AUDIO permission request for On-Device Voice Mode
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showVoiceMode = true
        } else {
            chatViewModel.showSnackbar("Microphone permission is needed for Voice Mode")
        }
    }

    fun openVoiceModeWithPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showVoiceMode = true
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            chatViewModel.clearSnackbar()
        }
    }

    fun launchExternalBrowser(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            chatViewModel.showSnackbar("No browser app available to open link")
        }
    }

    fun launchEmailIntent(email: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${email.trim()}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            clipboard.setText(AnnotatedString(email))
            chatViewModel.showSnackbar("Email copied to clipboard")
        }
    }

    fun sharePlainText(text: String) {
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share via AetherRouter"))
        }
    }

    fun startShareIntent(intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { chatViewModel.showSnackbar("No share target available") }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 840.dp

        val drawerBodyContent: @Composable (closeAfterAction: Boolean) -> Unit = { closeAfter ->
            ChatDrawerBody(
                folders = uiState.folders,
                selectedFolderFilterId = uiState.selectedFolderFilterId,
                conversations = uiState.conversations,
                activeConversationId = uiState.activeConversation?.id,
                searchQuery = uiState.drawerSearchQuery,
                onSearchChange = chatViewModel::updateDrawerSearch,
                onSelectFolderFilter = chatViewModel::selectFolderFilter,
                onCreateFolderClick = { editingFolderDialog = true to null },
                onEditFolderClick = { folder -> editingFolderDialog = true to folder },
                onNewChat = {
                    chatViewModel.startNewConversation()
                    if (closeAfter) scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    chatViewModel.selectConversation(id)
                    if (closeAfter) scope.launch { drawerState.close() }
                },
                onTogglePinConversation = chatViewModel::togglePinConversation,
                onMoveConversationClick = { conv -> movingConversation = conv },
                onDeleteConversation = chatViewModel::deleteConversation,
                onOpenPersonaStudio = {
                    if (closeAfter) scope.launch { drawerState.close() }
                    showPersonaStudio = true
                },
                onOpenMcpHub = {
                    if (closeAfter) scope.launch { drawerState.close() }
                    showMcpHub = true
                },
                onOpenModelCompare = {
                    if (closeAfter) scope.launch { drawerState.close() }
                    showModelCompare = true
                },
                onOpenWebSampler = {
                    if (closeAfter) scope.launch { drawerState.close() }
                    showWebSamplerSheet = true
                },
                onOpenAppearanceStudio = {
                    if (closeAfter) scope.launch { drawerState.close() }
                    showAppearanceStudio = true
                },
                onOpenSettings = {
                    if (closeAfter) scope.launch { drawerState.close() }
                    showVaultSheet = true
                },
                usageSnapshot = uiState.usageSnapshot,
                hasApiKey = uiState.preferences.hasApiKey,
                isLoadingUsage = uiState.isLoadingCredits,
                onOpenUsageStudio = {
                    if (closeAfter) scope.launch { drawerState.close() }
                    showUsageStudio = true
                    chatViewModel.refreshUsageAndActivity(silent = true)
                },
                onOpenGitHubStudio = {
                    if (closeAfter) scope.launch { drawerState.close() }
                    chatViewModel.clearGitHubPendingPush()
                    showGitHubStudio = true
                },
                use24HourClock = uiState.preferences.use24HourClock,
                nowEpochMillis = uiState.clock.epochMillis,
            )
        }

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    ChatTopHeaderBar(
                        uiState = uiState,
                        showDrawerButton = !isExpandedLayout,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenModelPicker = { showModelPicker = true },
                        onOpenPersonaStudio = { showPersonaStudio = true },
                        onOpenMcpHub = { showMcpHub = true },
                        onToggleWebSearch = chatViewModel::toggleQuickWebSearch,
                        onCompressContext = chatViewModel::compressActiveConversationContext,
                        onOpenVaultSettings = { showVaultSheet = true },
                        onNewChat = chatViewModel::startNewConversation,
                    )
                },
                bottomBar = {
                    ChatComposerPanel(
                        text = uiState.composerText,
                        stagedAttachments = uiState.stagedAttachments,
                        customStickers = uiState.customStickers,
                        smartReplies = uiState.smartReplies,
                        slashTemplates = uiState.slashTemplates,
                        imageLoader = animatedImageLoader,
                        isStreaming = uiState.streaming.isStreaming,
                        isProcessingAttachment = uiState.isProcessingAttachment,
                        isFetchingWebpage = uiState.isFetchingWebpage,
                        hasApiKey = uiState.preferences.hasApiKey,
                        onTextChange = chatViewModel::updateComposerText,
                        onSend = { chatViewModel.sendMessage() },
                        onStop = chatViewModel::stopStreaming,
                        onSelectSmartReply = { suggestion ->
                            chatViewModel.sendMessage(overrideText = suggestion)
                        },
                        onSlashCommandAction = { entry ->
                            when (entry) {
                                SlashCommandEntry.ToggleWeb -> chatViewModel.toggleQuickWebSearch()
                                SlashCommandEntry.CompareModels -> showModelCompare = true
                                SlashCommandEntry.OpenPersonas -> showPersonaStudio = true
                                SlashCommandEntry.CompressContext -> chatViewModel.compressActiveConversationContext()
                                SlashCommandEntry.NewChat -> chatViewModel.startNewConversation()
                                SlashCommandEntry.ClearChat -> chatViewModel.clearActiveConversationMessages()
                                is SlashCommandEntry.CustomTemplate -> Unit
                            }
                        },
                        onManageSlashTemplatesClick = { showSlashTemplatesDialog = true },
                        onOpenVoiceMode = ::openVoiceModeWithPermission,
                        onOpenKeySetup = { showVaultSheet = true },
                        onAttachUrl = chatViewModel::attachWebpageToComposer,
                        onPickPhotosClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onPickDocumentsClick = {
                            documentPickerLauncher.launch(arrayOf("*/*"))
                        },
                        onOpenAttachUrlDialog = { showAttachUrlDialog = true },
                        onBrowseGitHubClick = {
                            chatViewModel.clearGitHubPendingPush()
                            showGitHubStudio = true
                        },
                        openRouterAlert = uiState.openRouterAlert,
                        onDismissAlert = chatViewModel::dismissOpenRouterAlert,
                        onOpenUsageStudio = {
                            showUsageStudio = true
                            chatViewModel.refreshUsageAndActivity(silent = true)
                        },
                        onRemoveStagedAttachment = chatViewModel::removeStagedAttachment,
                        onRichKeyboardMedia = { uris -> chatViewModel.addAttachmentsFromUris(uris) },
                        onAttachSticker = chatViewModel::attachCustomSticker,
                        onImportStickerClick = {
                            stickerImportLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onDeleteSticker = chatViewModel::deleteCustomSticker,
                    )
                },
            ) { innerPadding ->
                ChatTimelineContent(
                    uiState = uiState,
                    imageLoader = animatedImageLoader,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onOpenKeySetup = { showVaultSheet = true },
                    onOpenModelPicker = { showModelPicker = true },
                    onOpenModelCompare = { showModelCompare = true },
                    onOpenPersonaStudio = { showPersonaStudio = true },
                    onOpenMcpHub = { showMcpHub = true },
                    onOpenAppearanceStudio = { showAppearanceStudio = true },
                    onOpenWebSampler = { showWebSamplerSheet = true },
                    onClearContextSummary = chatViewModel::clearContextSummary,
                    onQuickPrompt = { prompt ->
                        chatViewModel.updateComposerText(prompt)
                        if (uiState.preferences.hasApiKey) {
                            chatViewModel.sendMessage(prompt)
                        } else {
                            showVaultSheet = true
                        }
                    },
                    onToggleSpeakMessage = chatViewModel::toggleSpeakMessage,
                    onSwipeMessage = chatViewModel::selectMessageSwipe,
                    onForkMessage = chatViewModel::forkConversationAtMessage,
                    onLinkClick = { url ->
                        chatViewModel.handleUrlClick(url, ::launchExternalBrowser)
                    },
                    onCopyText = { text ->
                        clipboard.setText(AnnotatedString(text))
                        chatViewModel.showSnackbar("Copied to clipboard")
                    },
                    onSaveCodeFile = { language, code ->
                        val spec = CodeArtifactUtils.buildCodeExportSpec(language, code, baseName = "snippet")
                        pendingSafExportContent = spec.code
                        createDocumentLauncher.launch(spec.suggestedFileName to spec.mimeType)
                    },
                    onRunLivePreview = { language, code ->
                        chatViewModel.openLiveArtifactPreview(language, code)
                    },
                    onPushCodeToGitHub = { language, code ->
                        chatViewModel.startGitHubPushForCode(language, code)
                        showGitHubStudio = true
                    },
                    onShareText = ::sharePlainText,
                    onToggleReaction = { msg, emoji -> chatViewModel.toggleReaction(msg, emoji) },
                    onRegenerate = chatViewModel::regenerateAssistantMessage,
                    onEdit = { editingMessage = it },
                    onDelete = { chatViewModel.deleteMessage(it.id) },
                )
            }
        }

        if (isExpandedLayout) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.width(310.dp),
                    ) {
                        drawerBodyContent(false)
                    }
                },
                content = mainScaffold,
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.widthIn(max = 320.dp),
                    ) {
                        drawerBodyContent(true)
                    }
                },
                content = mainScaffold,
            )
        }

        // Full-Screen Stateful High-Visibility Model Picker Overlay
        if (showModelPicker) {
            ModelPickerOverlay(
                uiState = pickerState,
                activeModelId = uiState.activeConversation?.modelId ?: uiState.preferences.defaultModelId,
                onSelectModel = { model ->
                    chatViewModel.selectModelForActiveConversation(model)
                    showModelPicker = false
                },
                onClose = { showModelPicker = false },
                onSearchChange = modelPickerViewModel::updateSearchQuery,
                onSelectSortMode = modelPickerViewModel::selectSortMode,
                onToggleSortDirection = modelPickerViewModel::toggleSortDirection,
                onSelectCompany = modelPickerViewModel::selectCompanyFilter,
                onToggleFavFilter = modelPickerViewModel::toggleFilterFavorites,
                onToggleVisionFilter = modelPickerViewModel::toggleFilterVision,
                onToggleReasoningFilter = modelPickerViewModel::toggleFilterReasoning,
                onToggleFreeFilter = modelPickerViewModel::toggleFilterFree,
                onToggleToolsFilter = modelPickerViewModel::toggleFilterTools,
                onClearFilters = modelPickerViewModel::clearFiltersAndSearch,
                onSaveScrollPosition = modelPickerViewModel::saveScrollPosition,
                onToggleModelFavorite = modelPickerViewModel::toggleModelFavorite,
                onRefreshCatalog = modelPickerViewModel::refreshCatalogFromOpenRouter,
            )
        }

        // Full-Screen Appearance, Typography & Zoom Studio Overlay
        if (showAppearanceStudio) {
            AppearanceStudioOverlay(
                appearance = uiState.preferences.appearance,
                onClose = { showAppearanceStudio = false },
                onUpdateAppearance = chatViewModel::updateAppearance,
                onUploadCustomFontClick = {
                    customFontPickerLauncher.launch(arrayOf("*/*"))
                },
                onResetDefaults = chatViewModel::resetAppearanceDefaults,
            )
        }

        // Full-Screen Persona & SillyTavern V2 Studio + Shared Lorebooks Overlay
        if (showPersonaStudio) {
            PersonaStudioOverlay(
                personas = uiState.personas,
                activePersonaId = uiState.activeConversation?.personaId,
                globalLorebooks = uiState.globalLorebooks,
                enabledLorebookIds = uiState.activeConversation?.enabledLorebookIds ?: emptyList(),
                creatorDisplayName = uiState.preferences.creatorProfile.displayName,
                imageLoader = animatedImageLoader,
                onClose = { showPersonaStudio = false },
                onImportPersonaClick = {
                    importPersonaLauncher.launch(arrayOf("*/*"))
                },
                onSavePersona = chatViewModel::savePersona,
                onDeletePersona = chatViewModel::deletePersona,
                onApplyPersona = chatViewModel::applyPersonaToActiveChat,
                onDetachPersona = chatViewModel::detachPersonaFromActiveChat,
                onExportPersonaV2Saf = { persona ->
                    chatViewModel.preparePersonaV2Export(persona) { name, mime, content ->
                        pendingSafExportContent = content
                        createDocumentLauncher.launch(name to mime)
                    }
                },
                onSharePersonaV2 = { persona ->
                    chatViewModel.sharePersonaV2Json(persona, ::startShareIntent)
                },
                onSaveGlobalLorebook = chatViewModel::saveGlobalLorebook,
                onDeleteGlobalLorebook = chatViewModel::deleteGlobalLorebook,
                onToggleGlobalLorebook = chatViewModel::toggleGlobalLorebookForActiveChat,
            )
        }

        // Full-Screen Side-by-Side Model Compare Arena
        if (showModelCompare) {
            ModelCompareOverlay(
                compareState = uiState.compareState,
                primaryModelId = uiState.activeConversation?.modelId ?: uiState.preferences.defaultModelId,
                allModels = uiState.allModels,
                onClose = { showModelCompare = false },
                onSelectModelB = chatViewModel::setCompareModelB,
                onRunCompare = chatViewModel::runSideBySideCompare,
                onStopCompare = chatViewModel::stopSideBySideCompare,
                onCommitWinner = { chooseA ->
                    chatViewModel.commitCompareWinner(chooseA)
                    showModelCompare = false
                },
                onLinkClick = { url ->
                    chatViewModel.handleUrlClick(url, ::launchExternalBrowser)
                },
                onCopyText = { text ->
                    clipboard.setText(AnnotatedString(text))
                    chatViewModel.showSnackbar("Copied to clipboard")
                },
            )
        }

        // Full-Screen MCP Tools Hub Overlay
        if (showMcpHub) {
            McpToolsHubOverlay(
                servers = uiState.mcpServers,
                enabledServerIdsForChat = uiState.activeConversation?.enabledMcpServerIds ?: emptyList(),
                syncingServerId = uiState.isSyncingMcpServerId,
                onClose = { showMcpHub = false },
                onSaveServer = chatViewModel::saveMcpServer,
                onDeleteServer = chatViewModel::deleteMcpServer,
                onSyncTools = chatViewModel::syncMcpServerTools,
                onToggleForActiveChat = chatViewModel::toggleMcpServerForActiveChat,
            )
        }

        // Full-Screen On-Device Voice Mode Overlay
        if (showVoiceMode) {
            VoiceModeOverlay(
                voiceState = uiState.voiceState,
                isStreamingResponse = uiState.streaming.isStreaming,
                onClose = { showVoiceMode = false },
                onStartListening = chatViewModel::startVoiceListening,
                onStopListening = chatViewModel::stopVoiceListening,
                onSendTranscriptNow = chatViewModel::sendVoiceTranscriptNow,
                onStopSpeaking = chatViewModel::stopSpeaking,
            )
        }

        // Full-Screen Usage & Credits Studio Overlay
        if (showUsageStudio) {
            UsageAndCreditsStudioOverlay(
                usageSnapshot = uiState.usageSnapshot,
                activityBreakdown = uiState.activityBreakdown,
                hasApiKey = uiState.preferences.hasApiKey,
                hasManagementApiKey = uiState.preferences.hasManagementApiKey,
                isLoadingUsage = uiState.isLoadingCredits,
                isLoadingActivity = uiState.isLoadingActivity,
                onClose = { showUsageStudio = false },
                onRefreshAll = { chatViewModel.refreshUsageAndActivity(silent = false) },
                onOpenSettingsForKeys = {
                    showUsageStudio = false
                    showVaultSheet = true
                },
            )
        }

        // Full-Screen GitHub Repo Browser & Commit Studio Overlay
        if (showGitHubStudio) {
            GitHubStudioOverlay(
                state = uiState.gitHubState,
                hasGitHubPat = uiState.preferences.hasGitHubPat,
                onClose = { showGitHubStudio = false },
                onOpenSettings = {
                    showGitHubStudio = false
                    showVaultSheet = true
                },
                onRefreshRepos = chatViewModel::refreshGitHubRepos,
                onSelectRepo = chatViewModel::selectGitHubRepo,
                onBackToRepos = chatViewModel::backToGitHubRepos,
                onSelectBranch = chatViewModel::selectGitHubBranch,
                onNavigatePath = chatViewModel::navigateGitHubPath,
                onAttachFileToChat = { entry ->
                    chatViewModel.attachGitHubFileToComposer(entry) {
                        showGitHubStudio = false
                    }
                },
                onPrepareCommitPreview = chatViewModel::prepareGitHubCommitPreview,
                onConfirmCommit = chatViewModel::confirmGitHubCommit,
                onClearCommitPreview = chatViewModel::clearGitHubCommitPreview,
                onClearPendingPush = chatViewModel::clearGitHubPendingPush,
                onOpenCommitUrl = { url ->
                    chatViewModel.handleUrlClick(url, ::launchExternalBrowser)
                },
            )
        }

        // Full-Screen Offline-Sandboxed HTML/CSS/JS Live Artifact Preview Overlay
        val activeArtifact = uiState.liveArtifactPreview
        if (activeArtifact != null) {
            LiveArtifactPreviewOverlay(
                artifact = activeArtifact,
                onClose = chatViewModel::dismissLiveArtifactPreview,
                onExportHtml = { fileName, htmlContent ->
                    pendingSafExportContent = htmlContent
                    createDocumentLauncher.launch(fileName to "text/html")
                },
            )
        }

        // Hardened Full-Screen In-App Browser Overlay
        val activeUrl = uiState.inAppBrowserUrl
        if (activeUrl != null) {
            InAppBrowserOverlay(
                initialUrl = activeUrl,
                isAttachingPage = uiState.isFetchingWebpage,
                onClose = chatViewModel::dismissInAppBrowser,
                onOpenExternal = ::launchExternalBrowser,
                onAttachPageToChat = chatViewModel::attachWebpageToComposer,
                onCopyUrl = { url ->
                    clipboard.setText(AnnotatedString(url))
                    chatViewModel.showSnackbar("URL copied")
                },
                onShareUrl = ::sharePlainText,
            )
        }
    }

    // Link Action Chooser Sheet (when LinkOpenBehavior.ASK is active)
    val pendingUrl = uiState.pendingLinkChoiceUrl
    if (pendingUrl != null) {
        LinkActionChooserSheet(
            url = pendingUrl,
            onDismiss = chatViewModel::dismissLinkChooser,
            onOpenInApp = { chatViewModel.openInAppBrowserDirectly(pendingUrl) },
            onOpenExternal = {
                chatViewModel.dismissLinkChooser()
                launchExternalBrowser(pendingUrl)
            },
            onAttachToChat = { chatViewModel.attachWebpageToComposer(pendingUrl) },
            onCopy = {
                clipboard.setText(AnnotatedString(pendingUrl))
                chatViewModel.dismissLinkChooser()
                chatViewModel.showSnackbar("URL copied")
            },
        )
    }

    // Vault, Creator Profile, Export & Core Preferences Sheet
    if (showVaultSheet) {
        VaultAndPreferencesSheet(
            preferences = uiState.preferences,
            hasActiveConversation = uiState.activeConversation != null,
            creditBalance = uiState.creditBalance,
            usageSnapshot = uiState.usageSnapshot,
            isLoadingCredits = uiState.isLoadingCredits,
            onRefreshCredits = { chatViewModel.refreshCreditBalance(silent = false) },
            onOpenUsageStudio = {
                showUsageStudio = true
                chatViewModel.refreshUsageAndActivity(silent = true)
            },
            onSaveManagementApiKey = chatViewModel::saveManagementApiKeyAndSync,
            onSaveGitHubPat = chatViewModel::saveGitHubPatAndConnect,
            onOpenGitHubStudio = {
                chatViewModel.clearGitHubPendingPush()
                showGitHubStudio = true
            },
            onDismiss = { showVaultSheet = false },
            onSaveApiKey = chatViewModel::saveApiKeyAndSync,
            onOpenAppearanceStudio = { showAppearanceStudio = true },
            onThemeModeChange = chatViewModel::setThemeMode,
            onDynamicColorChange = chatViewModel::setUseDynamicColor,
            on24HourClockChange = chatViewModel::setUse24HourClock,
            onShowTimestampsChange = chatViewModel::setShowMessageTimestamps,
            onLinkBehaviorChange = chatViewModel::setLinkOpenBehavior,
            onBiometricLockChange = chatViewModel::setBiometricLockEnabled,
            onSmartRepliesChange = chatViewModel::setSmartRepliesEnabled,
            onSaveCreatorProfile = chatViewModel::saveCreatorProfile,
            onProfileLinkClick = { url ->
                chatViewModel.handleUrlClick(url, ::launchExternalBrowser)
            },
            onProfileEmailClick = ::launchEmailIntent,
            onExportChatSaf = { format ->
                chatViewModel.prepareChatExport(format) { name, mime, content ->
                    pendingSafExportContent = content
                    createDocumentLauncher.launch(name to mime)
                }
            },
            onShareChatFile = { format ->
                chatViewModel.shareActiveChatFile(format, ::startShareIntent)
            },
            onExportVaultSaf = { passphrase ->
                chatViewModel.prepareVaultExport(passphrase) { name, mime, content ->
                    pendingSafExportContent = content
                    createDocumentLauncher.launch(name to mime)
                }
            },
            onShareVaultFile = { passphrase ->
                chatViewModel.shareVaultBackupFile(passphrase, ::startShareIntent)
            },
            onImportBackupSaf = { passphrase ->
                pendingImportPassphrase = passphrase
                importBackupLauncher.launch(arrayOf("*/*"))
            },
        )
    }

    // Live Web, Presets, Fallback Routing & Sampler Studio Sheet
    if (showWebSamplerSheet) {
        WebAndSamplerSheet(
            conversation = uiState.activeConversation,
            preferences = uiState.preferences,
            allModels = uiState.allModels,
            samplerPresets = uiState.samplerPresets,
            onSaveSamplerPreset = chatViewModel::saveSamplerPreset,
            onDeleteSamplerPreset = chatViewModel::deleteSamplerPreset,
            onDismiss = { showWebSamplerSheet = false },
            onSave = chatViewModel::updateConversationSettings,
        )
    }

    // Custom /Slash Prompt Templates Manager Dialog
    if (showSlashTemplatesDialog) {
        SlashTemplatesManagerDialog(
            templates = uiState.slashTemplates,
            onDismiss = { showSlashTemplatesDialog = false },
            onSaveTemplate = chatViewModel::saveSlashTemplate,
            onDeleteTemplate = chatViewModel::deleteSlashTemplate,
        )
    }

    // Create / Edit Project Folder Dialog
    if (editingFolderDialog.first) {
        ProjectFolderDialog(
            existingFolder = editingFolderDialog.second,
            onDismiss = { editingFolderDialog = false to null },
            onSave = chatViewModel::saveFolder,
            onDelete = chatViewModel::deleteFolder,
        )
    }

    // Move Conversation to Project Folder Dialog
    val convToMove = movingConversation
    if (convToMove != null) {
        MoveToFolderDialog(
            folders = uiState.folders,
            currentFolderId = convToMove.folderId,
            onDismiss = { movingConversation = null },
            onSelectFolder = { folderId ->
                chatViewModel.moveConversationToFolder(convToMove.id, folderId)
            },
        )
    }

    // Manual Attach Webpage URL Dialog
    if (showAttachUrlDialog) {
        AttachWebpageUrlDialog(
            onDismiss = { showAttachUrlDialog = false },
            onAttachUrl = chatViewModel::attachWebpageToComposer,
        )
    }

    // Edit Message Dialog
    val msgToEdit = editingMessage
    if (msgToEdit != null) {
        EditMessageDialog(
            message = msgToEdit,
            onDismiss = { editingMessage = null },
            onSave = { updated, rerun ->
                chatViewModel.editMessage(msgToEdit, updated, reRunIfUser = rerun)
                editingMessage = null
            },
        )
    }
}

@Composable
private fun ChatTopHeaderBar(
    uiState: ChatWorkspaceUiState,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenPersonaStudio: () -> Unit,
    onOpenMcpHub: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onCompressContext: () -> Unit,
    onOpenVaultSettings: () -> Unit,
    onNewChat: () -> Unit,
) {
    val activeModelName = uiState.activeModel?.name
        ?: uiState.activeConversation?.modelId?.substringAfter('/')
        ?: uiState.preferences.defaultModelId.substringAfter('/')
    val webEnabled = uiState.activeConversation?.webSearchEnabled ?: uiState.preferences.defaultWebSearchEnabled
    val webMode = uiState.activeConversation?.webSearchMode ?: uiState.preferences.defaultWebSearchMode
    val webLabel = when {
        !webEnabled -> "Web: Off"
        webMode == WebSearchMode.AGENTIC_TOOL -> "Web: Agentic"
        else -> "Web: Always-On"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("open_drawer_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = "Open conversations",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Widened Model Selector Pill
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = aetherCardBorder(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenModelPicker)
                            .testTag("model_selector_pill"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.preferences.hasApiKey) LiveWebActiveColor
                                        else MaterialTheme.colorScheme.tertiary,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeModelName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Choose model",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenVaultSettings,
                        modifier = Modifier.testTag("open_vault_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = "API key, credits, profile, export, and settings",
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.testTag("new_chat_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "New conversation",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Live Android System Clock & Live Web Grounding Status Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = "Live Android clock",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${uiState.clock.headerLabel(uiState.preferences.use24HourClock)} ${uiState.clock.timezoneShort}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.testTag("live_clock_pill"),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (webEnabled) {
                            LiveWebActiveColor.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (webEnabled) {
                                LiveWebActiveColor.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onToggleWebSearch)
                            .testTag("live_web_toggle_pill"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = "Toggle live web search",
                                tint = if (webEnabled) LiveWebActiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = webLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (webEnabled) LiveWebActiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }

            // Active Persona, Shared Lorebooks & MCP Tools Badges (when configured on current chat)
            val activePersona = uiState.activePersona
            val activeMcpCount = uiState.activeConversation?.enabledMcpServerIds?.size ?: 0
            val activeLoreCount = uiState.activeConversation?.enabledLorebookIds?.size ?: 0
            if (activePersona != null || activeMcpCount > 0 || activeLoreCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activePersona != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = aetherCardBorder(selected = true, accent = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onOpenPersonaStudio)
                                .testTag("active_persona_header_pill"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Face,
                                    contentDescription = "Active persona",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = activePersona.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (activeLoreCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = aetherCardBorder(selected = true),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onOpenPersonaStudio)
                                .testTag("active_lorebook_header_pill"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MenuBook,
                                    contentDescription = "Active lorebooks",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = if (uiState.lastMatchedLorebookCount > 0) {
                                        "Lore: $activeLoreCount (${uiState.lastMatchedLorebookCount} hit)"
                                    } else {
                                        "Lore: $activeLoreCount"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (activeMcpCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onOpenMcpHub)
                                .testTag("active_mcp_header_pill"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Extension,
                                    contentDescription = "Active MCP servers",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "MCP: $activeMcpCount active",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Live Context Window Meter, Estimated Cost & One-Tap Context Compression Bar
            if (uiState.messages.isNotEmpty() || uiState.stagedAttachments.isNotEmpty() || uiState.activePersona != null) {
                ContextAndCostBar(
                    metrics = uiState.contextCostMetrics,
                    isCompressing = uiState.isCompressingContext,
                    canCompress = uiState.contextCostMetrics.activeMessagesCount >= 6 &&
                        !uiState.streaming.isStreaming &&
                        uiState.preferences.hasApiKey,
                    onCompressClick = onCompressContext,
                )
            }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ChatTimelineContent(
    uiState: ChatWorkspaceUiState,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    onOpenKeySetup: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenModelCompare: () -> Unit,
    onOpenPersonaStudio: () -> Unit,
    onOpenMcpHub: () -> Unit,
    onOpenAppearanceStudio: () -> Unit,
    onOpenWebSampler: () -> Unit,
    onClearContextSummary: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onToggleSpeakMessage: (ChatMessage) -> Unit,
    onSwipeMessage: (ChatMessage, Int) -> Unit,
    onForkMessage: (ChatMessage) -> Unit,
    onLinkClick: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onSaveCodeFile: (language: String, code: String) -> Unit,
    onRunLivePreview: (language: String, code: String) -> Unit,
    onPushCodeToGitHub: (language: String, code: String) -> Unit,
    onShareText: (String) -> Unit,
    onToggleReaction: (ChatMessage, String) -> Unit,
    onRegenerate: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
) {
    val listState = rememberLazyListState()
    val modelById = remember(uiState.allModels) { uiState.allModels.associateBy { it.id } }

    val timelineItems = remember(uiState.messages, uiState.clock.epochMillis) {
        buildList {
            var lastDayBucket = -1
            for (msg in uiState.messages) {
                val bucket = LiveClockProvider.dayBucketKey(msg.createdAt)
                if (bucket != lastDayBucket) {
                    add(
                        TimelineItem.DayHeader(
                            dayKey = bucket,
                            label = LiveClockProvider.formatDayDividerLabel(msg.createdAt, uiState.clock.epochMillis),
                        ),
                    )
                    lastDayBucket = bucket
                }
                add(TimelineItem.MessageRow(msg))
            }
        }
    }

    LaunchedEffect(
        uiState.activeConversation?.id,
        timelineItems.size,
        uiState.streaming.content.length,
        uiState.streaming.reasoningText.length,
    ) {
        val total = timelineItems.size + if (uiState.streaming.isStreaming) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    if (timelineItems.isEmpty() && !uiState.streaming.isStreaming) {
        EmptyChatCanvas(
            uiState = uiState,
            modifier = modifier,
            onOpenKeySetup = onOpenKeySetup,
            onOpenModelPicker = onOpenModelPicker,
            onOpenModelCompare = onOpenModelCompare,
            onOpenPersonaStudio = onOpenPersonaStudio,
            onOpenMcpHub = onOpenMcpHub,
            onOpenAppearanceStudio = onOpenAppearanceStudio,
            onOpenWebSampler = onOpenWebSampler,
            onQuickPrompt = onQuickPrompt,
        )
        return
    }

    val contextSummary = uiState.activeConversation?.contextSummary.orEmpty()

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (contextSummary.isNotBlank()) {
            item(key = "compressed_context_banner") {
                CompressedContextBanner(
                    summary = contextSummary,
                    compressedCount = uiState.contextCostMetrics.compressedMessagesCount,
                    onClearSummary = onClearContextSummary,
                )
            }
        }

        items(items = timelineItems, key = { it.key }) { item ->
            when (item) {
                is TimelineItem.DayHeader -> DayDividerHeader(label = item.label)
                is TimelineItem.MessageRow -> {
                    val isSpeakingThis = uiState.voiceState.isSpeaking &&
                        uiState.voiceState.speakingMessageId == item.message.id
                    val msgCost = remember(item.message, modelById, uiState.activeModel) {
                        ContextAndCostEstimator.estimateMessageCostUsd(
                            message = item.message,
                            modelById = modelById,
                            fallbackModel = uiState.activeModel,
                        )
                    }
                    ChatMessageItem(
                        message = item.message,
                        imageLoader = imageLoader,
                        use24HourClock = uiState.preferences.use24HourClock,
                        showTimestamps = uiState.preferences.showMessageTimestamps,
                        estimatedCostUsd = msgCost,
                        isSpeakingThisMessage = isSpeakingThis,
                        onToggleSpeak = { onToggleSpeakMessage(item.message) },
                        onSwipePrevious = {
                            onSwipeMessage(item.message, item.message.activeSwipeIndex - 1)
                        },
                        onSwipeNext = {
                            onSwipeMessage(item.message, item.message.activeSwipeIndex + 1)
                        },
                        onForkHere = { onForkMessage(item.message) },
                        onLinkClick = onLinkClick,
                        onCopyText = onCopyText,
                        onSaveCodeFile = onSaveCodeFile,
                        onRunLivePreview = onRunLivePreview,
                        onPushCodeToGitHub = onPushCodeToGitHub,
                        onShareText = onShareText,
                        onToggleReaction = { emoji -> onToggleReaction(item.message, emoji) },
                        onRegenerate = { onRegenerate(item.message) },
                        onEdit = { onEdit(item.message) },
                        onDelete = { onDelete(item.message) },
                    )
                }
            }
        }

        if (uiState.streaming.isStreaming) {
            item(key = "streaming_bubble") {
                StreamingAssistantBubble(
                    modelId = uiState.streaming.modelId,
                    statusNote = uiState.streaming.statusNote,
                    activeToolName = uiState.streaming.activeToolName,
                    content = uiState.streaming.content,
                    reasoningText = uiState.streaming.reasoningText,
                    hasEncryptedSignature = uiState.streaming.hasEncryptedSignature,
                    thinkingElapsedMs = uiState.streaming.thinkingElapsedMs,
                    citations = uiState.streaming.citations,
                    onLinkClick = onLinkClick,
                    onCopyCode = onCopyText,
                    onSaveCodeFile = onSaveCodeFile,
                    onRunLivePreview = onRunLivePreview,
                    onPushCodeToGitHub = onPushCodeToGitHub,
                )
            }
        }
    }
}

@Composable
private fun EmptyChatCanvas(
    uiState: ChatWorkspaceUiState,
    modifier: Modifier = Modifier,
    onOpenKeySetup: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenModelCompare: () -> Unit,
    onOpenPersonaStudio: () -> Unit,
    onOpenMcpHub: () -> Unit,
    onOpenAppearanceStudio: () -> Unit,
    onOpenWebSampler: () -> Unit,
    onQuickPrompt: (String) -> Unit,
) {
    val bgGlow = Brush.radialGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.background,
        ),
    )
    Box(
        modifier = modifier
            .background(bgGlow)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AetherCrystalEmblem(
                size = 92.dp,
                cornerRadius = 24.dp,
                showHalo = true,
                haloSize = 116.dp,
                contentDescription = "AetherRouter",
                modifier = Modifier.testTag("empty_hero_logo"),
            )

            Text(
                text = "AetherRouter",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Encrypted OpenRouter studio with live web grounding, custom fonts & zoom, multimodal attachments, and project workspaces.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            if (!uiState.preferences.hasApiKey) {
                Button(
                    onClick = onOpenKeySetup,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("empty_configure_key_button"),
                ) {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configure Encrypted OpenRouter Key",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val pillPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            val pillColors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            val pillBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            val cobaltAccent = MaterialTheme.colorScheme.primary
            val goldAccent = MaterialTheme.colorScheme.tertiary

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenModelPicker,
                    colors = pillColors,
                    border = pillBorder,
                    contentPadding = pillPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_browse_models_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = cobaltAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Models",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onOpenPersonaStudio,
                    colors = pillColors,
                    border = pillBorder,
                    contentPadding = pillPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_personas_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Face,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Personas",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenModelCompare,
                    colors = pillColors,
                    border = pillBorder,
                    contentPadding = pillPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_compare_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CompareArrows,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Compare",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onOpenAppearanceStudio,
                    colors = pillColors,
                    border = pillBorder,
                    contentPadding = pillPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_appearance_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = cobaltAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Theme",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenWebSampler,
                    colors = pillColors,
                    border = pillBorder,
                    contentPadding = pillPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_web_sampler_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = cobaltAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Web & Sampler",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onOpenMcpHub,
                    colors = pillColors,
                    border = pillBorder,
                    contentPadding = pillPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_mcp_hub_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MCP Tools",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val quickPrompts = listOf(
                "What major AI & tech stories broke today? Include clickable sources.",
                "Summarize an attached PDF, DOCX, or webpage with key takeaways.",
                "Help me design a custom system prompt for deep research.",
            )
            quickPrompts.forEach { sample ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onQuickPrompt(sample) },
                ) {
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditMessageDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var draft by remember(message) { mutableStateOf(message.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Edit Message") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.role == "user") {
                    Button(onClick = { onSave(draft, true) }) {
                        Text("Save & Re-run")
                    }
                }
                TextButton(onClick = { onSave(draft, false) }) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
