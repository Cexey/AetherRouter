package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GitHubBranchSummary
import com.example.data.model.GitHubCommitPreview
import com.example.data.model.GitHubCommitResult
import com.example.data.model.GitHubContentEntry
import com.example.data.model.GitHubPendingPush
import com.example.data.model.GitHubRepoSummary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiveWebActiveColor

data class GitHubStudioUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val authenticatedUser: String? = null,
    val repos: List<GitHubRepoSummary> = emptyList(),
    val selectedRepo: GitHubRepoSummary? = null,
    val branches: List<GitHubBranchSummary> = emptyList(),
    val selectedBranch: String = "",
    val currentPath: String = "",
    val contents: List<GitHubContentEntry> = emptyList(),
    val pendingPush: GitHubPendingPush? = null,
    val commitPreview: GitHubCommitPreview? = null,
    val lastCommitResult: GitHubCommitResult? = null,
    val isAttachingFile: Boolean = false,
    val isCommitting: Boolean = false,
)

/**
 * Full-screen Lightweight GitHub Studio supporting:
 * 1. Browsing repositories, switching branches, navigating folders, and attaching files directly to chat.
 * 2. Pushing AI-generated code blocks/documents to any selected branch with a live Commit Preview
 *    (automatically fetching existing file SHA to distinguish Create vs Update).
 */
