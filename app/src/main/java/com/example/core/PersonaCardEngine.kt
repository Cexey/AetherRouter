package com.example.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.data.model.ChatMessage
import com.example.data.model.LorebookEntry
import com.example.data.model.PersonaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class ResolvedPersonaPrompt(
    val systemBlock: String,
    val postHistoryInstructions: String,
    val matchedLorebookCount: Int,
)

/**
 * SillyTavern V1/V2 (`chara_card_v2`) JSON, PNG `chara` tEXt chunk, and `.txt`/`.md` prompt parser,
 * plus Lorebook (`character_book`) keyword injection and `{{char}}` / `{{user}}` macro resolution.
 */
class PersonaCardEngine(private val context: Context) {

    companion object {
        private const val MAX_IMPORT_BYTES = 12 * 1024 * 1024L
        private val MACRO_CHAR = Regex("""\{\{\s*char\s*\}\}""", RegexOption.IGNORE_CASE)
        private val MACRO_USER = Regex("""\{\{\s*user\s*\}\}""", RegexOption.IGNORE_CASE)
        private val MACRO_TIME = Regex("""\{\{\s*time\s*\}\}""", RegexOption.IGNORE_CASE)
        private val MACRO_DATE = Regex("""\{\{\s*date\s*\}\}""", RegexOption.IGNORE_CASE)
    }

    private val avatarsDir: File by lazy {
        File(context.filesDir, "personas").apply { mkdirs() }
    }

