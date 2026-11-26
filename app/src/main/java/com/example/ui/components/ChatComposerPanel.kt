package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.data.model.ChatAttachment
import com.example.data.model.CustomSticker
import com.example.ui.theme.LiveWebActiveColor
import kotlinx.coroutines.flow.distinctUntilChanged

private val COMPOSER_URL_REGEX = Regex("""https?://[^\s<>()]+""")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatComposerPanel(
    text: String,
    stagedAttachments: List<ChatAttachment>,
    customStickers: List<CustomSticker>,
    smartReplies: List<String>,
    imageLoader: ImageLoader,
    isStreaming: Boolean,
    isProcessingAttachment: Boolean,
    isFetchingWebpage: Boolean,
    hasApiKey: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSelectSmartReply: (String) -> Unit,
    onOpenVoiceMode: () -> Unit,
    onOpenKeySetup: () -> Unit,
    onAttachUrl: (String) -> Unit,
    onPickPhotosClick: () -> Unit,
    onPickDocumentsClick: () -> Unit,
    onOpenAttachUrlDialog: () -> Unit,
    onRemoveStagedAttachment: (String) -> Unit,
    onRichKeyboardMedia: (List<Uri>) -> Unit,
    onAttachSticker: (CustomSticker) -> Unit,
    onImportStickerClick: () -> Unit,
    onDeleteSticker: (String) -> Unit,
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    var showEmojiTray by remember { mutableStateOf(false) }
    val detectedUrl = remember(text) { COMPOSER_URL_REGEX.find(text)?.value }
    val canSend = text.isNotBlank() || stagedAttachments.isNotEmpty()

    // Compose 1.7 BasicTextField(TextFieldState) with native Modifier.contentReceiver
    // for direct Gboard & Samsung Keyboard GIF / Sticker insertion.
    val textFieldState = rememberTextFieldState(initialText = text)

    LaunchedEffect(text) {
        if (text != textFieldState.text.toString()) {
            textFieldState.setTextAndPlaceCursorAtEnd(text)
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { latest ->
                if (latest != text) {
                    onTextChange(latest)
                }
            }
    }

    val receiveContentListener = remember {
        ReceiveContentListener { transferableContent ->
            if (!transferableContent.hasMediaType(MediaType.Image)) {
                return@ReceiveContentListener transferableContent
            }
            val uris = mutableListOf<Uri>()
            val remaining = transferableContent.consume { item ->
                val u = item.uri
                if (u != null) {
                    uris.add(u)
                    true
                } else {
                    false
                }
            }
            if (uris.isNotEmpty()) {
                onRichKeyboardMedia(uris)
            }
            remaining
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!hasApiKey) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenKeySetup),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Tap to add your OpenRouter API key (AES-256 encrypted on device)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                            )
                        }
                    }
                }

                if (smartReplies.isNotEmpty() && !isStreaming && hasApiKey) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        smartReplies.forEachIndexed { idx, suggestion ->
                            SuggestionChip(
                                onClick = { onSelectSmartReply(suggestion) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        text = suggestion,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                modifier = Modifier.testTag("smart_reply_chip_$idx"),
                            )
                        }
                    }
                }

                if (stagedAttachments.isNotEmpty()) {
                    StagedAttachmentsStrip(
                        attachments = stagedAttachments,
                        imageLoader = imageLoader,
                        onRemove = onRemoveStagedAttachment,
                    )
                }

                if (isProcessingAttachment) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Processing attachment…",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }

                if (detectedUrl != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LiveWebActiveColor.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isFetchingWebpage) { onAttachUrl(detectedUrl) }
                            .testTag("attach_detected_url_chip"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = "Attach live webpage",
                                tint = LiveWebActiveColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = if (isFetchingWebpage) {
                                    "Reading live webpage…"
                                } else {
                                    "Tap to attach live page: $detectedUrl"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = LiveWebActiveColor,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Attachment Menu Trigger
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("composer_attach_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = "Attach photos, GIFs, documents, or webpage",
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Photos, GIFs & Stickers") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                                },
                                onClick = {
                                    showAttachMenu = false
                                    onPickPhotosClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Files & Documents (PDF, DOCX, Code…)") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Description, contentDescription = null)
                                },
                                onClick = {
                                    showAttachMenu = false
                                    onPickDocumentsClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Attach Live Webpage URL") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Language, contentDescription = null)
                                },
                                onClick = {
                                    showAttachMenu = false
                                    onOpenAttachUrlDialog()
                                },
                            )
                        }
                    }

                    // Rich-Content Enabled Input Pill
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(22.dp),
                            ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            BasicTextField(
                                state = textFieldState,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 6),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp)
                                    .contentReceiver(receiveContentListener)
                                    .testTag("chat_composer_input"),
                                decorator = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (textFieldState.text.isEmpty()) {
                                            Text(
                                                text = "Message, paste GIF, or attach files…",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )

                            IconButton(
                                onClick = { showEmojiTray = !showEmojiTray },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("composer_emoji_tray_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EmojiEmotions,
                                    contentDescription = "Open emoji and sticker tray",
                                    tint = if (showEmojiTray) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }

                            IconButton(
                                onClick = {
                                    showEmojiTray = false
                                    onOpenVoiceMode()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("composer_voice_mode_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = "Open on-device voice mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // Send / Stop FAB
                    Surface(
                        shape = CircleShape,
                        color = if (isStreaming) {
                            MaterialTheme.colorScheme.error
                        } else if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = isStreaming || canSend,
                                onClick = {
                                    if (isStreaming) {
                                        onStop()
                                    } else {
                                        showEmojiTray = false
                                        onSend()
                                    }
                                },
                            )
                            .testTag(if (isStreaming) "stop_stream_button" else "send_message_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isStreaming) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                                contentDescription = if (isStreaming) "Stop streaming" else "Send message",
                                tint = if (isStreaming) {
                                    MaterialTheme.colorScheme.onError
                                } else if (canSend) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            if (showEmojiTray) {
                EmojiStickerTray(
                    customStickers = customStickers,
                    imageLoader = imageLoader,
                    onInsertEmoji = { emoji ->
                        val next = textFieldState.text.toString() + emoji
                        textFieldState.setTextAndPlaceCursorAtEnd(next)
                        onTextChange(next)
                    },
                    onAttachSticker = { sticker ->
                        onAttachSticker(sticker)
                    },
                    onImportStickerClick = onImportStickerClick,
                    onDeleteSticker = onDeleteSticker,
                    onClose = { showEmojiTray = false },
                )
            }
        }
    }
}
