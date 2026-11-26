package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.Composable
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
import com.example.data.model.Conversation
import com.example.data.model.Folder
import com.example.ui.theme.parseHexColorOrNull

@Composable
fun ChatDrawerBody(
    folders: List<Folder>,
    selectedFolderFilterId: String?,
    conversations: List<Conversation>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectFolderFilter: (String?) -> Unit,
    onCreateFolderClick: () -> Unit,
    onEditFolderClick: (Folder) -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onMoveConversationClick: (Conversation) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenPersonaStudio: () -> Unit,
    onOpenMcpHub: () -> Unit,
    onOpenAppearanceStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folderById = remember(folders) { folders.associateBy { it.id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "AetherRouter",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Row {
                IconButton(
                    onClick = onCreateFolderClick,
                    modifier = Modifier.testTag("drawer_new_project_button"),
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New project folder")
                }
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier.testTag("drawer_new_chat_button"),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Start new conversation")
                }
            }
        }

        // Encrypted Full-Text Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search encrypted chats…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_encrypted_search_input"),
        )

        // Projects / Folders Horizontal Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedFolderFilterId == null,
                onClick = { onSelectFolderFilter(null) },
                label = { Text("All Chats") },
                modifier = Modifier.testTag("folder_filter_all"),
            )
            folders.forEach { folder ->
                val dotColor = parseHexColorOrNull(folder.colorHex) ?: MaterialTheme.colorScheme.primary
                FilterChip(
                    selected = selectedFolderFilterId == folder.id,
                    onClick = { onSelectFolderFilter(folder.id) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit project ${folder.name}",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onEditFolderClick(folder) },
                        )
                    },
                    label = {
                        Text(
                            text = folder.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        "No encrypted chats match \"$searchQuery\"."
                    } else if (selectedFolderFilterId != null) {
                        "No chats in this project yet. Tap + to start one."
                    } else {
                        "No conversations yet."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items = conversations, key = { it.id }) { conv ->
                    val selected = conv.id == activeConversationId
                    val folder = conv.folderId?.let { folderById[it] }
                    var menuOpen by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectConversation(conv.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (conv.isPinned) {
                                        Icon(
                                            imageVector = Icons.Outlined.PushPin,
                                            contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                    if (folder != null) {
                                        val dotColor = parseHexColorOrNull(folder.colorHex)
                                            ?: MaterialTheme.colorScheme.secondary
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(dotColor),
                                        )
                                    }
                                    Text(
                                        text = conv.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = buildString {
                                        append(conv.modelId.substringAfter('/'))
                                        if (folder != null) append(" • ${folder.name}")
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { menuOpen = true },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = "Conversation actions",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (conv.isPinned) "Unpin chat" else "Pin chat") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onTogglePinConversation(conv.id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move to project") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onMoveConversationClick(conv)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onDeleteConversation(conv.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenPersonaStudio,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("drawer_personas_button"),
                ) {
                    Icon(Icons.Outlined.Face, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Personas")
                }
                OutlinedButton(
                    onClick = onOpenMcpHub,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("drawer_mcp_hub_button"),
                ) {
                    Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MCP Tools")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenAppearanceStudio,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("drawer_appearance_button"),
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Theme")
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("drawer_settings_button"),
                ) {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Settings")
                }
            }
        }
    }
}
