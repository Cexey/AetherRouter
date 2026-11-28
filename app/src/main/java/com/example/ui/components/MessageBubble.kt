package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.core.ContextCostMetrics
import com.example.core.LiveClockProvider
import com.example.data.model.ChatMessage
import com.example.data.model.UrlCitation
import com.example.ui.theme.LiveWebActiveColor
import com.example.ui.theme.LocalAetherStyle
import com.example.ui.theme.ReasoningAccentColor
import com.example.ui.theme.contrastOnColor
import java.net.URI

@Composable
fun DayDividerHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = aetherCardBorder(),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    imageLoader: ImageLoader,
    use24HourClock: Boolean,
    showTimestamps: Boolean,
    estimatedCostUsd: Double = 0.0,
    isSpeakingThisMessage: Boolean = false,
    onToggleSpeak: () -> Unit = {},
    onSwipePrevious: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
    onForkHere: () -> Unit = {},
    onLinkClick: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onSaveCodeFile: ((language: String, code: String) -> Unit)? = null,
    onRunLivePreview: ((language: String, code: String) -> Unit)? = null,
    onPushCodeToGitHub: ((language: String, code: String) -> Unit)? = null,
    onShareText: (String) -> Unit,
    onToggleReaction: (String) -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"
    val timestampLabel = LiveClockProvider.formatMessageTimestamp(message.createdAt, use24HourClock)
    val style = LocalAetherStyle.current
    val radius = style.bubbleCornerRadiusDp
    val bubblePadV = if (style.compactDensity) 8.dp else 12.dp
    val bubblePadH = if (style.compactDensity) 12.dp else 14.dp
    var showReactionPicker by remember { mutableStateOf(false) }

    if (isTool) {
        ToolResultChipCard(content = message.content, modifier = modifier)
        return
    }

    if (isUser) {
        val userBg = style.userBubbleColorOverride ?: MaterialTheme.colorScheme.primaryContainer
        val userFg = if (style.userBubbleColorOverride != null) {
            contrastOnColor(style.userBubbleColorOverride)
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = radius,
                    topEnd = radius,
                    bottomStart = radius,
                    bottomEnd = (radius.value * 0.35f).coerceAtLeast(4f).dp,
                ),
                color = userBg,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
                tonalElevation = 0.dp,
                modifier = Modifier.widthIn(max = 330.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = bubblePadH, vertical = bubblePadV),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (message.attachments.isNotEmpty()) {
                        MessageAttachmentsGallery(
                            attachments = message.attachments,
                            imageLoader = imageLoader,
                        )
                    }
                    if (message.content.isNotBlank()) {
                        RichInlineText(
                            raw = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(color = userFg),
                            onLinkClick = onLinkClick,
                        )
                    }
                }
            }
            if (message.reactions.isNotEmpty()) {
                ReactionChipsRow(
                    reactions = message.reactions,
                    onToggleReaction = onToggleReaction,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (showReactionPicker) {
                QuickReactionPickerRow(
                    onPickEmoji = { emoji ->
                        onToggleReaction(emoji)
                        showReactionPicker = false
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            ) {
                if (showTimestamps) {
                    Text(
                        text = timestampLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                IconButton(
                    onClick = { showReactionPicker = !showReactionPicker },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("user_react_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentSatisfied,
                        contentDescription = "Add emoji reaction",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onCopyText(message.content) },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("user_copy_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy message",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onForkHere,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("user_fork_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CallSplit,
                        contentDescription = "Fork conversation from this message",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("user_edit_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit message",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("user_delete_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete message",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        val assistantBg = style.assistantSurfaceOverride ?: MaterialTheme.colorScheme.surfaceContainerLow
        val assistantFg = if (style.assistantSurfaceOverride != null) {
            contrastOnColor(style.assistantSurfaceOverride)
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val hasReasoning = message.reasoningContent.isNotBlank() || message.reasoningDetailsJson.isNotBlank()
        val hasEncryptedSig = message.reasoningDetailsJson.contains("signature") ||
            message.reasoningDetailsJson.contains("reasoning.encrypted")

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = if (style.compactDensity) 2.dp else 4.dp),
        ) {
            // Model header + timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = message.modelUsed.substringAfter('/').ifBlank { "Assistant" },
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showTimestamps) {
                    Text(
                        text = "• $timestampLabel",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                if (message.completionTokens > 0) {
                    Text(
                        text = "• ${message.completionTokens} tok",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                if (estimatedCostUsd > 0.0) {
                    Text(
                        text = "• ${ContextCostMetrics.formatUsd(estimatedCostUsd)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }

            // Collapsible Model Thinking Accordion
            if (hasReasoning) {
                ThinkingAccordionCard(
                    reasoningText = message.reasoningContent,
                    durationMs = message.thinkingDurationMs,
                    hasEncryptedSignature = hasEncryptedSig,
                    isStreaming = false,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Editorial Markdown Body
            Surface(
                shape = RoundedCornerShape(radius),
                color = assistantBg,
                border = aetherCardBorder(),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = bubblePadH, vertical = bubblePadV)) {
                    if (message.attachments.isNotEmpty()) {
                        MessageAttachmentsGallery(
                            attachments = message.attachments,
                            imageLoader = imageLoader,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    MarkdownContent(
                        markdown = message.content,
                        textColor = assistantFg,
                        onLinkClick = onLinkClick,
                        onCopyCode = { onCopyText(it) },
                        onSaveCodeFile = onSaveCodeFile,
                        onRunLivePreview = onRunLivePreview,
                        onPushCodeToGitHub = onPushCodeToGitHub,
                    )

                    if (message.citations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LiveSourcesBar(
                            citations = message.citations,
                            onLinkClick = onLinkClick,
                        )
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                ReactionChipsRow(
                    reactions = message.reactions,
                    onToggleReaction = onToggleReaction,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (showReactionPicker) {
                QuickReactionPickerRow(
                    onPickEmoji = { emoji ->
                        onToggleReaction(emoji)
                        showReactionPicker = false
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Response Action Bar (with swipeable < 1 / N > variants & Fork Chat)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
            ) {
                if (message.swipes.size > 1) {
                    val idx = message.activeSwipeIndex.coerceIn(0, message.swipes.lastIndex)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = aetherCardBorder(selected = true, accent = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("assistant_swipe_pill"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        ) {
                            IconButton(
                                onClick = onSwipePrevious,
                                enabled = idx > 0,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("swipe_prev_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChevronLeft,
                                    contentDescription = "Previous generated version",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = "${idx + 1}/${message.swipes.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            IconButton(
                                onClick = onSwipeNext,
                                enabled = idx < message.swipes.lastIndex,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("swipe_next_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = "Next generated version",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { showReactionPicker = !showReactionPicker },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("assistant_react_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentSatisfied,
                        contentDescription = "Add emoji reaction",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onToggleSpeak,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("assistant_tts_button"),
                ) {
                    Icon(
                        imageVector = if (isSpeakingThisMessage) Icons.Outlined.StopCircle else Icons.Outlined.VolumeUp,
                        contentDescription = if (isSpeakingThisMessage) "Stop reading aloud" else "Read message aloud",
                        modifier = Modifier.size(18.dp),
                        tint = if (isSpeakingThisMessage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = onRegenerate,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("assistant_regenerate_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Regenerate new swipe variant",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onForkHere,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("assistant_fork_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CallSplit,
                        contentDescription = "Fork chat from here",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onCopyText(message.content) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("assistant_copy_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy response",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onShareText(message.content) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("assistant_share_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share response",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("assistant_edit_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit response",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("assistant_delete_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete response",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun StreamingAssistantBubble(
    modelId: String,
    statusNote: String,
    activeToolName: String = "",
    content: String,
    reasoningText: String,
    hasEncryptedSignature: Boolean,
    thinkingElapsedMs: Long,
    citations: List<UrlCitation>,
    onLinkClick: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onSaveCodeFile: ((language: String, code: String) -> Unit)? = null,
    onRunLivePreview: ((language: String, code: String) -> Unit)? = null,
    onPushCodeToGitHub: ((language: String, code: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = modelId.substringAfter('/').ifBlank { "Generating" },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            if (statusNote.isNotBlank()) {
                Text(
                    text = "• $statusNote",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        if (activeToolName.isNotBlank()) {
            ActiveToolExecutionCard(
                toolName = activeToolName,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (reasoningText.isNotBlank() || hasEncryptedSignature) {
            ThinkingAccordionCard(
                reasoningText = reasoningText,
                durationMs = thinkingElapsedMs,
                hasEncryptedSignature = hasEncryptedSignature,
                isStreaming = content.isEmpty(),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (content.isNotBlank() || citations.isNotEmpty()) {
            val style = LocalAetherStyle.current
            val assistantBg = style.assistantSurfaceOverride ?: MaterialTheme.colorScheme.surfaceContainerLow
            val assistantFg = if (style.assistantSurfaceOverride != null) {
                contrastOnColor(style.assistantSurfaceOverride)
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Surface(
                shape = RoundedCornerShape(style.bubbleCornerRadiusDp),
                color = assistantBg,
                border = aetherCardBorder(selected = true),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (content.isNotBlank()) {
                        MarkdownContent(
                            markdown = content,
                            textColor = assistantFg,
                            onLinkClick = onLinkClick,
                            onCopyCode = onCopyCode,
                            onSaveCodeFile = onSaveCodeFile,
                            onRunLivePreview = onRunLivePreview,
                            onPushCodeToGitHub = onPushCodeToGitHub,
                        )
                    }
                    if (citations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LiveSourcesBar(citations = citations, onLinkClick = onLinkClick)
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingAccordionCard(
    reasoningText: String,
    durationMs: Long,
    hasEncryptedSignature: Boolean,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val secondsLabel = if (durationMs > 0L) String.format("%.1fs", durationMs / 1000.0) else ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
            .border(1.dp, ReasoningAccentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("thinking_accordion_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = "Model thinking",
                tint = ReasoningAccentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (isStreaming) "Thinking…" else "Model Thinking",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            if (secondsLabel.isNotBlank()) {
                Text(
                    text = secondsLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
            if (hasEncryptedSignature) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Encrypted signature preserved",
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Verified",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse thinking" else "Expand thinking",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = reasoningText.ifBlank {
                        "Encrypted reasoning tokens preserved in vault for lossless multi-turn signature replay."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
fun LiveSourcesBar(
    citations: List<UrlCitation>,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = "Live web sources",
                tint = LiveWebActiveColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "Live Sources (${citations.size})",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = LiveWebActiveColor,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            citations.forEachIndexed { idx, citation ->
                val domain = runCatching {
                    URI(citation.url).host?.removePrefix("www.") ?: citation.url
                }.getOrDefault(citation.url)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLinkClick(citation.url) }
                        .testTag("live_citation_chip_$idx"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = citation.title.ifBlank { domain },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = "Open source",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveToolExecutionCard(
    toolName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .testTag("active_tool_execution_card"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Build,
                    contentDescription = "Running tool",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Executing Tool: $toolName",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ToolResultChipCard(
    content: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val (headerTitle, bodyText) = remember(content) {
        val firstLine = content.lineSequence().firstOrNull().orEmpty()
        if (firstLine.startsWith("[Tool:") && firstLine.endsWith("]")) {
            val label = firstLine.removePrefix("[Tool:").removeSuffix("]").trim()
            val rest = content.substringAfter('\n', "").trim()
            label to rest
        } else {
            "Live Web Tool Output" to content
        }
    }
    val isWebTool = headerTitle.contains("web", ignoreCase = true) || headerTitle.contains("fetch", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .testTag("tool_result_card"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWebTool) Icons.Outlined.Language else Icons.Outlined.Build,
                    contentDescription = "Tool execution output",
                    tint = if (isWebTool) LiveWebActiveColor else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Toggle tool output",
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = bodyText.take(1500),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
