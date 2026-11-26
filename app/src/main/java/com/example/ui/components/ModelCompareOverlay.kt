package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.data.model.ModelInfo
import com.example.viewmodel.CompareLaneState
import com.example.viewmodel.ModelCompareUiState

/**
 * Full-screen Side-by-Side Model Compare Arena.
 * Streams the exact same prompt to two OpenRouter models concurrently and lets the user
 * commit the winning reply (saving the alternate reply as a swipe variant in the active chat).
 */
@Composable
fun ModelCompareOverlay(
    compareState: ModelCompareUiState,
    primaryModelId: String,
    allModels: List<ModelInfo>,
    onClose: () -> Unit,
    onSelectModelB: (String) -> Unit,
    onRunCompare: (String) -> Unit,
    onStopCompare: () -> Unit,
    onCommitWinner: (chooseModelA: Boolean) -> Unit,
    onLinkClick: (String) -> Unit,
    onCopyText: (String) -> Unit,
) {
    var promptDraft by remember { mutableStateOf(compareState.prompt) }
    var modelBMenuOpen by remember { mutableStateOf(false) }

    BackHandler {
        if (compareState.isRunning) onStopCompare()
        onClose()
    }

    val modelAName = remember(primaryModelId, allModels) {
        allModels.firstOrNull { it.id == primaryModelId }?.name ?: primaryModelId.substringAfter('/')
    }
    val effectiveModelBId = compareState.modelBId.ifBlank {
        allModels.firstOrNull { it.id != primaryModelId }?.id ?: "openai/gpt-4.1"
    }
    val modelBName = remember(effectiveModelBId, allModels) {
        allModels.firstOrNull { it.id == effectiveModelBId }?.name ?: effectiveModelBId.substringAfter('/')
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (compareState.isRunning) onStopCompare()
                        onClose()
                    },
                    modifier = Modifier.testTag("compare_back_button"),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close Model Compare")
                }
                Icon(
                    imageVector = Icons.Outlined.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Side-by-Side Model Compare",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Winner commits to chat; runner-up is saved as swipe #2",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            // Model A vs Model B Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = "MODEL A (ACTIVE)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = modelAName,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { modelBMenuOpen = true }
                        .testTag("compare_model_b_selector"),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = "MODEL B (TAP TO PICK)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = modelBName,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Pick comparison model",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = modelBMenuOpen,
                        onDismissRequest = { modelBMenuOpen = false },
                    ) {
                        allModels.take(30).forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${model.company} • ${model.formattedPricing()}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                },
                                onClick = {
                                    modelBMenuOpen = false
                                    onSelectModelB(model.id)
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            // Responsive Adaptive Compare Panes (Side-by-Side on wide, stacked split on compact)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                val sideBySide = maxWidth >= 600.dp
                if (sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CompareLaneCard(
                            label = "Model A • $modelAName",
                            lane = compareState.laneA,
                            canKeep = !compareState.isRunning && compareState.laneA.content.isNotBlank(),
                            onKeep = { onCommitWinner(true) },
                            onLinkClick = onLinkClick,
                            onCopyText = onCopyText,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        CompareLaneCard(
                            label = "Model B • $modelBName",
                            lane = compareState.laneB,
                            canKeep = !compareState.isRunning && compareState.laneB.content.isNotBlank(),
                            onKeep = { onCommitWinner(false) },
                            onLinkClick = onLinkClick,
                            onCopyText = onCopyText,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CompareLaneCard(
                            label = "Model A • $modelAName",
                            lane = compareState.laneA,
                            canKeep = !compareState.isRunning && compareState.laneA.content.isNotBlank(),
                            onKeep = { onCommitWinner(true) },
                            onLinkClick = onLinkClick,
                            onCopyText = onCopyText,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                        CompareLaneCard(
                            label = "Model B • $modelBName",
                            lane = compareState.laneB,
                            canKeep = !compareState.isRunning && compareState.laneB.content.isNotBlank(),
                            onKeep = { onCommitWinner(false) },
                            onLinkClick = onLinkClick,
                            onCopyText = onCopyText,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }
            }

            // Prompt Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = promptDraft,
                    onValueChange = { promptDraft = it },
                    placeholder = { Text("Enter a prompt to run on both models simultaneously…") },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("compare_prompt_input"),
                )
                Button(
                    enabled = compareState.isRunning || promptDraft.isNotBlank(),
                    onClick = {
                        if (compareState.isRunning) {
                            onStopCompare()
                        } else {
                            onRunCompare(promptDraft.trim())
                        }
                    },
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("compare_run_button"),
                ) {
                    Icon(
                        imageVector = if (compareState.isRunning) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                        contentDescription = if (compareState.isRunning) "Stop compare" else "Run compare",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareLaneCard(
    label: String,
    lane: CompareLaneState,
    canKeep: Boolean,
    onKeep: () -> Unit,
    onLinkClick: (String) -> Unit,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(16.dp),
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (lane.isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (lane.elapsedMs > 0L) {
                    Text(
                        text = String.format("%.1fs", lane.elapsedMs / 1000.0),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                if (lane.completionTokens > 0) {
                    Text(
                        text = "• ${lane.completionTokens} tok",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (!lane.error.isNullOrBlank()) {
                    Text(
                        text = lane.error,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                    )
                } else if (lane.content.isBlank() && !lane.isStreaming) {
                    Text(
                        text = "Ready to compare. Type a prompt below and press Send.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                } else {
                    MarkdownContent(
                        markdown = lane.content.ifBlank { "Thinking…" },
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onLinkClick = onLinkClick,
                        onCopyCode = onCopyText,
                    )
                }
            }

            if (canKeep) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onKeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compare_keep_winner_button"),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Keep This Reply in Chat")
                }
            }
        }
    }
}
