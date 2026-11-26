package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import com.example.data.model.CustomSticker
import com.example.ui.theme.LiveWebActiveColor
import java.io.File

private val EMOJI_CATEGORIES: List<Pair<String, List<String>>> = listOf(
    "Popular" to listOf(
        "😀", "😂", "🥹", "😍", "🤩", "😎", "🤔", "🤯",
        "🥳", "🔥", "⚡", "✨", "🚀", "💎", "🎯", "💯",
        "❤️", "💜", "💙", "👍", "👏", "🙌", "🤝", "🙏",
        "🎉", "🧠", "💡", "📌", "📝", "📚", "💻", "🌐",
    ),
    "Reactions" to listOf(
        "👍", "👎", "❤️", "🔥", "🚀", "🤯", "😂", "👏",
        "💯", "🎯", "⚡", "💡", "👀", "✅", "❌", "⚠️",
        "🙌", "🤝", "🫶", "🤩", "🤔", "😮", "😢", "🙏",
    ),
    "Work & Tech" to listOf(
        "💻", "📱", "⌨️", "🖥️", "🧪", "🔬", "📊", "📈",
        "📉", "🗂️", "📁", "📄", "📎", "🔗", "🔒", "🔑",
        "🛠️", "⚙️", "🧭", "🛰️", "🤖", "🧩", "🎨", "✍️",
    ),
)

val QUICK_REACTION_EMOJIS = listOf("👍", "❤️", "🔥", "🚀", "🤯", "😂", "💡")

@Composable
fun StagedAttachmentsStrip(
    attachments: List<ChatAttachment>,
    imageLoader: ImageLoader,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { att ->
            val hasThumb = att.previewImagePath.isNotBlank() && File(att.previewImagePath).exists()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .height(64.dp)
                    .widthIn(min = 124.dp, max = 220.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .testTag("staged_attachment_${att.kind.name.lowercase()}"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (hasThumb) {
                        AsyncImage(
                            model = File(att.previewImagePath),
                            imageLoader = imageLoader,
                            contentDescription = att.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Icon(
                                imageVector = when (att.kind) {
                                    AttachmentKind.PDF -> Icons.Outlined.PictureAsPdf
                                    AttachmentKind.WEBPAGE -> Icons.Outlined.Language
                                    else -> Icons.Outlined.Description
                                },
                                contentDescription = att.kind.name,
                                tint = if (att.kind == AttachmentKind.WEBPAGE) {
                                    LiveWebActiveColor
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = att.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detail = buildString {
                            append(att.kind.name)
                            val size = att.formattedSize()
                            if (size.isNotBlank()) append(" • $size")
                            val tok = att.estimatedTokens()
                            if (tok > 0) append(" • ~${tok}t")
                        }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(
                        onClick = { onRemove(att.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Remove attachment",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageAttachmentsGallery(
    attachments: List<ChatAttachment>,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val visualItems = attachments.filter {
            it.kind == AttachmentKind.IMAGE || it.kind == AttachmentKind.GIF || it.kind == AttachmentKind.STICKER
        }
        val docItems = attachments.filterNot { it in visualItems }

        if (visualItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visualItems.forEach { att ->
                    val path = att.previewImagePath.ifBlank { att.localFilePath }
                    val file = File(path)
                    if (path.isNotBlank() && file.exists()) {
                        val isSticker = att.kind == AttachmentKind.STICKER
                        Box(
                            modifier = Modifier
                                .size(if (isSticker) 118.dp else 164.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                        ) {
                            AsyncImage(
                                model = file,
                                imageLoader = imageLoader,
                                contentDescription = att.displayName,
                                contentScale = if (isSticker) ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (att.kind == AttachmentKind.GIF) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp),
                                ) {
                                    Text(
                                        text = "GIF",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (docItems.isNotEmpty()) {
            docItems.forEach { att ->
                val hasPdfThumb = att.kind == AttachmentKind.PDF &&
                    att.previewImagePath.isNotBlank() &&
                    File(att.previewImagePath).exists()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        if (hasPdfThumb) {
                            AsyncImage(
                                model = File(att.previewImagePath),
                                imageLoader = imageLoader,
                                contentDescription = att.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                        } else {
                            Icon(
                                imageVector = when (att.kind) {
                                    AttachmentKind.PDF -> Icons.Outlined.PictureAsPdf
                                    AttachmentKind.WEBPAGE -> Icons.Outlined.Language
                                    else -> Icons.Outlined.Description
                                },
                                contentDescription = att.kind.name,
                                tint = if (att.kind == AttachmentKind.WEBPAGE) {
                                    LiveWebActiveColor
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = att.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = buildString {
                                append(att.kind.name)
                                if (att.formattedSize().isNotBlank()) append(" • ${att.formattedSize()}")
                                if (att.estimatedTokens() > 0) append(" • ~${att.estimatedTokens()} tok")
                            }
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiStickerTray(
    customStickers: List<CustomSticker>,
    imageLoader: ImageLoader,
    onInsertEmoji: (String) -> Unit,
    onAttachSticker: (CustomSticker) -> Unit,
    onImportStickerClick: () -> Unit,
    onDeleteSticker: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var emojiCategoryIdx by remember { mutableIntStateOf(0) }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(268.dp)
            .testTag("emoji_sticker_tray"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Emojis") },
                        modifier = Modifier.testTag("tray_tab_emojis"),
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Stickers & GIFs (${customStickers.size})") },
                        modifier = Modifier.testTag("tray_tab_stickers"),
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close emoji tray")
                }
            }

            if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EMOJI_CATEGORIES.forEachIndexed { idx, (label, _) ->
                        FilterChip(
                            selected = emojiCategoryIdx == idx,
                            onClick = { emojiCategoryIdx = idx },
                            label = { Text(label) },
                        )
                    }
                }
                val emojis = EMOJI_CATEGORIES[emojiCategoryIdx].second
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(emojis) { emoji ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onInsertEmoji(emoji) },
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Tap a sticker/GIF to attach to your message",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    OutlinedButton(
                        onClick = onImportStickerClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("add_custom_sticker_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import")
                    }
                }

                if (customStickers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No saved stickers or GIFs yet. Tap Import to add PNG, WebP, or animated GIF packs from your gallery, or insert GIFs directly from Gboard / Samsung Keyboard.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 76.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(customStickers, key = { it.id }) { sticker ->
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { onAttachSticker(sticker) },
                            ) {
                                AsyncImage(
                                    model = File(sticker.localFilePath),
                                    imageLoader = imageLoader,
                                    contentDescription = sticker.label,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable { onDeleteSticker(sticker.id) },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Delete sticker",
                                            modifier = Modifier.size(12.dp),
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
}

@Composable
fun ReactionChipsRow(
    reactions: Map<String, Int>,
    onToggleReaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        reactions.forEach { (emoji, count) ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onToggleReaction(emoji) },
            ) {
                Text(
                    text = if (count > 1) "$emoji $count" else emoji,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun QuickReactionPickerRow(
    onPickEmoji: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            QUICK_REACTION_EMOJIS.forEach { emoji ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onPickEmoji(emoji) },
                ) {
                    Text(text = emoji, fontSize = 18.sp)
                }
            }
        }
    }
}
