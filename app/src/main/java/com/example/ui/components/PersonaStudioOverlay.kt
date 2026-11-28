package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.core.ContextAndCostEstimator
import com.example.data.model.GlobalLorebook
import com.example.data.model.LorebookEntry
import com.example.data.model.PersonaCard
import java.io.File
import java.util.UUID

/**
 * Full-screen Persona & SillyTavern V2 Studio overlay.
 * Supports authoring custom personas, importing SillyTavern V1/V2 JSON / PNG cards & .txt/.md prompts,
 * managing Lorebook keyword entries, selecting Alternate Greetings, and {{char}}/{{user}} macro preview.
 */
@Composable
fun PersonaStudioOverlay(
    personas: List<PersonaCard>,
    activePersonaId: String?,
    globalLorebooks: List<GlobalLorebook> = emptyList(),
    enabledLorebookIds: List<String> = emptyList(),
    creatorDisplayName: String,
    imageLoader: ImageLoader,
    onClose: () -> Unit,
    onImportPersonaClick: () -> Unit,
    onSavePersona: (PersonaCard) -> Unit,
    onDeletePersona: (String) -> Unit,
    onApplyPersona: (PersonaCard, String?) -> Unit,
    onDetachPersona: () -> Unit,
    onExportPersonaV2Saf: (PersonaCard) -> Unit = {},
    onSharePersonaV2: (PersonaCard) -> Unit = {},
    onSaveGlobalLorebook: (GlobalLorebook) -> Unit = {},
    onDeleteGlobalLorebook: (String) -> Unit = {},
    onToggleGlobalLorebook: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Personas, 1 = Shared Lorebooks
    var editingPersona by remember { mutableStateOf<PersonaCard?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var editingLorebook by remember { mutableStateOf<GlobalLorebook?>(null) }
    var isCreatingLorebook by remember { mutableStateOf(false) }
    var greetingPickerPersona by remember { mutableStateOf<PersonaCard?>(null) }

    BackHandler {
        when {
            editingPersona != null || isCreatingNew -> {
                editingPersona = null
                isCreatingNew = false
            }
            editingLorebook != null || isCreatingLorebook -> {
                editingLorebook = null
                isCreatingLorebook = false
            }
            else -> onClose()
        }
    }

    val effectiveUser = creatorDisplayName.trim().ifBlank { "User" }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (editingPersona != null || isCreatingNew) {
            PersonaEditorView(
                initial = editingPersona,
                effectiveUser = effectiveUser,
                onBack = {
                    editingPersona = null
                    isCreatingNew = false
                },
                onSave = { saved ->
                    onSavePersona(saved)
                    editingPersona = null
                    isCreatingNew = false
                },
            )
        } else if (editingLorebook != null || isCreatingLorebook) {
            GlobalLorebookEditorView(
                initial = editingLorebook,
                onBack = {
                    editingLorebook = null
                    isCreatingLorebook = false
                },
                onSave = { saved ->
                    onSaveGlobalLorebook(saved)
                    editingLorebook = null
                    isCreatingLorebook = false
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.testTag("persona_studio_back_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close Persona Studio")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Persona & SillyTavern Studio",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "{{user}} resolves to \"$effectiveUser\" • ${personas.size} saved",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (selectedTab == 0) isCreatingNew = true else isCreatingLorebook = true
                                },
                                modifier = Modifier.testTag("persona_create_new_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "Create new",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Mode Tabs: Personas vs Shared Lorebooks
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                colors = aetherFilterChipColors(),
                                border = aetherFilterChipBorder(enabled = true, selected = selectedTab == 0),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Face,
                                        contentDescription = null,
                                        tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("Personas (${personas.size})") },
                                modifier = Modifier.testTag("tab_personas"),
                            )
                            FilterChip(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                colors = aetherFilterChipColors(),
                                border = aetherFilterChipBorder(enabled = true, selected = selectedTab == 1),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.MenuBook,
                                        contentDescription = null,
                                        tint = if (selectedTab == 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = {
                                    val activeCount = enabledLorebookIds.size
                                    Text(
                                        if (activeCount > 0) "Shared Lorebooks ($activeCount on)"
                                        else "Shared Lorebooks (${globalLorebooks.size})",
                                    )
                                },
                                modifier = Modifier.testTag("tab_shared_lorebooks"),
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                if (selectedTab == 1) {
                    // Standalone Global Shared Lorebooks Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Button(
                            onClick = { isCreatingLorebook = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_shared_lorebook_button"),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Shared Lorebook")
                        }
                    }
                    Text(
                        text = "Reusable worldbooks injected automatically by keyword across any persona or standalone chat.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (globalLorebooks.isEmpty()) {
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
                                    imageVector = Icons.Outlined.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = "No shared lorebooks yet",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = "Create reusable world info, coding standards, or project glossaries and toggle them on per chat.",
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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(items = globalLorebooks, key = { it.id }) { book ->
                                val isActiveForChat = book.id in enabledLorebookIds
                                GlobalLorebookRow(
                                    book = book,
                                    isActiveForChat = isActiveForChat,
                                    onToggleActive = { onToggleGlobalLorebook(book.id) },
                                    onEdit = { editingLorebook = book },
                                    onDelete = { onDeleteGlobalLorebook(book.id) },
                                )
                            }
                        }
                    }
                } else {
                    // Action Row: Import SillyTavern Card + New Custom Prompt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onImportPersonaClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("persona_import_card_button"),
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Card / File")
                        }
                        OutlinedButton(
                            onClick = { isCreatingNew = true },
                            colors = aetherSlateButtonColors(),
                            border = aetherCardBorder(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("persona_author_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Persona")
                        }
                    }

                    Text(
                        text = "Supports SillyTavern V1/V2 JSON, PNG cards, .txt/.md prompts, and full V2 JSON export.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )

                    if (activePersonaId != null) {
                        val activeName = personas.firstOrNull { it.id == activePersonaId }?.name ?: "Active Persona"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = aetherCardBorder(selected = true),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Active in chat: $activeName",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = onDetachPersona,
                                    modifier = Modifier.testTag("persona_detach_button"),
                                ) {
                                    Text("Detach")
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    if (personas.isEmpty()) {
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
                                    imageVector = Icons.Outlined.Face,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "No personas in your vault yet",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = "Import a SillyTavern V2 JSON/PNG card or author a custom system persona with Lorebook entries.",
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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(items = personas, key = { it.id }) { card ->
                                val isActive = card.id == activePersonaId
                                PersonaCardRow(
                                    card = card,
                                    isActive = isActive,
                                    imageLoader = imageLoader,
                                    onApply = {
                                        val greetings = buildList {
                                            if (card.firstMessage.isNotBlank()) add(card.firstMessage)
                                            addAll(card.alternateGreetings.filter { it.isNotBlank() })
                                        }
                                        if (greetings.isNotEmpty()) {
                                            greetingPickerPersona = card
                                        } else {
                                            onApplyPersona(card, null)
                                            onClose()
                                        }
                                    },
                                    onEdit = { editingPersona = card },
                                    onExportSaf = { onExportPersonaV2Saf(card) },
                                    onShareV2 = { onSharePersonaV2(card) },
                                    onDelete = { onDeletePersona(card.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pickerCard = greetingPickerPersona
    if (pickerCard != null) {
        GreetingChooserDialog(
            persona = pickerCard,
            effectiveUser = effectiveUser,
            onDismiss = { greetingPickerPersona = null },
            onSelectGreeting = { chosenGreeting ->
                greetingPickerPersona = null
                onApplyPersona(pickerCard, chosenGreeting)
                onClose()
            },
        )
    }
}

@Composable
private fun PersonaCardRow(
    card: PersonaCard,
    isActive: Boolean,
    imageLoader: ImageLoader,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onExportSaf: () -> Unit,
    onShareV2: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatLabel = when (card.cardFormat) {
        "chara_card_v2" -> "ST V2"
        "chara_card_v1" -> "ST V1"
        "text_prompt" -> "Prompt"
        else -> "Custom"
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = aetherCardBorder(selected = isActive),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (card.avatarPath.isNotBlank() && File(card.avatarPath).exists()) {
                    AsyncImage(
                        model = File(card.avatarPath),
                        imageLoader = imageLoader,
                        contentDescription = "${card.name} avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = card.name.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = formatLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    val preview = card.description.ifBlank { card.systemPrompt }.ifBlank { card.personality }
                    if (preview.isNotBlank()) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (card.lorebookEntries.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${card.lorebookEntries.size} lorebook",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (card.alternateGreetings.isNotEmpty()) {
                    Text(
                        text = "• ${card.alternateGreetings.size} alt greetings",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onExportSaf, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = "Save ${card.name} as V2 JSON", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShareV2, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share ${card.name} V2 JSON", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit ${card.name}", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete ${card.name}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Button(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("persona_apply_button"),
                ) {
                    Text(if (isActive) "Re-Apply" else "Use")
                }
            }
        }
    }
}

@Composable
private fun GreetingChooserDialog(
    persona: PersonaCard,
    effectiveUser: String,
    onDismiss: () -> Unit,
    onSelectGreeting: (String?) -> Unit,
) {
    val allGreetings = remember(persona) {
        buildList {
            if (persona.firstMessage.isNotBlank()) add("Default Greeting" to persona.firstMessage)
            persona.alternateGreetings.forEachIndexed { idx, text ->
                if (text.isNotBlank()) add("Alternate Greeting #${idx + 1}" to text)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Activate ${persona.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Choose an opening greeting ({{user}} → $effectiveUser) or apply silently without inserting a greeting.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                allGreetings.forEach { (label, text) ->
                    val preview = text
                        .replace(Regex("""\{\{\s*char\s*\}\}""", RegexOption.IGNORE_CASE), persona.name)
                        .replace(Regex("""\{\{\s*user\s*\}\}""", RegexOption.IGNORE_CASE), effectiveUser)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = aetherCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectGreeting(text) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelectGreeting(null) }) {
                Text("Apply Without Greeting")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PersonaEditorView(
    initial: PersonaCard?,
    effectiveUser: String,
    onBack: () -> Unit,
    onSave: (PersonaCard) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var systemPrompt by remember(initial) { mutableStateOf(initial?.systemPrompt.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var personality by remember(initial) { mutableStateOf(initial?.personality.orEmpty()) }
    var scenario by remember(initial) { mutableStateOf(initial?.scenario.orEmpty()) }
    var firstMessage by remember(initial) { mutableStateOf(initial?.firstMessage.orEmpty()) }
    var postHistory by remember(initial) { mutableStateOf(initial?.postHistoryInstructions.orEmpty()) }
    var altGreetingsText by remember(initial) {
        mutableStateOf(initial?.alternateGreetings?.joinToString("\n---\n").orEmpty())
    }
    var loreEntries by remember(initial) { mutableStateOf(initial?.lorebookEntries ?: emptyList()) }
    var newLoreKeys by remember { mutableStateOf("") }
    var newLoreContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to personas")
                    }
                    Text(
                        text = if (initial == null) "Author Persona" else "Edit ${initial.name}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val altList = altGreetingsText
                                .split("\n---\n")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSave(
                                PersonaCard(
                                    id = initial?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    description = description.trim(),
                                    personality = personality.trim(),
                                    scenario = scenario.trim(),
                                    firstMessage = firstMessage.trim(),
                                    alternateGreetings = altList,
                                    systemPrompt = systemPrompt.trim(),
                                    postHistoryInstructions = postHistory.trim(),
                                    creatorNotes = initial?.creatorNotes.orEmpty(),
                                    tags = initial?.tags ?: emptyList(),
                                    lorebookEntries = loreEntries,
                                    avatarPath = initial?.avatarPath.orEmpty(),
                                    cardFormat = initial?.cardFormat ?: "custom",
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        },
                        modifier = Modifier.testTag("persona_editor_save_button"),
                    ) {
                        Text("Save")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        val loreChars = remember(loreEntries) { loreEntries.sumOf { it.content.length } }
        val (totalChars, estTokens) = remember(
            systemPrompt,
            description,
            personality,
            scenario,
            postHistory,
            loreChars,
        ) {
            ContextAndCostEstimator.estimatePersonaTokens(
                systemPrompt = systemPrompt,
                description = description,
                personality = personality,
                scenario = scenario,
                postHistory = postHistory,
                lorebookTotalChars = loreChars,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = aetherCardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Prompt Footprint",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "$totalChars chars • ~$estTokens tokens",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.testTag("persona_token_counter"),
                    )
                }
            }
            Text(
                text = "Macros supported: {{char}}, {{user}} (currently \"$effectiveUser\"), {{time}}, {{date}}",
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Persona Name *") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("persona_name_input"),
            )
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt") },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Character Description") },
                minLines = 2,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                label = { Text("Personality") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = scenario,
                onValueChange = { scenario = it },
                label = { Text("Scenario / Setting") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = firstMessage,
                onValueChange = { firstMessage = it },
                label = { Text("First Message (Greeting)") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = altGreetingsText,
                onValueChange = { altGreetingsText = it },
                label = { Text("Alternate Greetings (separate with --- on a new line)") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = postHistory,
                onValueChange = { postHistory = it },
                label = { Text("Post-History Instructions (Depth-0 Steering)") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Lorebook / World Info Entries (${loreEntries.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            loreEntries.forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = aetherCardBorder(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keys: ${entry.keys.joinToString(", ")}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Text(
                                text = entry.content,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { loreEntries = loreEntries.filterNot { it.id == entry.id } },
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove lorebook entry",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = newLoreKeys,
                onValueChange = { newLoreKeys = it },
                label = { Text("Trigger Keywords (comma-separated)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newLoreContent,
                onValueChange = { newLoreContent = it },
                label = { Text("Lorebook Fact to Inject When Matched") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = newLoreKeys.isNotBlank() && newLoreContent.isNotBlank(),
                onClick = {
                    val keys = newLoreKeys.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    if (keys.isNotEmpty()) {
                        loreEntries = loreEntries + LorebookEntry(
                            id = UUID.randomUUID().toString(),
                            keys = keys,
                            content = newLoreContent.trim(),
                            enabled = true,
                            insertionOrder = loreEntries.size * 10 + 10,
                        )
                        newLoreKeys = ""
                        newLoreContent = ""
                    }
                },
                colors = aetherSlateButtonColors(),
                border = aetherCardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Lorebook Entry")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GlobalLorebookRow(
    book: GlobalLorebook,
    isActiveForChat: Boolean,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val totalTokens = remember(book.entries) {
        ContextAndCostEstimator.estimateTokens(book.entries.joinToString("\n") { it.content })
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActiveForChat) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = aetherCardBorder(
            selected = isActiveForChat,
            accent = MaterialTheme.colorScheme.tertiary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.description.isNotBlank()) {
                        Text(
                            text = book.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = isActiveForChat,
                    onCheckedChange = { onToggleActive() },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${book.entries.size} entries • ~$totalTokens tok",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit lorebook", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete lorebook",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalLorebookEditorView(
    initial: GlobalLorebook?,
    onBack: () -> Unit,
    onSave: (GlobalLorebook) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var entries by remember(initial) { mutableStateOf(initial?.entries ?: emptyList()) }
    var newKeys by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to lorebooks")
                    }
                    Text(
                        text = if (initial == null) "New Shared Lorebook" else "Edit ${initial.name}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            onSave(
                                GlobalLorebook(
                                    id = initial?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    description = description.trim(),
                                    entries = entries,
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        },
                        modifier = Modifier.testTag("save_shared_lorebook_button"),
                    ) {
                        Text("Save")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Lorebook Name *") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Keyword Entries (${entries.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            entries.forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = aetherCardBorder(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keys: ${entry.keys.joinToString(", ")}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Text(
                                text = entry.content,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { entries = entries.filterNot { it.id == entry.id } },
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove entry",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = newKeys,
                onValueChange = { newKeys = it },
                label = { Text("Trigger Keywords (comma-separated)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newContent,
                onValueChange = { newContent = it },
                label = { Text("World Fact / Glossary Entry to Inject") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = newKeys.isNotBlank() && newContent.isNotBlank(),
                onClick = {
                    val keys = newKeys.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    if (keys.isNotEmpty()) {
                        entries = entries + LorebookEntry(
                            id = UUID.randomUUID().toString(),
                            keys = keys,
                            content = newContent.trim(),
                            enabled = true,
                            insertionOrder = entries.size * 10 + 10,
                        )
                        newKeys = ""
                        newContent = ""
                    }
                },
                colors = aetherSlateButtonColors(),
                border = aetherCardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Entry")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
