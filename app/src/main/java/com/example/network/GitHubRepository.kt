package com.example.network

import android.util.Base64
import com.example.core.CodeArtifactUtils
import com.example.data.model.GitHubBranchSummary
import com.example.data.model.GitHubCommitPreview
import com.example.data.model.GitHubCommitResult
import com.example.data.model.GitHubContentEntry
import com.example.data.model.GitHubRepoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GitHubFilePayload(
    val name: String,
    val path: String,
    val sha: String,
    val contentText: String,
    val sizeBytes: Long,
)

/**
 * Lightweight GitHub REST API client pinned strictly to `https://api.github.com`.
 * Supports repo listing, branch switching, folder/file browsing, attaching files to chat,
 * and pushing commits with automatic existing-file `sha` lookup.
 */
class GitHubRepository(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val API_BASE = "https://api.github.com"
        private const val MAX_ATTACH_BYTES = 512_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val _authenticatedUser = MutableStateFlow<String?>(null)
    val authenticatedUser: StateFlow<String?> = _authenticatedUser.asStateFlow()

    private val _cachedRepos = MutableStateFlow<List<GitHubRepoSummary>>(emptyList())
    val cachedRepos: StateFlow<List<GitHubRepoSummary>> = _cachedRepos.asStateFlow()

    fun clearMemoryCache() {
        _authenticatedUser.value = null
        _cachedRepos.value = emptyList()
    }

    suspend fun verifyToken(pat: String): Result<String> = withContext(Dispatchers.IO) {
        val token = pat.trim()
        if (token.isEmpty()) return@withContext Result.failure(IllegalArgumentException("GitHub token is empty"))
        runCatching {
            val req = baseBuilder("$API_BASE/user", token).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IllegalStateException(parseGitHubError(res.code, body))
                }
                val login = JSONObject(body).optString("login").trim()
                if (login.isBlank()) throw IllegalStateException("Unable to read GitHub account login")
                _authenticatedUser.value = login
                login
            }
        }
    }

    suspend fun listRepositories(pat: String): Result<List<GitHubRepoSummary>> = withContext(Dispatchers.IO) {
        val token = pat.trim()
        if (token.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Configure a GitHub PAT in Settings first"))
        runCatching {
            val url = "$API_BASE/user/repos".toHttpUrl().newBuilder()
                .addQueryParameter("sort", "updated")
                .addQueryParameter("per_page", "100")
                .addQueryParameter("affiliation", "owner,collaborator,organization_member")
                .build()
            val req = baseBuilder(url.toString(), token).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IllegalStateException(parseGitHubError(res.code, body))
                }
                val arr = JSONArray(body)
                val repos = mutableListOf<GitHubRepoSummary>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val ownerLogin = obj.optJSONObject("owner")?.optString("login").orEmpty()
                    val name = obj.optString("name").orEmpty()
                    if (ownerLogin.isBlank() || name.isBlank()) continue
                    repos.add(
                        GitHubRepoSummary(
                            id = obj.optLong("id", i.toLong()),
                            name = name,
                            fullName = obj.optString("full_name").ifBlank { "$ownerLogin/$name" },
                            owner = ownerLogin,
                            description = obj.optString("description").takeUnless { it == "null" }.orEmpty(),
                            defaultBranch = obj.optString("default_branch").ifBlank { "main" },
                            isPrivate = obj.optBoolean("private", false),
                            language = obj.optString("language").takeUnless { it == "null" }.orEmpty(),
                            htmlUrl = obj.optString("html_url").orEmpty(),
                            updatedAt = obj.optString("updated_at").orEmpty(),
                        ),
                    )
                }
                _cachedRepos.value = repos
                repos
            }
        }
    }

    suspend fun listBranches(pat: String, owner: String, repo: String): Result<List<GitHubBranchSummary>> =
        withContext(Dispatchers.IO) {
            val token = pat.trim()
            if (token.isEmpty()) return@withContext Result.failure(IllegalArgumentException("GitHub PAT missing"))
            if (!CodeArtifactUtils.isValidGitHubIdentifier(owner) || !CodeArtifactUtils.isValidGitHubIdentifier(repo)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid GitHub repository identifier"))
            }
            runCatching {
                val url = "$API_BASE/repos/$owner/$repo/branches".toHttpUrl().newBuilder()
                    .addQueryParameter("per_page", "100")
                    .build()
                val req = baseBuilder(url.toString(), token).get().build()
                okHttpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw IllegalStateException(parseGitHubError(res.code, body))
                    }
                    val arr = JSONArray(body)
                    val branches = mutableListOf<GitHubBranchSummary>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        if (name.isNotEmpty()) {
                            branches.add(
                                GitHubBranchSummary(
                                    name = name,
                                    isProtected = obj.optBoolean("protected", false),
                                    sha = obj.optJSONObject("commit")?.optString("sha").orEmpty(),
                                ),
                            )
                        }
                    }
                    branches
                }
            }
        }

    suspend fun listContents(
        pat: String,
        owner: String,
        repo: String,
        path: String,
        branch: String,
    ): Result<List<GitHubContentEntry>> = withContext(Dispatchers.IO) {
        val token = pat.trim()
        if (token.isEmpty()) return@withContext Result.failure(IllegalArgumentException("GitHub PAT missing"))
        if (!CodeArtifactUtils.isValidGitHubIdentifier(owner) || !CodeArtifactUtils.isValidGitHubIdentifier(repo)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid repository owner or name"))
        }
        val cleanPath = CodeArtifactUtils.sanitizeGitHubPath(path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid repository path"))
        runCatching {
            val urlBuilder = "$API_BASE/repos/$owner/$repo/contents".toHttpUrl().newBuilder()
            if (cleanPath.isNotEmpty()) {
                cleanPath.split('/').forEach { segment -> urlBuilder.addPathSegment(segment) }
            }
            if (branch.isNotBlank()) {
                urlBuilder.addQueryParameter("ref", branch.trim())
            }
            val req = baseBuilder(urlBuilder.build().toString(), token).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IllegalStateException(parseGitHubError(res.code, body))
                }
                val trimmed = body.trim()
                val entries = mutableListOf<GitHubContentEntry>()
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        entries.add(parseContentEntry(obj))
                    }
                } else {
                    entries.add(parseContentEntry(JSONObject(trimmed)))
                }
                entries.sortedWith(compareByDescending<GitHubContentEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            }
        }
    }

    suspend fun fetchFileContent(
        pat: String,
        owner: String,
        repo: String,
        path: String,
        branch: String,
    ): Result<GitHubFilePayload> = withContext(Dispatchers.IO) {
        val token = pat.trim()
        if (token.isEmpty()) return@withContext Result.failure(IllegalArgumentException("GitHub PAT missing"))
        if (!CodeArtifactUtils.isValidGitHubIdentifier(owner) || !CodeArtifactUtils.isValidGitHubIdentifier(repo)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid repository identifier"))
        }
        val cleanPath = CodeArtifactUtils.sanitizeGitHubPath(path)
            ?.takeIf { it.isNotEmpty() }
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid file path"))

        runCatching {
            val urlBuilder = "$API_BASE/repos/$owner/$repo/contents".toHttpUrl().newBuilder()
            cleanPath.split('/').forEach { urlBuilder.addPathSegment(it) }
            if (branch.isNotBlank()) urlBuilder.addQueryParameter("ref", branch.trim())

            val req = baseBuilder(urlBuilder.build().toString(), token).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IllegalStateException(parseGitHubError(res.code, body))
                }
                val obj = JSONObject(body)
                val size = obj.optLong("size", 0L)
                if (size > MAX_ATTACH_BYTES) {
                    throw IllegalStateException("File exceeds ${MAX_ATTACH_BYTES / 1024} KB text attach limit ($size bytes)")
                }
                val encoding = obj.optString("encoding").lowercase()
                val rawContent = obj.optString("content")
                val decodedText = if (encoding == "base64" && rawContent.isNotBlank()) {
                    val cleanedB64 = rawContent.replace("\n", "").replace("\r", "")
                    val bytes = Base64.decode(cleanedB64, Base64.DEFAULT)
                    String(bytes, Charsets.UTF_8)
                } else {
                    rawContent
                }
                GitHubFilePayload(
                    name = obj.optString("name").ifBlank { cleanPath.substringAfterLast('/') },
                    path = obj.optString("path").ifBlank { cleanPath },
                    sha = obj.optString("sha"),
                    contentText = decodedText,
                    sizeBytes = size.coerceAtLeast(decodedText.toByteArray(Charsets.UTF_8).size.toLong()),
                )
            }
        }
    }

    /**
     * Prepares a Commit Preview by looking up whether `path` already exists on `branch`
     * so we automatically capture its `sha` for seamless updates without 409 conflicts.
     */
    suspend fun prepareCommitPreview(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        rawPath: String,
        content: String,
        commitMessage: String,
    ): Result<GitHubCommitPreview> = withContext(Dispatchers.IO) {
        val token = pat.trim()
        if (token.isEmpty()) return@withContext Result.failure(IllegalArgumentException("GitHub PAT missing"))
        if (!CodeArtifactUtils.isValidGitHubIdentifier(owner) || !CodeArtifactUtils.isValidGitHubIdentifier(repo)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid repository owner or name"))
        }
        val cleanPath = CodeArtifactUtils.sanitizeGitHubPath(rawPath)
            ?.takeIf { it.isNotEmpty() }
            ?: return@withContext Result.failure(IllegalArgumentException("Enter a valid repository file path (no '..' or leading slash)"))
        val cleanBranch = branch.trim().ifBlank { "main" }
        val cleanMsg = commitMessage.trim().ifBlank { "Update $cleanPath via AetherRouter" }

        runCatching {
            val existingSha = fetchExistingFileShaOrNull(token, owner, repo, cleanPath, cleanBranch)
            val byteSize = content.toByteArray(Charsets.UTF_8).size
            val lineCount = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
            GitHubCommitPreview(
                owner = owner,
                repo = repo,
                branch = cleanBranch,
                path = cleanPath,
                content = content,
                commitMessage = cleanMsg,
                existingSha = existingSha,
                lineCount = lineCount,
                byteSize = byteSize,
            )
        }
    }

    suspend fun commitFile(
        pat: String,
        preview: GitHubCommitPreview,
    ): Result<GitHubCommitResult> = withContext(Dispatchers.IO) {
        val token = pat.trim()
        if (token.isEmpty()) return@withContext Result.failure(IllegalArgumentException("GitHub PAT missing"))
        if (!CodeArtifactUtils.isValidGitHubIdentifier(preview.owner) ||
            !CodeArtifactUtils.isValidGitHubIdentifier(preview.repo)
        ) {
            return@withContext Result.failure(IllegalArgumentException("Invalid repository identifier"))
        }
        val cleanPath = CodeArtifactUtils.sanitizeGitHubPath(preview.path)
            ?.takeIf { it.isNotEmpty() }
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid commit file path"))

        runCatching {
            // Re-verify latest SHA right before PUT so concurrent changes don't collide
            val latestSha = preview.existingSha
                ?: fetchExistingFileShaOrNull(token, preview.owner, preview.repo, cleanPath, preview.branch)

            val encodedBase64 = Base64.encodeToString(
                preview.content.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
            val payload = JSONObject().apply {
                put("message", preview.commitMessage.trim().ifBlank { "Commit $cleanPath via AetherRouter" })
                put("content", encodedBase64)
                put("branch", preview.branch)
                if (!latestSha.isNullOrBlank()) {
                    put("sha", latestSha)
                }
            }

            val urlBuilder = "$API_BASE/repos/${preview.owner}/${preview.repo}/contents".toHttpUrl().newBuilder()
            cleanPath.split('/').forEach { urlBuilder.addPathSegment(it) }

            val req = baseBuilder(urlBuilder.build().toString(), token)
                .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IllegalStateException(parseGitHubError(res.code, body))
                }
                val root = JSONObject(body)
                val commitObj = root.optJSONObject("commit")
                val contentObj = root.optJSONObject("content")
                GitHubCommitResult(
                    commitSha = commitObj?.optString("sha").orEmpty().take(12),
                    htmlUrl = contentObj?.optString("html_url")
                        ?.takeIf { it.isNotBlank() }
                        ?: commitObj?.optString("html_url").orEmpty(),
                    path = cleanPath,
                    branch = preview.branch,
                    isUpdate = !latestSha.isNullOrBlank(),
                )
            }
        }
    }

    private fun fetchExistingFileShaOrNull(
        token: String,
        owner: String,
        repo: String,
        cleanPath: String,
        branch: String,
    ): String? {
        val urlBuilder = "$API_BASE/repos/$owner/$repo/contents".toHttpUrl().newBuilder()
        cleanPath.split('/').forEach { urlBuilder.addPathSegment(it) }
        urlBuilder.addQueryParameter("ref", branch)
        val req = baseBuilder(urlBuilder.build().toString(), token).get().build()
        return okHttpClient.newCall(req).execute().use { res ->
            if (res.code == 404) return@use null
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException(parseGitHubError(res.code, body))
            }
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) {
                throw IllegalStateException("Target path '$cleanPath' is a directory, not a file")
            }
            JSONObject(trimmed).optString("sha").takeIf { it.isNotBlank() }
        }
    }

    private fun parseContentEntry(obj: JSONObject): GitHubContentEntry =
        GitHubContentEntry(
            name = obj.optString("name"),
            path = obj.optString("path"),
            type = obj.optString("type", "file"),
            sizeBytes = obj.optLong("size", 0L),
            sha = obj.optString("sha"),
            htmlUrl = obj.optString("html_url"),
        )

    private fun baseBuilder(url: String, token: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "AetherRouter-Android")

    private fun parseGitHubError(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
        return when (code) {
            401 -> "GitHub authentication failed (401). Check your Personal Access Token."
            403 -> "GitHub access denied or rate limited (403): ${msg ?: "Check token repo scopes."}"
            404 -> "GitHub repository, branch, or path not found (404)."
            409 -> "GitHub file conflict (409): the branch updated concurrently. Retry preview."
            422 -> "GitHub validation error (422): ${msg ?: "Invalid path or branch."}"
            else -> "GitHub API error ($code): ${msg ?: body.take(180)}"
        }
    }
}
