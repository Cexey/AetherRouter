package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.McpServerConfig
import com.example.ui.theme.LiveWebActiveColor

/**
 * Full-screen MCP (Model Context Protocol) Tools Hub.
 * Manages remote HTTP / Streamable-HTTP JSON-RPC 2.0 MCP servers with AES-256 encrypted auth headers,
 * live `tools/list` discovery, and per-chat activation switches.
 */
@Composable
fun McpToolsHubOverlay(
    servers: List<McpServerConfig>,
    enabledServerIdsForChat: List<String>,
    syncingServerId: String?,
    onClose: () -> Unit,
    onSaveServer: (id: String?, name: String, url: String, headerName: String, headerValue: String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onSyncTools: (McpServerConfig) -> Unit,
    onToggleForActiveChat: (String) -> Unit,
) {
    var editingServer by remember { mutableStateOf<Pair<Boolean, McpServerConfig?>>(false to null) }

    BackHandler(onBack = onClose)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("mcp_hub_back_button"),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close MCP Tools Hub")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MCP Tools Hub",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "${enabledServerIdsForChat.size} active in current chat • HTTP JSON-RPC 2.0",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Button(
                    onClick = { editingServer = true to null },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("mcp_add_server_button"),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Server")
                }
            }

            Text(
                text = "Connect remote HTTPS MCP tool servers. Auth headers are encrypted with AES-256-GCM on device.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "No MCP servers connected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "Add a remote MCP endpoint to discover tools and invoke them automatically during chats.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = servers, key = { it.id }) { server ->
                        val activeInChat = server.id in enabledServerIdsForChat
                        val isSyncing = syncingServerId == server.id
                        McpServerCard(
                            server = server,
                            activeInChat = activeInChat,
                            isSyncing = isSyncing,
                            onToggleForChat = { onToggleForActiveChat(server.id) },
                            onSync = { onSyncTools(server) },
                            onEdit = { editingServer = true to server },
                            onDelete = { onDeleteServer(server.id) },
                        )
                    }
                }
            }
        }
    }

    if (editingServer.first) {
        McpServerFormDialog(
            existing = editingServer.second,
            onDismiss = { editingServer = false to null },
            onSave = { id, name, url, headerName, headerVal ->
                onSaveServer(id, name, url, headerName, headerVal)
                editingServer = false to null
            },
        )
    }
}

@Composable
private fun McpServerCard(
    server: McpServerConfig,
    activeInChat: Boolean,
    isSyncing: Boolean,
    onToggleForChat: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expandedTools by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (activeInChat) 1.5.dp else 1.dp,
                color = if (activeInChat) LiveWebActiveColor else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (server.authHeaderValue.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Encrypted auth header configured",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = server.endpointUrl,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (activeInChat) "Active" else "Off",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (activeInChat) LiveWebActiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Switch(
                        checked = activeInChat,
                        onCheckedChange = { onToggleForChat() },
                        modifier = Modifier.testTag("mcp_chat_toggle_${server.id}"),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSync,
                    enabled = !isSyncing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("mcp_sync_tools_button"),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSyncing) "Syncing…" else "Sync Tools (${server.tools.size})")
                }
                if (server.tools.isNotEmpty()) {
                    TextButton(onClick = { expandedTools = !expandedTools }) {
                        Text(if (expandedTools) "Hide Tools" else "Inspect")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit ${server.name}", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete ${server.name}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (expandedTools && server.tools.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    server.tools.forEach { tool ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp),
                            )
                            Column {
                                Text(
                                    text = tool.name,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        text = tool.description,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerFormDialog(
    existing: McpServerConfig?,
    onDismiss: () -> Unit,
    onSave: (id: String?, name: String, url: String, headerName: String, headerValue: String) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var endpointUrl by remember(existing) { mutableStateOf(existing?.endpointUrl.orEmpty()) }
    var headerName by remember(existing) { mutableStateOf(existing?.authHeaderName?.ifBlank { "Authorization" } ?: "Authorization") }
    var headerValue by remember(existing) { mutableStateOf(existing?.authHeaderValue.orEmpty()) }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Connect MCP Server" else "Edit MCP Server") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Label *") },
                    placeholder = { Text("e.g. GitHub MCP, Docs Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_name_input"),
                )
                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text("HTTPS Endpoint URL *") },
                    placeholder = { Text("https://mcp.example.com/rpc") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_url_input"),
                )
                OutlinedTextField(
                    value = headerName,
                    onValueChange = { headerName = it },
                    label = { Text("Auth Header Name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = headerValue,
                    onValueChange = { headerValue = it },
                    label = { Text("Auth Header Value (encrypted)") },
                    placeholder = { Text("Bearer token…") },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showSecret) "Hide secret" else "Show secret",
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && endpointUrl.isNotBlank(),
                onClick = { onSave(existing?.id, name, endpointUrl, headerName, headerValue) },
                modifier = Modifier.testTag("mcp_save_server_button"),
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