@Composable
fun GitHubStudioOverlay(
    state: GitHubStudioUiState,
    hasGitHubPat: Boolean,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshRepos: () -> Unit,
    onSelectRepo: (GitHubRepoSummary) -> Unit,
    onBackToRepos: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onNavigatePath: (String) -> Unit,
    onAttachFileToChat: (GitHubContentEntry) -> Unit,
    onPrepareCommitPreview: (branch: String, targetPath: String, commitMessage: String) -> Unit,
    onConfirmCommit: () -> Unit,
    onClearCommitPreview: () -> Unit,
    onClearPendingPush: () -> Unit,
    onOpenCommitUrl: (String) -> Unit,
) {
    BackHandler {
        when {
            state.commitPreview != null -> onClearCommitPreview()
            state.selectedRepo != null && state.currentPath.isNotEmpty() -> {
                val parent = state.currentPath.substringBeforeLast('/', "")
                onNavigatePath(parent)
            }
            state.selectedRepo != null -> onBackToRepos()
            else -> onClose()
        }
    }

    var repoSearch by remember { mutableStateOf("") }
    var branchMenuOpen by remember { mutableStateOf(false) }

    val pendingPush = state.pendingPush
    var targetPathInput by remember(pendingPush, state.currentPath) {
        val suggested = pendingPush?.suggestedFileName ?: "snippet.txt"
        val defaultPath = if (state.currentPath.isBlank()) suggested else "${state.currentPath}/$suggested"
        mutableStateOf(defaultPath)
    }
    var commitMessageInput by remember(pendingPush) {
        val name = pendingPush?.suggestedFileName ?: "snippet.txt"
        mutableStateOf("Add $name via AetherRouter")
    }

    LaunchedEffect(hasGitHubPat) {
        if (hasGitHubPat && state.repos.isEmpty() && !state.isLoading) {
            onRefreshRepos()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (state.selectedRepo != null && state.commitPreview == null) {
                                    onBackToRepos()
                                } else {
                                    onClose()
                                }
                            },
                            modifier = Modifier.testTag("github_studio_back_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    pendingPush != null && state.selectedRepo != null ->
                                        "Push to ${state.selectedRepo.name}"
                                    pendingPush != null -> "Select Repo to Push Code"
                                    state.selectedRepo != null -> state.selectedRepo.fullName
                                    else -> "GitHub Repo Studio"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = state.authenticatedUser?.let { "Signed in as @$it" }
                                    ?: "api.github.com • Encrypted PAT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                        if (hasGitHubPat) {
                            IconButton(
                                onClick = onRefreshRepos,
                                enabled = !state.isLoading,
                                modifier = Modifier.testTag("github_refresh_button"),
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Refresh repositories",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (!hasGitHubPat) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = aetherCardBorder(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = "Connect Your GitHub Personal Access Token",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "Save a fine-grained or classic GitHub PAT in Settings (encrypted via Android Keystore AES-256-GCM) to browse repositories, attach files to chat, and commit AI-generated code directly to any branch.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                            Button(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("github_open_settings_button"),
                            ) {
                                Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Configure GitHub PAT in Settings")
                            }
                        }
                    }
                }
                return@Column
            }

            // Error banner if present
            if (!state.errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Success banner after a commit completes
            val commitResult = state.lastCommitResult
            if (commitResult != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LiveWebActiveColor.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Committed",
                            tint = LiveWebActiveColor,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (commitResult.isUpdate) "Updated" else "Created"} ${commitResult.path}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "Branch: ${commitResult.branch} • SHA: ${commitResult.commitSha}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                        if (commitResult.htmlUrl.isNotBlank()) {
                            IconButton(onClick = { onOpenCommitUrl(commitResult.htmlUrl) }) {
                                Icon(Icons.Outlined.OpenInNew, contentDescription = "Open commit on GitHub")
                            }
                        }
                    }
                }
            }

            val activeRepo = state.selectedRepo
            if (activeRepo == null) {
                // Repository List Screen
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    if (pendingPush != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ready to Push: ${pendingPush.suggestedFileName}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Text(
                                        text = "Pick a repository below to choose branch & commit preview.",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                TextButton(onClick = onClearPendingPush) {
                                    Text("Cancel Push")
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = repoSearch,
                        onValueChange = { repoSearch = it },
                        placeholder = { Text("Filter repositories…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = aetherTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("github_repo_search_input"),
                    )

                    val filteredRepos = remember(state.repos, repoSearch) {
                        val q = repoSearch.trim().lowercase()
                        if (q.isEmpty()) state.repos
                        else state.repos.filter {
                            it.fullName.lowercase().contains(q) || it.description.lowercase().contains(q)
                        }
                    }

                    if (state.isLoading && filteredRepos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredRepos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No repositories found. Tap refresh or check your GitHub PAT repo permissions.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items = filteredRepos, key = { it.id }) { repo ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = aetherCardBorder(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSelectRepo(repo) }
                                        .testTag("github_repo_row_${repo.name}"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (repo.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public,
                                                contentDescription = if (repo.isPrivate) "Private repo" else "Public repo",
                                                modifier = Modifier.size(15.dp),
                                                tint = if (repo.isPrivate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = repo.fullName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (repo.language.isNotBlank()) {
                                                Text(
                                                    text = repo.language,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = MaterialTheme.colorScheme.secondary,
                                                    ),
                                                )
                                            }
                                        }
                                        if (repo.description.isNotBlank()) {
                                            Text(
                                                text = repo.description,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = "Default branch: ${repo.defaultBranch}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Inside Selected Repository: Branch Selector + Commit Preview / Push Form + File Browser
                val activeBranch = state.selectedBranch.ifBlank { activeRepo.defaultBranch }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    // Branch Picker & Breadcrumb Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            FilterChip(
                                selected = true,
                                onClick = { branchMenuOpen = true },
                                colors = aetherFilterChipColors(),
                                border = aetherFilterChipBorder(enabled = true, selected = true),
                                label = {
                                    Text(
                                        text = "Branch: $activeBranch",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag("github_branch_picker_chip"),
                            )
                            DropdownMenu(
                                expanded = branchMenuOpen,
                                onDismissRequest = { branchMenuOpen = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                val branchList = state.branches.ifEmpty {
                                    listOf(GitHubBranchSummary(name = activeRepo.defaultBranch))
                                }
                                branchList.forEach { b ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (b.isProtected) "${b.name} (protected)" else b.name,
                                                fontFamily = JetBrainsMonoFontFamily,
                                            )
                                        },
                                        onClick = {
                                            branchMenuOpen = false
                                            onSelectBranch(b.name)
                                        },
                                    )
                                }
                            }
                        }

                        // Breadcrumb path
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { onNavigatePath("") }) {
                                Text("/", fontFamily = JetBrainsMonoFontFamily)
                            }
                            if (state.currentPath.isNotBlank()) {
                                val parts = state.currentPath.split('/')
                                parts.forEachIndexed { idx, part ->
                                    val subPath = parts.take(idx + 1).joinToString("/")
                                    Text("/")
                                    TextButton(onClick = { onNavigatePath(subPath) }) {
                                        Text(part, fontFamily = JetBrainsMonoFontFamily)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Commit Preview Confirmation Step
                    val preview = state.commitPreview
                    if (preview != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = aetherCardBorder(selected = true),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("github_commit_preview_card"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "Commit Preview",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (preview.isUpdate) {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            } else {
                                                LiveWebActiveColor.copy(alpha = 0.2f)
                                            },
                                        ) {
                                            Text(
                                                text = if (preview.isUpdate) "Update Existing" else "Create New File",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Repo: ${preview.owner}/${preview.repo}\nBranch: ${preview.branch}\nPath: ${preview.path}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                        ),
                                    )
                                    if (!preview.existingSha.isNullOrBlank()) {
                                        Text(
                                            text = "Auto-fetched file SHA: ${preview.existingSha.take(12)}…",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = MaterialTheme.colorScheme.primary,
                                            ),
                                        )
                                    }
                                    Text(
                                        text = "Commit message: \"${preview.commitMessage}\"",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    )
                                    Text(
                                        text = "${preview.lineCount} lines • ${preview.byteSize} bytes",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = preview.content.take(900) +
                                                if (preview.content.length > 900) "\n…(truncated preview)" else "",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                            ),
                                            modifier = Modifier.padding(10.dp),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = onClearCommitPreview,
                                            enabled = !state.isCommitting,
                                            colors = aetherSlateButtonColors(),
                                            border = aetherCardBorder(),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Edit")
                                        }
                                        Button(
                                            onClick = onConfirmCommit,
                                            enabled = !state.isCommitting,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("github_confirm_commit_button"),
                                        ) {
                                            if (state.isCommitting) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Push Commit")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Optional Push Code Form above the Folder/File Browser
                        if (pendingPush != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = aetherCardBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Push Code to Branch '$activeBranch'",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        )
                                        TextButton(onClick = onClearPendingPush) {
                                            Text("Cancel")
                                        }
                                    }
                                    OutlinedTextField(
                                        value = targetPathInput,
                                        onValueChange = { targetPathInput = it },
                                        label = { Text("Target file path in repo") },
                                        placeholder = { Text("e.g. src/main/App.kt") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = aetherTextFieldColors(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("github_commit_path_input"),
                                    )
                                    OutlinedTextField(
                                        value = commitMessageInput,
                                        onValueChange = { commitMessageInput = it },
                                        label = { Text("Commit message") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = aetherTextFieldColors(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("github_commit_message_input"),
                                    )
                                    Button(
                                        onClick = {
                                            onPrepareCommitPreview(activeBranch, targetPathInput, commitMessageInput)
                                        },
                                        enabled = targetPathInput.isNotBlank() && !state.isLoading,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("github_review_commit_button"),
                                    ) {
                                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Review Commit Preview (Auto-SHA)")
                                    }
                                }
                            }
                        }

                        // Folder & File Browser List
                        if (state.isLoading && state.contents.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (state.currentPath.isNotEmpty()) {
                                    item(key = "parent_dir") {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = aetherCardBorder(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    val parent = state.currentPath.substringBeforeLast('/', "")
                                                    onNavigatePath(parent)
                                                },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                )
                                                Text(".. (Up one folder)", fontFamily = JetBrainsMonoFontFamily)
                                            }
                                        }
                                    }
                                }

                                items(items = state.contents, key = { it.path }) { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = aetherCardBorder(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (entry.isDirectory) {
                                                    onNavigatePath(entry.path)
                                                } else if (pendingPush != null) {
                                                    targetPathInput = entry.path
                                                } else {
                                                    onAttachFileToChat(entry)
                                                }
                                            }
                                            .testTag("github_entry_${entry.name}"),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                                                contentDescription = if (entry.isDirectory) "Folder" else "File",
                                                tint = if (entry.isDirectory) {
                                                    MaterialTheme.colorScheme.tertiary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (!entry.isDirectory && entry.sizeBytes > 0L) {
                                                    Text(
                                                        text = if (entry.sizeBytes >= 1024L) "${entry.sizeBytes / 1024L} KB" else "${entry.sizeBytes} B",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        ),
                                                    )
                                                }
                                            }
                                            if (!entry.isDirectory) {
                                                if (pendingPush != null) {
                                                    TextButton(onClick = { targetPathInput = entry.path }) {
                                                        Text("Use Path")
                                                    }
                                                } else {
                                                    OutlinedButton(
                                                        onClick = { onAttachFileToChat(entry) },
                                                        enabled = !state.isAttachingFile,
                                                        colors = aetherSlateButtonColors(),
                                                        border = aetherCardBorder(),
                                                        modifier = Modifier.testTag("github_attach_file_${entry.name}"),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.AttachFile,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(15.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Attach")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
