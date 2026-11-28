package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.CreatorProfile
import com.example.data.model.ExportFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatorProfileCard(
    profile: CreatorProfile,
    onSaveProfile: (CreatorProfile) -> Unit,
    onLinkClick: (String) -> Unit,
    onEmailClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var displayName by remember(profile, isEditing) { mutableStateOf(profile.displayName) }
    var email by remember(profile, isEditing) { mutableStateOf(profile.email) }
    var githubRepoUrl by remember(profile, isEditing) { mutableStateOf(profile.githubRepoUrl) }
    var websiteUrl by remember(profile, isEditing) { mutableStateOf(profile.websiteUrl) }
    var customLinkLabel by remember(profile, isEditing) { mutableStateOf(profile.customLinkLabel) }
    var customLinkUrl by remember(profile, isEditing) { mutableStateOf(profile.customLinkUrl) }
    var bioNotes by remember(profile, isEditing) { mutableStateOf(profile.bioNotes) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = aetherCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .testTag("creator_profile_card"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Creator Profile & Project Links",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                if (!isEditing) {
                    TextButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.testTag("edit_creator_profile_button"),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (profile.hasAnyInfo) "Edit" else "Fill In")
                    }
                }
            }

            if (!isEditing) {
                if (!profile.hasAnyInfo) {
                    Text(
                        text = "Dedicated space for your name, email, GitHub repository, website, and project links. Tap \"Fill In\" whenever you're ready to customize it.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                } else {
                    if (profile.displayName.isNotBlank()) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    if (profile.bioNotes.isNotBlank()) {
                        Text(
                            text = profile.bioNotes,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (profile.githubRepoUrl.isNotBlank()) {
                            AssistChip(
                                onClick = { onLinkClick(normalizeUrl(profile.githubRepoUrl)) },
                                label = { Text("GitHub Repo") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("creator_github_chip"),
                            )
                        }
                        if (profile.email.isNotBlank()) {
                            AssistChip(
                                onClick = { onEmailClick(profile.email) },
                                label = { Text(profile.email) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("creator_email_chip"),
                            )
                        }
                        if (profile.websiteUrl.isNotBlank()) {
                            AssistChip(
                                onClick = { onLinkClick(normalizeUrl(profile.websiteUrl)) },
                                label = { Text("Website") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("creator_website_chip"),
                            )
                        }
                        if (profile.customLinkUrl.isNotBlank()) {
                            AssistChip(
                                onClick = { onLinkClick(normalizeUrl(profile.customLinkUrl)) },
                                label = { Text(profile.customLinkLabel.ifBlank { "Project Link" }) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("creator_custom_link_chip"),
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name / Handle") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("creator_name_input"),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("creator_email_input"),
                )
                OutlinedTextField(
                    value = githubRepoUrl,
                    onValueChange = { githubRepoUrl = it },
                    label = { Text("GitHub Repo URL") },
                    placeholder = { Text("https://github.com/username/repo") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("creator_github_input"),
                )
                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { websiteUrl = it },
                    label = { Text("Website / Portfolio URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = customLinkLabel,
                        onValueChange = { customLinkLabel = it },
                        label = { Text("Extra Link Label") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.42f),
                    )
                    OutlinedTextField(
                        value = customLinkUrl,
                        onValueChange = { customLinkUrl = it },
                        label = { Text("Extra Link URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.58f),
                    )
                }
                OutlinedTextField(
                    value = bioNotes,
                    onValueChange = { bioNotes = it },
                    label = { Text("Bio / About Notes") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { isEditing = false },
                        colors = aetherSlateButtonColors(),
                        border = aetherCardBorder(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSaveProfile(
                                CreatorProfile(
                                    displayName = displayName,
                                    email = email,
                                    githubRepoUrl = githubRepoUrl,
                                    websiteUrl = websiteUrl,
                                    customLinkLabel = customLinkLabel,
                                    customLinkUrl = customLinkUrl,
                                    bioNotes = bioNotes,
                                ),
                            )
                            isEditing = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_creator_profile_button"),
                    ) {
                        Text("Save Profile")
                    }
                }
            }
        }
    }
}

@Composable
fun ExportAndBackupCard(
    hasActiveConversation: Boolean,
    onExportChatSaf: (ExportFormat) -> Unit,
    onShareChatFile: (ExportFormat) -> Unit,
    onExportVaultSaf: (passphrase: String) -> Unit,
    onShareVaultFile: (passphrase: String) -> Unit,
    onImportBackupSaf: (passphrase: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var vaultPassphrase by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = aetherCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .testTag("export_backup_card"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Export, Google Drive & Vault Backup",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }

            Text(
                text = "Save directly to Google Drive, OneDrive, or device storage via Android Storage Picker, or share via Android Share Sheet.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            // Active Chat Export Row
            Text(
                text = "Active Conversation",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            listOf(
                ExportFormat.MARKDOWN,
                ExportFormat.PLAIN_TEXT,
                ExportFormat.JSON,
            ).forEach { format ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { onExportChatSaf(format) },
                        enabled = hasActiveConversation,
                        colors = aetherSlateButtonColors(),
                        border = aetherCardBorder(),
                        modifier = Modifier.testTag("export_saf_${format.extension}"),
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = { onShareChatFile(format) },
                        enabled = hasActiveConversation,
                        colors = aetherSlateButtonColors(),
                        border = aetherCardBorder(),
                        modifier = Modifier.testTag("share_file_${format.extension}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Encrypted Full Vault Backup & Restore
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Encrypted Full Vault (.orvault)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            OutlinedTextField(
                value = vaultPassphrase,
                onValueChange = { vaultPassphrase = it },
                label = { Text("Optional Passphrase (for cross-device restore)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = aetherTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_passphrase_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onExportVaultSaf(vaultPassphrase) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_vault_saf_button"),
                ) {
                    Text("Backup Vault")
                }
                OutlinedButton(
                    onClick = { onShareVaultFile(vaultPassphrase) },
                    colors = aetherSlateButtonColors(),
                    border = aetherCardBorder(),
                    modifier = Modifier.testTag("share_vault_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share vault backup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            OutlinedButton(
                onClick = { onImportBackupSaf(vaultPassphrase) },
                colors = aetherSlateButtonColors(),
                border = aetherCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_vault_saf_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import .orvault or .json Backup")
            }
        }
    }
}

private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}