    suspend fun importFromUri(uri: Uri): Result<PersonaCard> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "imported_persona"
            val ext = displayName.substringAfterLast('.', "").lowercase(Locale.US)
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) {
                        throw IllegalArgumentException("Persona file exceeds 12 MB safety limit")
                    }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: throw IllegalStateException("Unable to read selected file")

            if (ext == "png" || isPngHeader(rawBytes)) {
                val charaJson = extractPngCharaTextChunk(rawBytes)
                    ?: throw IllegalArgumentException("PNG does not contain a SillyTavern 'chara' metadata chunk")
                val avatarFile = File(avatarsDir, "${UUID.randomUUID()}.png")
                FileOutputStream(avatarFile).use { it.write(rawBytes) }
                parseSillyTavernJson(charaJson, fallbackName = displayName.substringBeforeLast('.'))
                    .copy(avatarPath = avatarFile.absolutePath)
            } else {
                val text = rawBytes.toString(Charsets.UTF_8).trim()
                if (text.isEmpty()) throw IllegalArgumentException("Selected file is empty")
                if (ext == "json" || text.startsWith("{")) {
                    parseSillyTavernJson(text, fallbackName = displayName.substringBeforeLast('.'))
                } else {
                    // Plain .txt / .md custom system prompt import
                    val title = displayName.substringBeforeLast('.')
                        .replace('_', ' ')
                        .replace('-', ' ')
                        .trim()
                        .ifBlank { "Custom Prompt" }
                    PersonaCard(
                        id = UUID.randomUUID().toString(),
                        name = title,
                        systemPrompt = text,
                        cardFormat = "text_prompt",
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun parseSillyTavernJson(rawJson: String, fallbackName: String = "Imported Persona"): PersonaCard {
        val root = JSONObject(rawJson)
        val isV2 = root.optString("spec").equals("chara_card_v2", ignoreCase = true) ||
            root.has("data") && root.optJSONObject("data") != null
        val data = if (isV2) root.optJSONObject("data") ?: root else root

        val name = data.optString("name").trim().ifBlank { fallbackName }
        val description = data.optString("description").trim()
        val personality = data.optString("personality").trim()
        val scenario = data.optString("scenario").trim()
        val firstMes = data.optString("first_mes").ifBlank { data.optString("firstMessage") }.trim()
        val systemPrompt = data.optString("system_prompt").ifBlank { data.optString("systemPrompt") }.trim()
        val postHistory = data.optString("post_history_instructions")
            .ifBlank { data.optString("postHistoryInstructions") }
            .trim()
        val creatorNotes = data.optString("creator_notes").trim()

        val altGreetings = buildList {
            val arr = data.optJSONArray("alternate_greetings")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val g = arr.optString(i).trim()
                    if (g.isNotEmpty()) add(g)
                }
            }
        }

        val tags = buildList {
            val arr = data.optJSONArray("tags")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.optString(i).trim()
                    if (t.isNotEmpty()) add(t)
                }
            }
        }

        val lorebook = parseLorebookEntries(data.optJSONObject("character_book"))

        return PersonaCard(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            firstMessage = firstMes,
            alternateGreetings = altGreetings,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistory,
            creatorNotes = creatorNotes,
            tags = tags,
            lorebookEntries = lorebook,
            cardFormat = if (isV2) "chara_card_v2" else "chara_card_v1",
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun resolveMacros(
        raw: String,
        charName: String,
        userDisplayName: String,
        clockSnapshot: LiveClockSnapshot = LiveClockProvider.captureSnapshot(),
        use24HourClock: Boolean = false,
    ): String {
        if (raw.isEmpty()) return ""
        val safeUser = userDisplayName.trim().ifBlank { "User" }
        val safeChar = charName.trim().ifBlank { "Assistant" }
        val timeText = clockSnapshot.displayTime(use24HourClock)
        return raw
            .replace(MACRO_CHAR, safeChar)
            .replace(MACRO_USER, safeUser)
            .replace(MACRO_TIME, timeText)
            .replace(MACRO_DATE, clockSnapshot.formattedFullDate)
    }

    fun buildResolvedPrompt(
        persona: PersonaCard?,
        history: List<ChatMessage>,
        userDisplayName: String,
        clockSnapshot: LiveClockSnapshot,
        use24HourClock: Boolean,
        extraLorebookEntries: List<LorebookEntry> = emptyList(),
    ): ResolvedPersonaPrompt {
        val charName = persona?.name?.takeIf { it.isNotBlank() } ?: "Assistant"
        val resolve: (String) -> String = { text ->
            resolveMacros(text, charName, userDisplayName, clockSnapshot, use24HourClock)
        }

        // Scan the last 6 user/assistant messages for Persona + Global Lorebook keyword matches
        val recentText = history
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(6)
            .joinToString("\n") { it.content }
            .lowercase(Locale.US)

        val allLore = buildList {
            if (persona != null) addAll(persona.lorebookEntries)
            addAll(extraLorebookEntries)
        }

        val matchedLore = allLore
            .filter { entry ->
                entry.enabled &&
                    entry.content.isNotBlank() &&
                    entry.keys.any { key ->
                        val k = key.trim().lowercase(Locale.US)
                        k.isNotEmpty() && recentText.contains(k)
                    }
            }
            .sortedBy { it.insertionOrder }

        val systemBlock = buildString {
            if (persona != null) {
                if (persona.systemPrompt.isNotBlank()) {
                    append(resolve(persona.systemPrompt))
                    append("\n\n")
                }
                if (persona.name.isNotBlank() && (persona.description.isNotBlank() || persona.personality.isNotBlank() || persona.scenario.isNotBlank())) {
                    append("[Persona: ${persona.name}]\n")
                }
                if (persona.description.isNotBlank()) {
                    append("Description: ").append(resolve(persona.description)).append("\n")
                }
                if (persona.personality.isNotBlank()) {
                    append("Personality: ").append(resolve(persona.personality)).append("\n")
                }
                if (persona.scenario.isNotBlank()) {
                    append("Scenario: ").append(resolve(persona.scenario)).append("\n")
                }
            }
            if (matchedLore.isNotEmpty()) {
                append("\n[Active World Info / Lorebook]\n")
                matchedLore.forEach { entry ->
                    append("- ").append(resolve(entry.content)).append("\n")
                }
            }
        }.trim()

        val postHistory = persona?.postHistoryInstructions?.let { resolve(it).trim() }.orEmpty()
        return ResolvedPersonaPrompt(
            systemBlock = systemBlock,
            postHistoryInstructions = postHistory,
            matchedLorebookCount = matchedLore.size,
        )
    }

    fun exportAsSillyTavernV2Json(persona: PersonaCard): String {
        val entriesArr = JSONArray().apply {
            persona.lorebookEntries.forEachIndexed { idx, entry ->
                put(
                    JSONObject().apply {
                        put("id", idx)
                        put("keys", JSONArray().apply { entry.keys.forEach { put(it) } })
                        put("content", entry.content)
                        put("enabled", entry.enabled)
                        put("insertion_order", entry.insertionOrder)
                    },
                )
            }
        }
        val dataObj = JSONObject().apply {
            put("name", persona.name)
            put("description", persona.description)
            put("personality", persona.personality)
            put("scenario", persona.scenario)
            put("first_mes", persona.firstMessage)
            put("alternate_greetings", JSONArray().apply { persona.alternateGreetings.forEach { put(it) } })
            put("system_prompt", persona.systemPrompt)
            put("post_history_instructions", persona.postHistoryInstructions)
            put("creator_notes", persona.creatorNotes)
            put("tags", JSONArray().apply { persona.tags.forEach { put(it) } })
            if (persona.lorebookEntries.isNotEmpty()) {
                put(
                    "character_book",
                    JSONObject().apply {
                        put("name", "${persona.name} Lorebook")
                        put("entries", entriesArr)
                    },
                )
            }
        }
        return JSONObject().apply {
            put("spec", "chara_card_v2")
            put("spec_version", "2.0")
            put("data", dataObj)
        }.toString(2)
    }

    private fun parseLorebookEntries(book: JSONObject?): List<LorebookEntry> {
        if (book == null) return emptyList()
        val entriesObj = book.opt("entries") ?: return emptyList()
        val result = mutableListOf<LorebookEntry>()

        fun parseSingle(obj: JSONObject?) {
            if (obj == null) return
            val keys = mutableListOf<String>()
            val keysArr = obj.optJSONArray("keys") ?: obj.optJSONArray("key")
            if (keysArr != null) {
                for (i in 0 until keysArr.length()) {
                    val k = keysArr.optString(i).trim()
                    if (k.isNotEmpty()) keys.add(k)
                }
            }
            val content = obj.optString("content").trim()
            if (content.isNotEmpty() && keys.isNotEmpty()) {
                result.add(
                    LorebookEntry(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        keys = keys,
                        content = content,
                        enabled = obj.optBoolean("enabled", !obj.optBoolean("disable", false)),
                        insertionOrder = obj.optInt("insertion_order", obj.optInt("order", 100)),
                    ),
                )
            }
        }

        when (entriesObj) {
            is JSONArray -> {
                for (i in 0 until entriesObj.length()) {
                    parseSingle(entriesObj.optJSONObject(i))
                }
            }
            is JSONObject -> {
                val names = entriesObj.keys()
                while (names.hasNext()) {
                    parseSingle(entriesObj.optJSONObject(names.next()))
                }
            }
        }
        return result.sortedBy { it.insertionOrder }
    }

    private fun isPngHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
    }

    private fun extractPngCharaTextChunk(pngBytes: ByteArray): String? {
        return runCatching {
            val dis = DataInputStream(ByteArrayInputStream(pngBytes))
            dis.skipBytes(8) // PNG signature
            while (dis.available() >= 12) {
                val length = dis.readInt()
                if (length < 0 || length > pngBytes.size) break
                val typeBytes = ByteArray(4)
                dis.readFully(typeBytes)
                val chunkType = String(typeBytes, Charsets.US_ASCII)
                val data = ByteArray(length)
                dis.readFully(data)
                dis.skipBytes(4) // CRC
                if (chunkType == "tEXt") {
                    val nullIdx = data.indexOf(0)
                    if (nullIdx > 0) {
                        val keyword = String(data, 0, nullIdx, Charsets.ISO_8859_1)
                        if (keyword.equals("chara", ignoreCase = true) || keyword.equals("ccv3", ignoreCase = true)) {
                            val b64 = String(data, nullIdx + 1, data.size - nullIdx - 1, Charsets.UTF_8).trim()
                            return@runCatching String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                        }
                    }
                }
                if (chunkType == "IEND") break
            }
            null
        }.getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
