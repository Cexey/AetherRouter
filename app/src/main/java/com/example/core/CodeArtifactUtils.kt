package com.example.core

import java.util.Locale

data class CodeExportSpec(
    val suggestedFileName: String,
    val extension: String,
    val mimeType: String,
    val code: String,
)

/**
 * Helpers for Markdown code-block file saving, HTML/CSS/JS live preview detection,
 * and strict GitHub path/identifier sanitization.
 */
object CodeArtifactUtils {

    private val LANGUAGE_TO_EXT_MIME: Map<String, Pair<String, String>> = mapOf(
        "kt" to ("kt" to "text/x-kotlin"),
        "kotlin" to ("kt" to "text/x-kotlin"),
        "kts" to ("kts" to "text/x-kotlin"),
        "py" to ("py" to "text/x-python"),
        "python" to ("py" to "text/x-python"),
        "js" to ("js" to "application/javascript"),
        "javascript" to ("js" to "application/javascript"),
        "mjs" to ("mjs" to "application/javascript"),
        "jsx" to ("jsx" to "text/javascript"),
        "ts" to ("ts" to "text/typescript"),
        "typescript" to ("ts" to "text/typescript"),
        "tsx" to ("tsx" to "text/typescript"),
        "html" to ("html" to "text/html"),
        "htm" to ("html" to "text/html"),
        "svg" to ("svg" to "image/svg+xml"),
        "css" to ("css" to "text/css"),
        "scss" to ("scss" to "text/css"),
        "json" to ("json" to "application/json"),
        "md" to ("md" to "text/markdown"),
        "markdown" to ("md" to "text/markdown"),
        "sh" to ("sh" to "text/x-shellscript"),
        "bash" to ("sh" to "text/x-shellscript"),
        "zsh" to ("sh" to "text/x-shellscript"),
        "shell" to ("sh" to "text/x-shellscript"),
        "xml" to ("xml" to "application/xml"),
        "yaml" to ("yaml" to "text/yaml"),
        "yml" to ("yml" to "text/yaml"),
        "sql" to ("sql" to "application/sql"),
        "java" to ("java" to "text/x-java-source"),
        "c" to ("c" to "text/x-c"),
        "cpp" to ("cpp" to "text/x-c++src"),
        "cxx" to ("cpp" to "text/x-c++src"),
        "rs" to ("rs" to "text/rust"),
        "rust" to ("rs" to "text/rust"),
        "go" to ("go" to "text/x-go"),
        "golang" to ("go" to "text/x-go"),
        "rb" to ("rb" to "text/x-ruby"),
        "ruby" to ("rb" to "text/x-ruby"),
        "php" to ("php" to "application/x-httpd-php"),
        "swift" to ("swift" to "text/x-swift"),
        "dart" to ("dart" to "text/x-dart"),
        "csv" to ("csv" to "text/csv"),
        "toml" to ("toml" to "text/plain"),
        "ini" to ("ini" to "text/plain"),
        "dockerfile" to ("Dockerfile" to "text/plain"),
        "txt" to ("txt" to "text/plain"),
        "text" to ("txt" to "text/plain"),
        "plaintext" to ("txt" to "text/plain"),
    )

    private val GITHUB_SEGMENT_REGEX = Regex("^[A-Za-z0-9._-]+$")

    fun inferExtensionAndMime(languageTag: String): Pair<String, String> {
        val normalized = languageTag.trim().lowercase(Locale.US)
            .substringBefore(' ')
            .substringBefore(':')
        return LANGUAGE_TO_EXT_MIME[normalized] ?: ("txt" to "text/plain")
    }

    fun buildCodeExportSpec(languageTag: String, code: String, baseName: String = "snippet"): CodeExportSpec {
        val (ext, mime) = inferExtensionAndMime(languageTag)
        val cleanBase = baseName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "snippet" }
        val fileName = if (ext == "Dockerfile") {
            "Dockerfile"
        } else {
            val baseWithoutExt = cleanBase.substringBeforeLast('.', cleanBase)
            "$baseWithoutExt.$ext"
        }
        return CodeExportSpec(
            suggestedFileName = fileName,
            extension = ext,
            mimeType = mime,
            code = code,
        )
    }

    fun isPreviewableHtmlArtifact(languageTag: String, code: String): Boolean {
        val lang = languageTag.trim().lowercase(Locale.US)
        if (lang == "html" || lang == "htm" || lang == "svg") return code.isNotBlank()
        val head = code.trimStart().take(300).lowercase(Locale.US)
        return head.startsWith("<!doctype html") ||
            head.startsWith("<html") ||
            (head.contains("<html") && head.contains("<body")) ||
            (head.startsWith("<svg") && code.contains("</svg>", ignoreCase = true))
    }

    fun wrapArtifactForOfflinePreview(languageTag: String, code: String): String {
        val trimmed = code.trim()
        val lower = trimmed.lowercase(Locale.US)
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html")) {
            return trimmed
        }
        val lang = languageTag.trim().lowercase(Locale.US)
        val bodyContent = if (lang == "svg" || lower.startsWith("<svg")) {
            """<div style="display:flex;align-items:center;justify-content:center;min-height:100vh;padding:16px;box-sizing:border-box;">$trimmed</div>"""
        } else {
            trimmed
        }
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <style>
                body {
                  margin: 0;
                  padding: 16px;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  background: #ffffff;
                  color: #111827;
                }
              </style>
            </head>
            <body>
            $bodyContent
            </body>
            </html>
        """.trimIndent()
    }

    fun isValidGitHubIdentifier(segment: String): Boolean {
        val trimmed = segment.trim()
        if (trimmed.isEmpty() || trimmed.length > 100) return false
        if (trimmed == "." || trimmed == ".." || trimmed.contains("..")) return false
        return GITHUB_SEGMENT_REGEX.matches(trimmed)
    }

    /**
     * Validates and normalizes a repository-relative file path for GitHub Contents API.
     * Rejects path traversal (`..`), control characters, backslashes, or empty segments.
     */
    fun sanitizeGitHubPath(rawPath: String): String? {
        val trimmed = rawPath.trim().trim('/')
        if (trimmed.isEmpty()) return ""
        if (trimmed.contains('\\') || trimmed.any { it.code < 32 || it.code == 127 }) return null
        val segments = trimmed.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." || it.contains("..") }) return null
        return segments.joinToString("/")
    }
}
