package com.example.network

import com.example.core.LiveClockProvider
import com.example.core.LiveClockSnapshot
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.McpToolDef
import com.example.data.model.ReasoningEffort
import com.example.data.model.UrlCitation
import com.example.data.model.WebSearchEngine
import com.example.data.model.WebSearchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class ToolCallPayload(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

sealed interface OpenRouterStreamEvent {
    data class ContentDelta(val text: String) : OpenRouterStreamEvent
    data class ReasoningDelta(val text: String) : OpenRouterStreamEvent
    data class ReasoningDetailsUpdated(val rawJsonArray: String, val hasEncryptedSignature: Boolean) : OpenRouterStreamEvent
    data class CitationsUpdated(val citations: List<UrlCitation>) : OpenRouterStreamEvent
    data class ToolCallsRequested(val calls: List<ToolCallPayload>, val rawToolCallsJson: String) : OpenRouterStreamEvent
    data class UsageUpdated(val promptTokens: Int, val completionTokens: Int) : OpenRouterStreamEvent
    data class Completed(
        val fullContent: String,
        val fullReasoning: String,
        val reasoningDetailsJson: String,
        val citations: List<UrlCitation>,
        val promptTokens: Int,
        val completionTokens: Int,
    ) : OpenRouterStreamEvent
}

/**
 * Lossless assembler for OpenRouter's `delta.reasoning_details` streaming chunks.
 * Preserves every entry type (`reasoning.text`, `reasoning.summary`, `reasoning.encrypted`)
 * including signature-only chunks and encrypted blobs where `delta.reasoning` is null.
 */
internal class ReasoningDetailsAssembler {
    private val orderedKeys = mutableListOf<String>()
    private val entries = LinkedHashMap<String, JSONObject>()
    var hasEncryptedOrSignature: Boolean = false
        private set

    fun ingest(detailsArray: JSONArray): String {
        val appendedHumanText = StringBuilder()
        for (i in 0 until detailsArray.length()) {
            val chunk = detailsArray.optJSONObject(i) ?: continue
            val type = chunk.optString("type", "reasoning.text")
            val index = if (chunk.has("index") && !chunk.isNull("index")) chunk.optInt("index") else -1
            val id = chunk.optString("id", "")
            val key = when {
                id.isNotBlank() -> "$type|id:$id"
                index >= 0 -> "$type|idx:$index"
                else -> "$type|seq:${orderedKeys.size}"
            }

            if (type == "reasoning.encrypted" || !chunk.optString("signature").isNullOrBlank()) {
                hasEncryptedOrSignature = true
            }

            val existing = entries[key]
            if (existing == null) {
                val copy = JSONObject(chunk.toString())
                entries[key] = copy
                orderedKeys.add(key)
                chunk.optString("text").takeIf { it.isNotEmpty() }?.let { appendedHumanText.append(it) }
                chunk.optString("summary").takeIf { it.isNotEmpty() }?.let { appendedHumanText.append(it) }
            } else {
                // Merge incremental string deltas cleanly without mutating signatures or encrypted blobs
                if (chunk.has("text") && !chunk.isNull("text")) {
                    val deltaText = chunk.optString("text")
                    if (deltaText.isNotEmpty()) {
                        existing.put("text", existing.optString("text", "") + deltaText)
                        appendedHumanText.append(deltaText)
                    }
                }
                if (chunk.has("summary") && !chunk.isNull("summary")) {
                    val deltaSummary = chunk.optString("summary")
                    if (deltaSummary.isNotEmpty()) {
                        existing.put("summary", existing.optString("summary", "") + deltaSummary)
                        appendedHumanText.append(deltaSummary)
                    }
                }
                if (chunk.has("data") && !chunk.isNull("data")) {
                    val deltaData = chunk.optString("data")
                    if (deltaData.isNotEmpty()) {
                        existing.put("data", existing.optString("data", "") + deltaData)
                    }
                }
                if (chunk.has("signature") && !chunk.isNull("signature")) {
                    val deltaSig = chunk.optString("signature")
                    if (deltaSig.isNotEmpty()) {
                        existing.put("signature", existing.optString("signature", "") + deltaSig)
                    }
                }
                if (chunk.has("format") && !chunk.isNull("format") && !existing.has("format")) {
                    existing.put("format", chunk.optString("format"))
                }
                if (chunk.has("id") && !chunk.isNull("id") && existing.optString("id").isBlank()) {
                    existing.put("id", chunk.optString("id"))
                }
            }
        }
        return appendedHumanText.toString()
    }

    fun toJsonString(): String {
        if (orderedKeys.isEmpty()) return ""
        val array = JSONArray()
        for (key in orderedKeys) {
            entries[key]?.let { array.put(it) }
        }
        return array.toString()
    }
}

/**
 * Pure OkHttp SSE streaming client for OpenRouter `/api/v1/chat/completions`.
 */
class OpenRouterStreamClient(
    private val okHttpClient: OkHttpClient,
    private val documentExtractor: com.example.core.DocumentExtractor? = null,
) {

    companion object {
        private const val COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    fun streamChatCompletion(
        apiKey: String,
        conversation: Conversation,
        history: List<ChatMessage>,
        modelSupportsTools: Boolean,
        clockSnapshot: LiveClockSnapshot,
        use24HourClock: Boolean,
        projectInstructions: String = "",
        personaSystemBlock: String = "",
        postHistoryInstructions: String = "",
        mcpWireTools: List<Pair<String, McpToolDef>> = emptyList(),
        includeLocalWebFetchTool: Boolean = true,
    ): Flow<OpenRouterStreamEvent> = flow {
        val payload = buildRequestPayload(
            conversation = conversation,
            history = history,
            modelSupportsTools = modelSupportsTools,
            clockSnapshot = clockSnapshot,
            use24HourClock = use24HourClock,
            projectInstructions = projectInstructions,
            personaSystemBlock = personaSystemBlock,
            postHistoryInstructions = postHistoryInstructions,
            mcpWireTools = mcpWireTools,
            includeLocalWebFetchTool = includeLocalWebFetchTool,
        )

        val request = Request.Builder()
            .url(COMPLETIONS_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("HTTP-Referer", "https://aetherrouter.local")
            .header("X-Title", "AetherRouter")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = okHttpClient.newCall(request)
        val response = call.execute()
        response.use { res ->
            if (!res.isSuccessful) {
                val errBody = res.body?.string().orEmpty()
                val parsedMsg = runCatching {
                    JSONObject(errBody).optJSONObject("error")?.optString("message")
                }.getOrNull()
                val detail = parsedMsg?.takeIf { it.isNotBlank() } ?: errBody.take(300)
                throw IllegalStateException("OpenRouter API error (${res.code}): $detail")
            }

            val body = res.body ?: throw IllegalStateException("Empty response body from OpenRouter")
            val contentBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            val detailsAssembler = ReasoningDetailsAssembler()
            val citationsByUrl = LinkedHashMap<String, UrlCitation>()
            val toolCallAccumulators = LinkedHashMap<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()
            var promptTokens = 0
            var completionTokens = 0

            // Track inline <think>...</think> tags if emitted by a model directly inside content
            var insideInlineThinkTag = false
            val pendingTagBuffer = StringBuilder()

            BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (line.isBlank() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    val root = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    if (root.has("error") && !root.isNull("error")) {
                        val errMsg = root.optJSONObject("error")?.optString("message")
                            ?: root.optString("error")
                        throw IllegalStateException("OpenRouter stream error: $errMsg")
                    }

                    root.optJSONObject("usage")?.let { usage ->
                        promptTokens = usage.optInt("prompt_tokens", promptTokens)
                        completionTokens = usage.optInt("completion_tokens", completionTokens)
                        emit(OpenRouterStreamEvent.UsageUpdated(promptTokens, completionTokens))
                    }

                    val choices = root.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.optJSONObject(0) ?: continue
                    val delta = choice.optJSONObject("delta")
                    val fullMsg = choice.optJSONObject("message")

                    // 1. Capture structured reasoning_details (including signature-only & encrypted chunks)
                    val deltaDetails = delta?.optJSONArray("reasoning_details")
                        ?: fullMsg?.optJSONArray("reasoning_details")
                    if (deltaDetails != null && deltaDetails.length() > 0) {
                        val extractedText = detailsAssembler.ingest(deltaDetails)
                        val serialized = detailsAssembler.toJsonString()
                        emit(
                            OpenRouterStreamEvent.ReasoningDetailsUpdated(
                                rawJsonArray = serialized,
                                hasEncryptedSignature = detailsAssembler.hasEncryptedOrSignature,
                            ),
                        )
                        if (extractedText.isNotEmpty()) {
                            reasoningBuilder.append(extractedText)
                            emit(OpenRouterStreamEvent.ReasoningDelta(extractedText))
                        }
                    }

                    // 2. Capture plain delta.reasoning / delta.reasoning_content fallback (avoid double-appending if details already carried it)
                    val plainReasoning = delta?.optString("reasoning")?.takeIf { it.isNotEmpty() && it != "null" }
                        ?: delta?.optString("reasoning_content")?.takeIf { it.isNotEmpty() && it != "null" }
                    if (plainReasoning != null && deltaDetails == null) {
                        reasoningBuilder.append(plainReasoning)
                        emit(OpenRouterStreamEvent.ReasoningDelta(plainReasoning))
                    }

                    // 3. Capture annotations (`url_citation`) from delta or message
                    val annotations = delta?.optJSONArray("annotations")
                        ?: fullMsg?.optJSONArray("annotations")
                    if (annotations != null && annotations.length() > 0) {
                        var added = false
                        for (i in 0 until annotations.length()) {
                            val ann = annotations.optJSONObject(i) ?: continue
                            val urlCitation = ann.optJSONObject("url_citation") ?: continue
                            val url = urlCitation.optString("url").trim()
                            if (url.isNotEmpty() && !citationsByUrl.containsKey(url)) {
                                val title = urlCitation.optString("title").trim().ifBlank { url }
                                val snippet = urlCitation.optString("content").trim()
                                citationsByUrl[url] = UrlCitation(url = url, title = title, snippet = snippet)
                                added = true
                            }
                        }
                        if (added) {
                            emit(OpenRouterStreamEvent.CitationsUpdated(citationsByUrl.values.toList()))
                        }
                    }

                    // 4. Capture streaming tool_calls
                    val deltaToolCalls = delta?.optJSONArray("tool_calls")
                    if (deltaToolCalls != null) {
                        for (i in 0 until deltaToolCalls.length()) {
                            val tc = deltaToolCalls.optJSONObject(i) ?: continue
                            val idx = tc.optInt("index", i)
                            val acc = toolCallAccumulators.getOrPut(idx) {
                                Triple(StringBuilder(), StringBuilder(), StringBuilder())
                            }
                            tc.optString("id").takeIf { it.isNotEmpty() && it != "null" }?.let {
                                if (acc.first.isEmpty()) acc.first.append(it)
                            }
                            val fn = tc.optJSONObject("function")
                            fn?.optString("name")?.takeIf { it.isNotEmpty() && it != "null" }?.let {
                                acc.second.append(it)
                            }
                            fn?.optString("arguments")?.takeIf { it.isNotEmpty() && it != "null" }?.let {
                                acc.third.append(it)
                            }
                        }
                    }

                    // 5. Capture visible content (splitting inline <think>...</think> cleanly if present)
                    val rawDeltaContent = delta?.optString("content")?.takeIf { it.isNotEmpty() && it != "null" }
                        ?: fullMsg?.optString("content")?.takeIf { it.isNotEmpty() && it != "null" }
                    if (rawDeltaContent != null) {
                        pendingTagBuffer.append(rawDeltaContent)
                        var text = pendingTagBuffer.toString()
                        pendingTagBuffer.clear()

                        while (text.isNotEmpty()) {
                            if (!insideInlineThinkTag) {
                                val openIdx = text.indexOf("<think>")
                                if (openIdx >= 0) {
                                    val before = text.substring(0, openIdx)
                                    if (before.isNotEmpty()) {
                                        contentBuilder.append(before)
                                        emit(OpenRouterStreamEvent.ContentDelta(before))
                                    }
                                    insideInlineThinkTag = true
                                    text = text.substring(openIdx + 7)
                                } else if (text.endsWith("<") || text.endsWith("<t") || text.endsWith("<th") ||
                                    text.endsWith("<thi") || text.endsWith("<thin") || text.endsWith("<think")
                                ) {
                                    pendingTagBuffer.append(text)
                                    text = ""
                                } else {
                                    contentBuilder.append(text)
                                    emit(OpenRouterStreamEvent.ContentDelta(text))
                                    text = ""
                                }
                            } else {
                                val closeIdx = text.indexOf("</think>")
                                if (closeIdx >= 0) {
                                    val thinkPart = text.substring(0, closeIdx)
                                    if (thinkPart.isNotEmpty()) {
                                        reasoningBuilder.append(thinkPart)
                                        emit(OpenRouterStreamEvent.ReasoningDelta(thinkPart))
                                    }
                                    insideInlineThinkTag = false
                                    text = text.substring(closeIdx + 8)
                                } else if (text.endsWith("<") || text.endsWith("</") || text.endsWith("</t") ||
                                    text.endsWith("</th") || text.endsWith("</thi") || text.endsWith("</thin") ||
                                    text.endsWith("</think")
                                ) {
                                    pendingTagBuffer.append(text)
                                    text = ""
                                } else {
                                    reasoningBuilder.append(text)
                                    emit(OpenRouterStreamEvent.ReasoningDelta(text))
                                    text = ""
                                }
                            }
                        }
                    }
                }
            }

            // Flush any remaining partial buffer at stream end
            if (pendingTagBuffer.isNotEmpty()) {
                val tail = pendingTagBuffer.toString()
                if (insideInlineThinkTag) {
                    reasoningBuilder.append(tail)
                    emit(OpenRouterStreamEvent.ReasoningDelta(tail))
                } else {
                    contentBuilder.append(tail)
                    emit(OpenRouterStreamEvent.ContentDelta(tail))
                }
            }

            // If the model emitted function tool calls (e.g. local fetch_webpage), notify caller
            if (toolCallAccumulators.isNotEmpty()) {
                val parsedCalls = mutableListOf<ToolCallPayload>()
                val rawArray = JSONArray()
                for ((_, acc) in toolCallAccumulators) {
                    val callId = acc.first.toString().ifBlank { "call_${System.nanoTime()}" }
                    val fnName = acc.second.toString()
                    val args = acc.third.toString().ifBlank { "{}" }
                    if (fnName.isNotBlank()) {
                        parsedCalls.add(ToolCallPayload(id = callId, name = fnName, argumentsJson = args))
                        rawArray.put(
                            JSONObject().apply {
                                put("id", callId)
                                put("type", "function")
                                put(
                                    "function",
                                    JSONObject().apply {
                                        put("name", fnName)
                                        put("arguments", args)
                                    },
                                )
                            },
                        )
                    }
                }
                if (parsedCalls.isNotEmpty()) {
                    emit(OpenRouterStreamEvent.ToolCallsRequested(parsedCalls, rawArray.toString()))
                }
            }

            emit(
                OpenRouterStreamEvent.Completed(
                    fullContent = contentBuilder.toString(),
                    fullReasoning = reasoningBuilder.toString(),
                    reasoningDetailsJson = detailsAssembler.toJsonString(),
                    citations = citationsByUrl.values.toList(),
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestPayload(
        conversation: Conversation,
        history: List<ChatMessage>,
        modelSupportsTools: Boolean,
        clockSnapshot: LiveClockSnapshot,
        use24HourClock: Boolean,
        projectInstructions: String = "",
        personaSystemBlock: String = "",
        postHistoryInstructions: String = "",
        mcpWireTools: List<Pair<String, McpToolDef>> = emptyList(),
        includeLocalWebFetchTool: Boolean,
    ): JSONObject {
        val root = JSONObject()
        root.put("model", conversation.modelId)
        root.put("stream", true)
        root.put("temperature", conversation.temperature.toDouble())
        root.put("top_p", conversation.topP.toDouble())
        root.put("max_tokens", conversation.maxTokens.coerceIn(256, 65536))

        // Reasoning parameter
        if (conversation.reasoningEffort != ReasoningEffort.NONE) {
            root.put(
                "reasoning",
                JSONObject().apply {
                    put("effort", conversation.reasoningEffort.wireValue)
                },
            )
        }

        // Build messages array with real-time Android temporal grounding + project + persona + lorebook
        val messagesArray = JSONArray()
        val systemText = buildString {
            append(clockSnapshot.toPromptContextLine(use24HourClock))
            if (personaSystemBlock.isNotBlank()) {
                append("\n\n")
                append(personaSystemBlock.trim())
            }
            if (projectInstructions.isNotBlank()) {
                append("\n\n[Project Instructions]\n")
                append(projectInstructions.trim())
            }
            if (conversation.systemPromptOverride.isNotBlank()) {
                append("\n\n")
                append(conversation.systemPromptOverride.trim())
            }
        }
        messagesArray.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemText)
            },
        )

        for (msg in history) {
            val obj = JSONObject()
            obj.put("role", msg.role)
            when (msg.role) {
                "user" -> {
                    val stamp = LiveClockProvider.formatMessagePromptStamp(msg.createdAt, use24HourClock)
                    if (msg.attachments.isEmpty()) {
                        obj.put("content", "[$stamp]\n${msg.content}")
                    } else {
                        val contentParts = JSONArray()
                        val combinedText = buildString {
                            append("[$stamp]\n")
                            if (msg.content.isNotBlank()) {
                                append(msg.content)
                            }
                            msg.attachments.forEach { att ->
                                if ((att.kind == AttachmentKind.DOCUMENT || att.kind == AttachmentKind.WEBPAGE) &&
                                    att.extractedText.isNotBlank()
                                ) {
                                    append("\n\n[Attached File: ${att.displayName} (${att.mimeType})]\n")
                                    append(att.extractedText)
                                }
                            }
                        }.trim()
                        if (combinedText.isNotEmpty()) {
                            contentParts.put(
                                JSONObject().apply {
                                    put("type", "text")
                                    put("text", combinedText)
                                },
                            )
                        }
                        msg.attachments.forEach { att ->
                            when (att.kind) {
                                AttachmentKind.IMAGE, AttachmentKind.GIF, AttachmentKind.STICKER -> {
                                    val dataUri = documentExtractor?.encodeImageAttachmentToDataUri(att)
                                    if (!dataUri.isNullOrBlank()) {
                                        contentParts.put(
                                            JSONObject().apply {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    JSONObject().apply { put("url", dataUri) },
                                                )
                                            },
                                        )
                                    }
                                }
                                AttachmentKind.PDF -> {
                                    val pdfDataUri = documentExtractor?.encodePdfAttachmentToDataUri(att)
                                    if (!pdfDataUri.isNullOrBlank()) {
                                        contentParts.put(
                                            JSONObject().apply {
                                                put("type", "file")
                                                put(
                                                    "file",
                                                    JSONObject().apply {
                                                        put("filename", att.displayName)
                                                        put("file_data", pdfDataUri)
                                                    },
                                                )
                                            },
                                        )
                                    }
                                }
                                AttachmentKind.DOCUMENT, AttachmentKind.WEBPAGE -> Unit
                            }
                        }
                        if (contentParts.length() > 0) {
                            obj.put("content", contentParts)
                        } else {
                            obj.put("content", "[$stamp]\n${msg.content}")
                        }
                    }
                }
                "assistant" -> {
                    obj.put("content", msg.content)
                    // Replay exact structured reasoning_details untouched for Claude/Gemini/OpenAI signature continuity
                    if (msg.reasoningDetailsJson.isNotBlank()) {
                        runCatching { JSONArray(msg.reasoningDetailsJson) }.getOrNull()?.let { arr ->
                            if (arr.length() > 0) obj.put("reasoning_details", arr)
                        }
                    }
                    if (msg.toolCallsJson.isNotBlank()) {
                        runCatching { JSONArray(msg.toolCallsJson) }.getOrNull()?.let { arr ->
                            if (arr.length() > 0) obj.put("tool_calls", arr)
                        }
                    }
                }
                "tool" -> {
                    obj.put("tool_call_id", msg.toolCallId)
                    obj.put("content", msg.content)
                }
            }
            messagesArray.put(obj)
        }

        // SillyTavern V2 Post-History Instructions (depth 0 trailing system reinforcement)
        if (postHistoryInstructions.isNotBlank()) {
            messagesArray.put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", postHistoryInstructions.trim())
                },
            )
        }
        root.put("messages", messagesArray)

        val toolsArray = JSONArray()

        // Billing-safe Live Web Search & Fetch Routing
        if (conversation.webSearchEnabled) {
            val useAgenticTools =
                conversation.webSearchMode == WebSearchMode.AGENTIC_TOOL && modelSupportsTools

            if (useAgenticTools) {
                // OpenRouter native billing-safe server tool: web_search
                toolsArray.put(
                    JSONObject().apply {
                        put("type", "openrouter:web_search")
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("max_results", conversation.webMaxResults.coerceIn(1, 10))
                                put("max_total_results", conversation.webMaxTotalResults.coerceIn(5, 30))
                                if (conversation.webSearchEngine != WebSearchEngine.AUTO) {
                                    put("engine", conversation.webSearchEngine.wireValue)
                                }
                            },
                        )
                    },
                )
                // OpenRouter native server tool: web_fetch
                toolsArray.put(
                    JSONObject().apply {
                        put("type", "openrouter:web_fetch")
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("engine", "openrouter")
                                put("max_content_tokens", 50000)
                            },
                        )
                    },
                )
                // Local HTTPS webpage reader fallback tool
                if (includeLocalWebFetchTool) {
                    toolsArray.put(
                        JSONObject().apply {
                            put("type", "function")
                            put(
                                "function",
                                JSONObject().apply {
                                    put("name", "fetch_webpage")
                                    put(
                                        "description",
                                        "Fetch and read the live readable text of a public HTTPS webpage directly from the device.",
                                    )
                                    put(
                                        "parameters",
                                        JSONObject().apply {
                                            put("type", "object")
                                            put(
                                                "properties",
                                                JSONObject().apply {
                                                    put(
                                                        "url",
                                                        JSONObject().apply {
                                                            put("type", "string")
                                                            put("description", "Public http or https URL to fetch")
                                                        },
                                                    )
                                                },
                                            )
                                            put("required", JSONArray().put("url"))
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            } else {
                // Always-On Grounding via OpenRouter web plugin (for forced search or non-tool models)
                val pluginsArray = JSONArray()
                pluginsArray.put(
                    JSONObject().apply {
                        put("id", "web")
                        put("max_results", conversation.webMaxResults.coerceIn(1, 10))
                        if (conversation.webSearchEngine != WebSearchEngine.AUTO) {
                            put("engine", conversation.webSearchEngine.wireValue)
                        }
                    },
                )
                root.put("plugins", pluginsArray)
            }
        }

        // Register enabled remote MCP server tools for tool-capable models
        if (modelSupportsTools && mcpWireTools.isNotEmpty()) {
            mcpWireTools.forEach { (wireName, toolDef) ->
                val paramsSchema = runCatching { JSONObject(toolDef.inputSchemaJson) }
                    .getOrDefault(JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                toolsArray.put(
                    JSONObject().apply {
                        put("type", "function")
                        put(
                            "function",
                            JSONObject().apply {
                                put("name", wireName)
                                put("description", toolDef.description.ifBlank { "MCP tool: ${toolDef.name}" })
                                put("parameters", paramsSchema)
                            },
                        )
                    },
                )
            }
        }

        if (toolsArray.length() > 0) {
            root.put("tools", toolsArray)
        }

        return root
    }
}
