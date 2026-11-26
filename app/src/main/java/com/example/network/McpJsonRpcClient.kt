package com.example.network

import com.example.data.model.McpServerConfig
import com.example.data.model.McpToolDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

/**
 * Remote HTTP / Streamable-HTTP JSON-RPC 2.0 client for Model Context Protocol (MCP) servers.
 * Supports encrypted custom auth headers, `initialize` + `tools/list` discovery, and `tools/call` execution.
 */
class McpJsonRpcClient(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RESPONSE_BYTES = 1_000_000
    }

    private val requestId = AtomicInteger(1)

    fun validateEndpointUrl(rawUrl: String): Result<String> = runCatching {
        val clean = rawUrl.trim()
        val uri = URI(clean)
        val scheme = uri.scheme?.lowercase() ?: throw IllegalArgumentException("Missing URL scheme")
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Missing host")

        val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        if (scheme != "https" && !(scheme == "http" && isLoopback)) {
            throw IllegalArgumentException("MCP endpoints must use HTTPS (or local http://localhost)")
        }
        // Block link-local cloud metadata addresses (e.g. 169.254.169.254)
        if (!isLoopback) {
            val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull()
            if (addresses != null && addresses.any { it.isLinkLocalAddress || it.isMulticastAddress || it.isAnyLocalAddress }) {
                throw IllegalArgumentException("Blocked restricted link-local or metadata host")
            }
        }
        clean
    }

    suspend fun discoverTools(server: McpServerConfig): Result<List<McpToolDef>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = validateEndpointUrl(server.endpointUrl).getOrThrow()

            // Best-effort MCP initialize handshake (some stateless servers accept tools/list directly)
            val initBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", requestId.getAndIncrement())
                put("method", "initialize")
                put(
                    "params",
                    JSONObject().apply {
                        put("protocolVersion", "2024-11-05")
                        put("capabilities", JSONObject())
                        put(
                            "clientInfo",
                            JSONObject().apply {
                                put("name", "AetherRouter")
                                put("version", "1.0.0")
                            },
                        )
                    },
                )
            }
            val sessionId = runCatching { executeRpc(endpoint, server, initBody, null).second }.getOrNull()

            // Discover tools via tools/list
            val listBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", requestId.getAndIncrement())
                put("method", "tools/list")
                put("params", JSONObject())
            }
            val (rpcResult, _) = executeRpc(endpoint, server, listBody, sessionId)
            val resultObj = rpcResult.optJSONObject("result")
                ?: throw IllegalStateException(
                    rpcResult.optJSONObject("error")?.optString("message")
                        ?: "MCP server returned no tools result",
                )

            val toolsArr = resultObj.optJSONArray("tools") ?: JSONArray()
            buildList {
                for (i in 0 until toolsArr.length()) {
                    val t = toolsArr.optJSONObject(i) ?: continue
                    val name = t.optString("name").trim()
                    if (name.isEmpty()) continue
                    val desc = t.optString("description", "").trim()
                    val schema = t.optJSONObject("inputSchema")?.toString()
                        ?: """{"type":"object","properties":{}}"""
                    add(McpToolDef(name = name, description = desc, inputSchemaJson = schema))
                }
            }
        }
    }

    suspend fun callTool(
        server: McpServerConfig,
        toolName: String,
        argumentsJson: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = validateEndpointUrl(server.endpointUrl).getOrThrow()
            val argsObj = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            val rpcBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", requestId.getAndIncrement())
                put("method", "tools/call")
                put(
                    "params",
                    JSONObject().apply {
                        put("name", toolName)
                        put("arguments", argsObj)
                    },
                )
            }
            val (rpcResult, _) = executeRpc(endpoint, server, rpcBody, null)
            val errorObj = rpcResult.optJSONObject("error")
            if (errorObj != null) {
                throw IllegalStateException("MCP error (${errorObj.optInt("code")}): ${errorObj.optString("message")}")
            }
            val resultObj = rpcResult.optJSONObject("result")
            if (resultObj == null) {
                return@runCatching rpcResult.toString(2).take(12_000)
            }
            val contentArr = resultObj.optJSONArray("content")
            if (contentArr != null && contentArr.length() > 0) {
                buildString {
                    for (i in 0 until contentArr.length()) {
                        val item = contentArr.optJSONObject(i) ?: continue
                        val txt = item.optString("text")
                        if (txt.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(txt)
                        } else {
                            if (isNotEmpty()) append("\n")
                            append(item.toString())
                        }
                    }
                }.take(16_000)
            } else {
                resultObj.toString(2).take(16_000)
            }
        }
    }

    private fun executeRpc(
        endpoint: String,
        server: McpServerConfig,
        payload: JSONObject,
        sessionId: String?,
    ): Pair<JSONObject, String?> {
        val builder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .post(payload.toString().toRequestBody(JSON_MEDIA))

        if (server.authHeaderName.isNotBlank() && server.authHeaderValue.isNotBlank()) {
            builder.header(server.authHeaderName.trim(), server.authHeaderValue.trim())
        }
        if (!sessionId.isNullOrBlank()) {
            builder.header("Mcp-Session-Id", sessionId)
        }

        okHttpClient.newCall(builder.build()).execute().use { res ->
            val raw = res.body?.string().orEmpty().take(MAX_RESPONSE_BYTES)
            if (!res.isSuccessful) {
                throw IllegalStateException("HTTP ${res.code} from ${server.name}: ${raw.take(240)}")
            }
            val newSessionId = res.header("Mcp-Session-Id") ?: sessionId
            val contentType = res.header("Content-Type").orEmpty().lowercase()

            val jsonObj = if (contentType.contains("text/event-stream") || raw.trimStart().startsWith("data:")) {
                // Parse Streamable-HTTP SSE response frames for JSON-RPC result
                val dataLines = raw.lines()
                    .filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .filter { it.startsWith("{") }
                val candidate = dataLines.lastOrNull()
                    ?: throw IllegalStateException("Empty SSE response from MCP server")
                JSONObject(candidate)
            } else {
                JSONObject(raw.ifBlank { "{}" })
            }
            return jsonObj to newSessionId
        }
    }

    fun makeWireToolName(serverId: String, toolName: String): String {
        val safeServer = serverId.replace(Regex("[^a-zA-Z0-9]"), "").take(8)
        val safeTool = toolName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(42)
        return "mcp__${safeServer}__${safeTool}"
    }
}
