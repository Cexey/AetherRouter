package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAetherStyle

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class BulletItem(val marker: String, val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onLinkClick: (String) -> Unit = {},
    onCopyCode: (String) -> Unit = {},
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val clipboard = LocalClipboardManager.current
    val styleConfig = LocalAetherStyle.current

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(styleConfig.paragraphSpacingDp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        RichInlineText(
                            raw = block.text,
                            style = style.copy(color = textColor, fontWeight = FontWeight.Bold),
                            onLinkClick = onLinkClick,
                        )
                    }
                    is MarkdownBlock.CodeBlock -> {
                        CodeBlockCard(
                            language = block.language,
                            code = block.code,
                            onCopy = {
                                clipboard.setText(AnnotatedString(block.code))
                                onCopyCode(block.code)
                            },
                        )
                    }
                    is MarkdownBlock.BlockQuote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            RichInlineText(
                                raw = block.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic,
                                ),
                                onLinkClick = onLinkClick,
                            )
                        }
                    }
                    is MarkdownBlock.BulletItem -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = block.marker,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            RichInlineText(
                                raw = block.text,
                                style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                                onLinkClick = onLinkClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    is MarkdownBlock.Table -> {
                        MarkdownTableCard(
                            headers = block.headers,
                            rows = block.rows,
                            onLinkClick = onLinkClick,
                        )
                    }
                    is MarkdownBlock.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    is MarkdownBlock.Paragraph -> {
                        RichInlineText(
                            raw = block.text,
                            style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                            onLinkClick = onLinkClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

@Composable
private fun MarkdownTableCard(
    headers: List<String>,
    rows: List<List<String>>,
    onLinkClick: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .horizontalScroll(scrollState),
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 8.dp),
        ) {
            headers.forEach { header ->
                RichInlineText(
                    raw = header,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    onLinkClick = onLinkClick,
                    modifier = Modifier
                        .width(140.dp)
                        .padding(horizontal = 10.dp),
                )
            }
        }
        rows.forEachIndexed { idx, row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                modifier = Modifier
                    .background(
                        if (idx % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surface,
                    )
                    .padding(vertical = 8.dp),
            ) {
                headers.indices.forEach { colIdx ->
                    val cell = row.getOrNull(colIdx).orEmpty()
                    RichInlineText(
                        raw = cell,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        onLinkClick = onLinkClick,
                        modifier = Modifier
                            .width(140.dp)
                            .padding(horizontal = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RichInlineText(
    raw: String,
    style: TextStyle,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeFg = MaterialTheme.colorScheme.tertiary

    val annotated = remember(raw, linkColor, codeBg, codeFg) {
        buildInlineAnnotatedString(
            raw = raw,
            linkColor = linkColor,
            codeBg = codeBg,
            codeFg = codeFg,
            onLinkClick = onLinkClick,
        )
    }
    Text(text = annotated, style = style, modifier = modifier)
}

private val INLINE_TOKEN_REGEX = Regex(
    """\[([^\]]+)]\((https?://[^)\s]+)\)|(https?://[^\s<>()]+)|`([^`]+)`|\*\*([^*]+)\*\*|~~([^~]+)~~|\*([^*]+)\*""",
)

private fun buildInlineAnnotatedString(
    raw: String,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color,
    onLinkClick: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_TOKEN_REGEX.findAll(raw)) {
        if (match.range.first > cursor) {
            append(raw.substring(cursor, match.range.first))
        }
        val mdLinkLabel = match.groups[1]?.value
        val mdLinkUrl = match.groups[2]?.value
        val bareUrl = match.groups[3]?.value
        val inlineCode = match.groups[4]?.value
        val boldText = match.groups[5]?.value
        val strikeText = match.groups[6]?.value
        val italicText = match.groups[7]?.value

        when {
            mdLinkLabel != null && mdLinkUrl != null -> {
                val cleanUrl = mdLinkUrl.trimEnd('.', ',', ';', '!', '?')
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "md_url",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        linkInteractionListener = { onLinkClick(cleanUrl) },
                    ),
                ) {
                    append(mdLinkLabel)
                }
            }
            bareUrl != null -> {
                val cleanUrl = bareUrl.trimEnd('.', ',', ';', '!', '?', ')')
                val trailing = bareUrl.removePrefix(cleanUrl)
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "bare_url",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        linkInteractionListener = { onLinkClick(cleanUrl) },
                    ),
                ) {
                    append(cleanUrl)
                }
                if (trailing.isNotEmpty()) append(trailing)
            }
            inlineCode != null -> {
                withStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        background = codeBg,
                        color = codeFg,
                    ),
                ) {
                    append(" $inlineCode ")
                }
            }
            boldText != null -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldText)
                }
            }
            strikeText != null -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(strikeText)
                }
            }
            italicText != null -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(italicText)
                }
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        append(raw.substring(cursor))
    }
}

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    if (input.isBlank()) return emptyList()
    val lines = input.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n")))
            paragraphBuffer.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Fenced code block
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(language = lang, code = codeLines.joinToString("\n")))
            i++
            continue
        }

        // Blank line
        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        // Horizontal divider
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Markdown table
        if (trimmed.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            flushParagraph()
            val headers = splitTableRow(trimmed)
            i += 2
            val tableRows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().contains("|")) {
                tableRows.add(splitTableRow(lines[i]))
                i++
            }
            blocks.add(MarkdownBlock.Table(headers = headers, rows = tableRows))
            continue
        }

        // Heading H1..H4
        val headingMatch = Regex("^(#{1,4})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            val level = headingMatch.groupValues[1].length
            blocks.add(MarkdownBlock.Heading(level = level, text = headingMatch.groupValues[2]))
            i++
            continue
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MarkdownBlock.BlockQuote(quoteLines.joinToString("\n")))
            continue
        }

        // Unordered list
        val unordered = Regex("^[-*+]\\s+(.+)$").find(trimmed)
        if (unordered != null) {
            flushParagraph()
            blocks.add(MarkdownBlock.BulletItem("•", unordered.groupValues[1]))
            i++
            continue
        }

        // Ordered list
        val ordered = Regex("^(\\d+\\.)\\s+(.+)$").find(trimmed)
        if (ordered != null) {
            flushParagraph()
            blocks.add(MarkdownBlock.BulletItem(ordered.groupValues[1], ordered.groupValues[2]))
            i++
            continue
        }

        paragraphBuffer.add(line)
        i++
    }
    flushParagraph()
    return blocks
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim().trim('|')
    if (!trimmed.contains("-")) return false
    return trimmed.split("|").all { cell -> cell.trim().matches(Regex(":?-{2,}:?")) }
}

private fun splitTableRow(line: String): List<String> =
    line.trim().trim('|').split("|").map { it.trim() }
