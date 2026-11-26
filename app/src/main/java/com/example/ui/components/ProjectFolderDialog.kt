package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.data.model.Folder
import com.example.ui.theme.contrastOnColor
import com.example.ui.theme.parseHexColorOrNull

private val PROJECT_COLORS = listOf(
    "#5B7CFA",
    "#20C9B7",
    "#9B6DFF",
    "#F46A8B",
    "#F5A623",
    "#22C55E",
    "#38BDF8",
    "#EF4444",
)

@Composable
fun ProjectFolderDialog(
    existingFolder: Folder?,
    onDismiss: () -> Unit,
    onSave: (id: String?, name: String, colorHex: String, defaultModelId: String, instructions: String) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    var name by remember(existingFolder) { mutableStateOf(existingFolder?.name.orEmpty()) }
    var colorHex by remember(existingFolder) { mutableStateOf(existingFolder?.colorHex ?: PROJECT_COLORS.first()) }
    var defaultModelId by remember(existingFolder) { mutableStateOf(existingFolder?.defaultModelId.orEmpty()) }
    var instructions by remember(existingFolder) { mutableStateOf(existingFolder?.projectInstructions.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingFolder == null) "New Project Folder" else "Edit Project Folder",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g., Android Architecture, Research") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_name_input"),
                )

                Text(
                    text = "Color Tag",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PROJECT_COLORS.forEach { hex ->
                        val color = parseHexColorOrNull(hex) ?: MaterialTheme.colorScheme.primary
                        val selected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable { colorHex = hex },
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Selected color",
                                    tint = contrastOnColor(color),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = defaultModelId,
                    onValueChange = { defaultModelId = it },
                    label = { Text("Default OpenRouter Model ID (Optional)") },
                    placeholder = { Text("e.g., anthropic/claude-sonnet-4") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Project Instructions (Auto-prepended)") },
                    placeholder = { Text("Shared context or guidelines for every chat in this folder…") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_instructions_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(existingFolder?.id, name, colorHex, defaultModelId, instructions)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_project_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (existingFolder != null && onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete(existingFolder.id)
                            onDismiss()
                        },
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
fun MoveToFolderDialog(
    folders: List<Folder>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onSelectFolder: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Chat to Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (currentFolderId == null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelectFolder(null)
                            onDismiss()
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Unfiled (All Chats)", style = MaterialTheme.typography.titleSmall)
                    }
                }

                folders.forEach { folder ->
                    val dotColor = parseHexColorOrNull(folder.colorHex) ?: MaterialTheme.colorScheme.primary
                    val selected = currentFolderId == folder.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectFolder(folder.id)
                                onDismiss()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(folder.name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
fun AttachWebpageUrlDialog(
    onDismiss: () -> Unit,
    onAttachUrl: (String) -> Unit,
) {
    var urlInput by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attach Live Webpage") },
        text = {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Public HTTPS URL") },
                placeholder = { Text("https://example.com/article") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attach_webpage_url_input"),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = urlInput.trim()
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        onAttachUrl(trimmed)
                        onDismiss()
                    }
                },
                enabled = urlInput.trim().length > 10,
                modifier = Modifier.testTag("confirm_attach_webpage_button"),
            ) {
                Text("Fetch & Attach")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
