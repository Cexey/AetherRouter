package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ContextCostMetrics
import com.example.data.model.SlashPromptTemplate
import com.example.ui.theme.LiveWebActiveColor

/**
 * Compact Live Context Window Meter, Estimated USD Cost, and one-tap Context Compression trigger.
 */
@Composable
fun ContextAndCostBar(
    metrics: ContextCostMetrics,
    isCompressing: Boolean,
    canCompress: Boolean,
    onCompressClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = when {
        metrics.usageFraction >= 0.90f -> MaterialTheme.colorScheme.error
        metrics.usageFraction >= 0.72f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = "Context window usage",
                    tint = barColor,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "Ctx ${metrics.formattedTokens()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.testTag("context_meter_label"),
                )
                if (metrics.estimatedChatCostUsd > 0.0) {
                    Text(
                        text = "• ${metrics.formattedCost()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.testTag("chat_cost_label"),
                    )
                }
                if (metrics.compressedMessagesCount > 0) {
                    Text(
                        text = "• ${metrics.compressedMessagesCount} archived",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.secondary,
                        ),
                    )
                }
            }

            if (canCompress || isCompressing) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = aetherCardBorder(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(enabled = !isCompressing, onClick = onCompressClick)
                        .testTag("compress_context_button"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        if (isCompressing) {
                            CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Compress,
                                contentDescription = "Compress older turns",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        Text(
                            text = if (isCompressing) "Compressing…" else "Compress",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { metrics.usageFraction.coerceIn(0.01f, 1f) },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp)),
        )
    }
}

/**
 * Collapsible Rolling Context Compression Memory banner shown at the top of compressed chats.
 */
@Composable
fun CompressedContextBanner(
    summary: String,
    compressedCount: Int,
    onClearSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = aetherCardBorder(selected = true, accent = MaterialTheme.colorScheme.tertiary),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .testTag("compressed_summary_banner"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Compress,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Compressed Memory ($compressedCount earlier turns summarized)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Toggle memory summary",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClearSummary) {
                        Text("Restore Full History to Context")
                    }
                }
            }
        }
    }
}

sealed class SlashCommandEntry(
    val command: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    data object ToggleWeb : SlashCommandEntry("/web", "Toggle Live Web", "Switch Live Web search on/off", Icons.Outlined.Language)
    data object CompareModels : SlashCommandEntry("/compare", "Compare 2 Models", "Stream one prompt to two models side-by-side", Icons.Outlined.CompareArrows)
    data object OpenPersonas : SlashCommandEntry("/persona", "Persona & Lorebooks", "Open SillyTavern Persona & Shared Lorebook Studio", Icons.Outlined.Face)
    data object CompressContext : SlashCommandEntry("/compress", "Compress Context", "Summarize earlier turns to free up context window", Icons.Outlined.Compress)
    data object NewChat : SlashCommandEntry("/new", "New Conversation", "Start a clean conversation thread", Icons.Outlined.Add)
    data object ClearChat : SlashCommandEntry("/clear", "Clear Messages", "Clear messages in this conversation", Icons.Outlined.DeleteOutline)
    data class CustomTemplate(val template: SlashPromptTemplate) : SlashCommandEntry(
        command = "/${template.command}",
        title = template.title,
        subtitle = template.promptBody.replace('\n', ' ').take(60),
        icon = Icons.Outlined.AutoAwesome,
    )
}

/**
 * Floating Slash Command Palette shown above the composer when the user types `/...`.
 */
@Composable
fun SlashCommandPalette(
    query: String,
    customTemplates: List<SlashPromptTemplate>,
    onSelectCommand: (SlashCommandEntry) -> Unit,
    onManageTemplatesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val needle = query.trim().lowercase()
    val allEntries = remember(customTemplates) {
        listOf(
            SlashCommandEntry.ToggleWeb,
            SlashCommandEntry.CompareModels,
            SlashCommandEntry.OpenPersonas,
            SlashCommandEntry.CompressContext,
            SlashCommandEntry.NewChat,
            SlashCommandEntry.ClearChat,
        ) + customTemplates.map { SlashCommandEntry.CustomTemplate(it) }
    }
    val filtered = remember(needle, allEntries) {
        if (needle == "/") allEntries
        else allEntries.filter {
            it.command.lowercase().contains(needle) || it.title.lowercase().contains(needle.removePrefix("/"))
        }
    }

    if (filtered.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = aetherCardBorder(),
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("slash_command_palette"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Quick Slash Commands & Templates",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "+ Add Template",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onManageTemplatesClick)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            filtered.forEach { entry ->
                val entryTint = when (entry) {
                    SlashCommandEntry.ToggleWeb -> LiveWebActiveColor
                    SlashCommandEntry.CompareModels,
                    SlashCommandEntry.OpenPersonas,
                    is SlashCommandEntry.CustomTemplate -> MaterialTheme.colorScheme.tertiary
                    SlashCommandEntry.ClearChat -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCommand(entry) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = entryTint,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = entry.command,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = entryTint,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SlashTemplatesManagerDialog(
    templates: List<SlashPromptTemplate>,
    onDismiss: () -> Unit,
    onSaveTemplate: (command: String, title: String, body: String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
) {
    var commandInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Custom /Slash Prompt Templates") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
                    label = { Text("Command (without /)") },
                    placeholder = { Text("e.g. refactor") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = aetherTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Label") },
                    placeholder = { Text("e.g. Refactor Cleanly") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = aetherTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bodyInput,
                    onValueChange = { bodyInput = it },
                    label = { Text("Prompt Snippet") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    colors = aetherTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = commandInput.isNotBlank() && bodyInput.isNotBlank(),
                    onClick = {
                        onSaveTemplate(
                            commandInput.trim().lowercase(),
                            titleInput.trim().ifBlank { commandInput.trim() },
                            bodyInput.trim(),
                        )
                        commandInput = ""
                        titleInput = ""
                        bodyInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save /${commandInput.ifBlank { "template" }}")
                }

                if (templates.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    templates.forEach { tpl ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = aetherCardBorder(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "/${tpl.command} — ${tpl.title}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    )
                                    Text(
                                        text = tpl.promptBody,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { onDeleteTemplate(tpl.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete template",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
