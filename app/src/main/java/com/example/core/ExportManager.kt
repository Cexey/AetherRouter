package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ExportFormat
import com.example.data.model.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ImportedVaultBundle(
    val folders: List<Folder>,
    val conversations: List<Conversation>,
    val messages: List<ChatMessage>,
)

/**
 * Handles SAF exports (Google Drive / OneDrive / local storage), FileProvider Share Sheet
 * exports (.md, .txt, .json), and AES-256-GCM encrypted .orvault full backup & restore.
 */
class ExportManager(
    private val context: Context,
    private val cryptoManager: CryptoManager,
) {
    companion object {
        private const val VAULT_MAGIC = "AETHER_ORVAULT_V1"
        private const val PBKDF2_ITERATIONS = 120_000
    }

    fun formatConversationAsMarkdown(
        conversation: Conversation,
        messages: List<ChatMessage>,
        use24HourClock: Boolean,
    ): String = buildString {
        append("# ${conversation.title}\n\n")
        append("- **Model:** `${conversation.modelId}`\n")
        append("- **Exported:** ${LiveClockProvider.formatMessagePromptStamp(System.currentTimeMillis(), use24HourClock)}\n\n")
        append("---\n\n")
        for (msg in messages) {
            if (msg.role == "tool") continue
            val roleHeading = if (msg.role == "user") "User" else "Assistant (${msg.modelUsed.ifBlank { conversation.modelId }})"
            val stamp = LiveClockProvider.formatMessagePromptStamp(msg.createdAt, use24HourClock)
            append("### $roleHeading — $stamp\n\n")
            if (msg.reasoningContent.isNotBlank()) {
                append("<details>\n<summary>Model Thinking</summary>\n\n")
                append(msg.reasoningContent.trim())
                append("\n\n</details>\n\n")
            }
            if (msg.attachments.isNotEmpty()) {
                append("_Attachments: ${msg.attachments.joinToString(", ") { it.displayName }}_\n\n")
            }
            append(msg.content.trim())
            append("\n\n")
            if (msg.citations.isNotEmpty()) {
                append("**Sources:**\n")
                msg.citations.forEach { c ->
                    append("- [${c.title}](${c.url})\n")
                }
                append("\n")
            }
        }
    }

    fun formatConversationAsPlainText(
        conversation: Conversation,
        messages: List<ChatMessage>,
        use24HourClock: Boolean,
    ): String = buildString {
        append("${conversation.title}\n")
        append("Model: ${conversation.modelId}\n")
        append("=".repeat(44)).append("\n\n")
        for (msg in messages) {
            if (msg.role == "tool") continue
            val who = if (msg.role == "user") "USER" else "ASSISTANT"
            val stamp = LiveClockProvider.formatMessagePromptStamp(msg.createdAt, use24HourClock)
            append("[$who • $stamp]\n")
            append(msg.content.trim()).append("\n\n")
        }
    }

    fun formatConversationAsJson(
        conversation: Conversation,
        messages: List<ChatMessage>,
    ): String {
        val root = JSONObject().apply {
            put("format", "aetherrouter_chat_v1")
            put("exportedAt", System.currentTimeMillis())
            put("conversation", conversationToJson(conversation))
            put("messages", JSONArray().apply { messages.forEach { put(messageToJson(it)) } })
        }
        return root.toString(2)
    }

    fun buildEncryptedVaultPayload(
        folders: List<Folder>,
        conversations: List<Conversation>,
        messages: List<ChatMessage>,
        passphrase: String = "",
    ): String {
        val innerJson = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("folders", JSONArray().apply { folders.forEach { put(folderToJson(it)) } })
            put("conversations", JSONArray().apply { conversations.forEach { put(conversationToJson(it)) } })
            put("messages", JSONArray().apply { messages.forEach { put(messageToJson(it)) } })
        }.toString()

        val envelope = JSONObject().apply {
            put("magic", VAULT_MAGIC)
            if (passphrase.isNotBlank()) {
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val key = deriveAesKey(passphrase, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                val cipherBytes = cipher.doFinal(innerJson.toByteArray(Charsets.UTF_8))
                put("mode", "passphrase_aes256_gcm")
                put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            } else {
                put("mode", "device_keystore_aes256_gcm")
                put("ciphertext", cryptoManager.encrypt(innerJson))
            }
        }
        return envelope.toString(2)
    }

    suspend fun writeTextToSafUri(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: throw IllegalStateException("Cannot write to selected destination")
        }
    }

    suspend fun createShareIntentForFile(
        fileName: String,
        mimeType: String,
        content: String,
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
            val file = File(exportDir, safeName)
            file.writeText(content, Charsets.UTF_8)
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, safeName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(share, "Share $safeName")
        }
    }

    suspend fun readAndParseImportUri(
        uri: Uri,
        passphrase: String = "",
    ): Result<ImportedVaultBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException("Unable to read backup file")

            val root = JSONObject(raw)
            val payloadJson = when {
                root.optString("magic") == VAULT_MAGIC -> {
                    when (root.optString("mode")) {
                        "passphrase_aes256_gcm" -> {
                            require(passphrase.isNotBlank()) { "Passphrase required to decrypt this .orvault backup" }
                            val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
                            val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
                            val cipherBytes = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
                            val key = deriveAesKey(passphrase, salt)
                            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                            JSONObject(String(cipher.doFinal(cipherBytes), Charsets.UTF_8))
                        }
                        "device_keystore_aes256_gcm" -> {
                            val decrypted = cryptoManager.decrypt(root.getString("ciphertext"))
                            require(decrypted.isNotBlank()) { "Could not decrypt device vault backup on this device" }
                            JSONObject(decrypted)
                        }
                        else -> throw IllegalArgumentException("Unsupported vault encryption mode")
                    }
                }
                root.optString("format") == "aetherrouter_chat_v1" -> {
                    JSONObject().apply {
                        put("folders", JSONArray())
                        put("conversations", JSONArray().put(root.getJSONObject("conversation")))
                        put("messages", root.getJSONArray("messages"))
                    }
                }
                else -> throw IllegalArgumentException("Unrecognized AetherRouter backup format")
            }

            val folders = mutableListOf<Folder>()
            val foldersArr = payloadJson.optJSONArray("folders")
            if (foldersArr != null) {
                for (i in 0 until foldersArr.length()) {
                    folders.add(jsonToFolder(foldersArr.getJSONObject(i)))
                }
            }

            val convs = mutableListOf<Conversation>()
            val convArr = payloadJson.optJSONArray("conversations")
            if (convArr != null) {
                for (i in 0 until convArr.length()) {
                    convs.add(jsonToConversation(convArr.getJSONObject(i)))
                }
            }

            val msgs = mutableListOf<ChatMessage>()
            val msgArr = payloadJson.optJSONArray("messages")
            if (msgArr != null) {
                for (i in 0 until msgArr.length()) {
                    msgs.add(jsonToMessage(msgArr.getJSONObject(i)))
                }
            }

            ImportedVaultBundle(folders = folders, conversations = convs, messages = msgs)
        }
    }

    fun suggestedFilename(baseTitle: String, format: ExportFormat): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val slug = baseTitle.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "aetherrouter_export" }
        return "${slug}_${stamp}.${format.extension}"
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun folderToJson(f: Folder) = JSONObject().apply {
        put("id", f.id)
        put("name", f.name)
        put("colorHex", f.colorHex)
        put("defaultModelId", f.defaultModelId)
        put("projectInstructions", f.projectInstructions)
        put("updatedAt", f.updatedAt)
    }

    private fun jsonToFolder(o: JSONObject) = Folder(
        id = o.getString("id"),
        name = o.getString("name"),
        colorHex = o.optString("colorHex", "#5B7CFA"),
        defaultModelId = o.optString("defaultModelId", ""),
        projectInstructions = o.optString("projectInstructions", ""),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun conversationToJson(c: Conversation) = JSONObject().apply {
        put("id", c.id)
        put("folderId", c.folderId ?: JSONObject.NULL)
        put("title", c.title)
        put("modelId", c.modelId)
        put("systemPromptOverride", c.systemPromptOverride)
        put("temperature", c.temperature.toDouble())
        put("topP", c.topP.toDouble())
        put("maxTokens", c.maxTokens)
        put("contextWindowLimit", c.contextWindowLimit)
        put("reasoningEffort", c.reasoningEffort.wireValue)
        put("webSearchEnabled", c.webSearchEnabled)
        put("webSearchMode", c.webSearchMode.name)
        put("webSearchEngine", c.webSearchEngine.wireValue)
        put("webMaxResults", c.webMaxResults)
        put("webMaxTotalResults", c.webMaxTotalResults)
        put("isPinned", c.isPinned)
        put("createdAt", c.createdAt)
        put("updatedAt", c.updatedAt)
    }

    private fun jsonToConversation(o: JSONObject) = Conversation(
        id = o.getString("id"),
        folderId = o.optString("folderId").takeIf { it.isNotBlank() && it != "null" },
        title = o.optString("title", "Imported Chat"),
        modelId = o.optString("modelId", "anthropic/claude-sonnet-4"),
        systemPromptOverride = o.optString("systemPromptOverride", ""),
        temperature = o.optDouble("temperature", 0.7).toFloat(),
        topP = o.optDouble("topP", 1.0).toFloat(),
        maxTokens = o.optInt("maxTokens", 4096),
        contextWindowLimit = o.optInt("contextWindowLimit", 128_000),
        reasoningEffort = com.example.data.model.ReasoningEffort.fromWire(o.optString("reasoningEffort")),
        webSearchEnabled = o.optBoolean("webSearchEnabled", true),
        webSearchMode = com.example.data.model.WebSearchMode.fromString(o.optString("webSearchMode")),
        webSearchEngine = com.example.data.model.WebSearchEngine.fromWire(o.optString("webSearchEngine")),
        webMaxResults = o.optInt("webMaxResults", 5),
        webMaxTotalResults = o.optInt("webMaxTotalResults", 15),
        isPinned = o.optBoolean("isPinned", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun messageToJson(m: ChatMessage) = JSONObject().apply {
        put("id", m.id)
        put("conversationId", m.conversationId)
        put("role", m.role)
        put("content", m.content)
        put("reasoningContent", m.reasoningContent)
        put("reasoningDetailsJson", m.reasoningDetailsJson)
        put("thinkingDurationMs", m.thinkingDurationMs)
        put("modelUsed", m.modelUsed)
        put("promptTokens", m.promptTokens)
        put("completionTokens", m.completionTokens)
        put("createdAt", m.createdAt)
    }

    private fun jsonToMessage(o: JSONObject) = ChatMessage(
        id = o.getString("id"),
        conversationId = o.getString("conversationId"),
        role = o.getString("role"),
        content = o.optString("content", ""),
        reasoningContent = o.optString("reasoningContent", ""),
        reasoningDetailsJson = o.optString("reasoningDetailsJson", ""),
        thinkingDurationMs = o.optLong("thinkingDurationMs", 0L),
        modelUsed = o.optString("modelUsed", ""),
        promptTokens = o.optInt("promptTokens", 0),
        completionTokens = o.optInt("completionTokens", 0),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )
}
