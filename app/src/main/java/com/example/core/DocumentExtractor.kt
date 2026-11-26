package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Pure-Kotlin multimodal media vault, custom font installer, and wide-format document extractor.
 * Supports Images, animated GIFs/WebP stickers, PDF (native file_data + PdfRenderer preview),
 * DOCX, ODT, PPTX, XLSX, EPUB, RTF, CSV, JSON, Markdown, and source code files.
 */
class DocumentExtractor(private val context: Context) {

    companion object {
        private const val MAX_FILE_BYTES = 18 * 1024 * 1024L // 18 MB cap
        private const val MAX_EXTRACTED_CHARS = 48_000
        private const val MAX_IMAGE_DIMENSION = 1600
        private val HTML_TAG_REGEX = Regex("(?is)<(script|style)[^>]*>.*?</\\1>|<[^>]+>")
        private val RTF_CTRL_REGEX = Regex("""\\[a-zA-Z]+-?\d*\s?|\{\\\*[^}]*\}|[{}]""")
    }

    val mediaDir: File by lazy {
        File(context.filesDir, "media").apply { mkdirs() }
    }

    val fontsDir: File by lazy {
        File(context.filesDir, "fonts").apply { mkdirs() }
    }

    val animatedImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    suspend fun ingestUri(
        uri: Uri,
        forcedKind: AttachmentKind? = null,
    ): Result<ChatAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val displayName = resolveDisplayName(uri) ?: "attachment_${System.currentTimeMillis()}"
            val mimeType = resolver.getType(uri) ?: guessMimeFromName(displayName)
            val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)

            val kind = forcedKind ?: classifyKind(mimeType, extension)
            val safeFile = createConfinedFile(mediaDir, extension.ifBlank { "bin" })

            var bytesWritten = 0L
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(safeFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        bytesWritten += read
                        if (bytesWritten > MAX_FILE_BYTES) {
                            throw IllegalArgumentException("File exceeds 18 MB safety limit")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IllegalStateException("Unable to read selected file")

            var previewPath = ""
            var extractedText = ""

            when (kind) {
                AttachmentKind.IMAGE, AttachmentKind.GIF, AttachmentKind.STICKER -> {
                    previewPath = safeFile.absolutePath
                }
                AttachmentKind.PDF -> {
                    previewPath = renderPdfFirstPageThumbnail(safeFile)
                }
                AttachmentKind.DOCUMENT -> {
                    extractedText = extractDocumentText(safeFile, extension, mimeType)
                        .take(MAX_EXTRACTED_CHARS)
                }
                AttachmentKind.WEBPAGE -> Unit
            }

            ChatAttachment(
                id = UUID.randomUUID().toString(),
                displayName = displayName,
                mimeType = mimeType,
                kind = kind,
                localFilePath = safeFile.absolutePath,
                previewImagePath = previewPath,
                extractedText = extractedText,
                sizeBytes = bytesWritten,
            )
        }
    }

    suspend fun installCustomFont(uri: Uri): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "custom_font.ttf"
            val ext = displayName.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext != "ttf" && ext != "otf") {
                throw IllegalArgumentException("Please select a .ttf or .otf font file")
            }
            val target = createConfinedFile(fontsDir, ext)
            var bytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        bytes += n
                        if (bytes > 10 * 1024 * 1024L) {
                            throw IllegalArgumentException("Font file exceeds 10 MB limit")
                        }
                        out.write(buf, 0, n)
                    }
                }
            } ?: throw IllegalStateException("Could not open font file")

            // Verify valid Android Typeface before saving path
            val typeface = Typeface.createFromFile(target)
            if (typeface == null) {
                target.delete()
                throw IllegalArgumentException("Invalid or corrupted font file")
            }

            val cleanName = displayName.substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank { "Custom Font" }
            target.absolutePath to cleanName
        }
    }

    fun encodeImageAttachmentToDataUri(attachment: ChatAttachment): String? {
        val file = resolveSafeMediaFile(attachment.localFilePath) ?: return null
        return runCatching {
            // Preserve GIF/WebP animated bytes directly if small, or compress large raster photos
            if ((attachment.mimeType.equals("image/gif", ignoreCase = true) ||
                    attachment.mimeType.equals("image/webp", ignoreCase = true)) &&
                file.length() <= 4 * 1024 * 1024L
            ) {
                val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                return@runCatching "data:${attachment.mimeType};base64,$b64"
            }

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > MAX_IMAGE_DIMENSION || opts.outHeight / sample > MAX_IMAGE_DIMENSION) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                ?: return@runCatching null

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            bitmap.recycle()
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        }.getOrNull()
    }

    fun encodePdfAttachmentToDataUri(attachment: ChatAttachment): String? {
        val file = resolveSafeMediaFile(attachment.localFilePath) ?: return null
        if (file.length() > MAX_FILE_BYTES) return null
        return runCatching {
            val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            "data:application/pdf;base64,$b64"
        }.getOrNull()
    }

    private fun resolveSafeMediaFile(path: String): File? {
        if (path.isBlank()) return null
        val file = File(path).canonicalFile
        val root = mediaDir.canonicalPath + File.separator
        return if (file.canonicalPath.startsWith(root) && file.exists()) file else null
    }

    private fun createConfinedFile(baseDir: File, ext: String): File {
        val safeExt = ext.replace(Regex("[^a-z0-9]"), "").take(8).ifBlank { "bin" }
        val file = File(baseDir, "${UUID.randomUUID()}.$safeExt").canonicalFile
        val root = baseDir.canonicalPath + File.separator
        require(file.canonicalPath.startsWith(root)) { "Path confinement violation" }
        return file
    }

    private fun renderPdfFirstPageThumbnail(pdfFile: File): String {
        return runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return@runCatching ""
                    renderer.openPage(0).use { page ->
                        val targetWidth = 360
                        val targetHeight = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetWidth)
                            .toInt()
                            .coerceIn(180, 540)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val thumbFile = createConfinedFile(mediaDir, "png")
                        FileOutputStream(thumbFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                        }
                        bitmap.recycle()
                        thumbFile.absolutePath
                    }
                }
            }
        }.getOrDefault("")
    }

    private fun extractDocumentText(file: File, extension: String, mimeType: String): String {
        return runCatching {
            when (extension) {
                "docx" -> extractXmlFromZip(file, setOf("word/document.xml"))
                "odt" -> extractXmlFromZip(file, setOf("content.xml"))
                "pptx" -> extractZipEntriesMatching(file) { name ->
                    name.startsWith("ppt/slides/slide") && name.endsWith(".xml")
                }
                "xlsx" -> extractXlsxText(file)
                "epub" -> extractEpubText(file)
                "rtf" -> extractRtfText(file)
                else -> {
                    if (mimeType.startsWith("text/") || extension in TEXT_CODE_EXTENSIONS) {
                        file.readText(Charsets.UTF_8)
                    } else {
                        "Binary file attached (${file.length()} bytes, type $mimeType)."
                    }
                }
            }
        }.getOrElse { err ->
            "Could not extract text (${err.message ?: "unsupported format"})."
        }
    }

    private fun extractXmlFromZip(file: File, targetEntries: Set<String>): String {
        val sb = StringBuilder()
        ZipInputStream(file.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name in targetEntries) {
                    sb.append(parseXmlTextNodes(zis))
                    sb.append("\n")
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString().trim()
    }

    private fun extractZipEntriesMatching(file: File, predicate: (String) -> Boolean): String {
        val chunks = mutableListOf<Pair<String, String>>()
        ZipInputStream(file.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && predicate(entry.name)) {
                    chunks.add(entry.name to parseXmlTextNodes(zis))
                }
                entry = zis.nextEntry
            }
        }
        return chunks.sortedBy { it.first }
            .joinToString("\n\n---\n\n") { (name, text) ->
                "[${name.substringAfterLast('/')}]\n$text"
            }
            .trim()
    }

    private fun extractXlsxText(file: File): String {
        val sharedStrings = mutableListOf<String>()
        val sheets = mutableListOf<Pair<String, String>>()
        ZipInputStream(file.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        val text = parseXmlTextNodes(zis)
                        sharedStrings.addAll(text.lines().filter { it.isNotBlank() })
                    }
                    entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") -> {
                        sheets.add(entry.name to parseXmlTextNodes(zis))
                    }
                }
                entry = zis.nextEntry
            }
        }
        return buildString {
            if (sharedStrings.isNotEmpty()) {
                append("Spreadsheet Text Values:\n")
                append(sharedStrings.joinToString(" | "))
                append("\n\n")
            }
            sheets.sortedBy { it.first }.forEach { (name, content) ->
                if (content.isNotBlank()) {
                    append("[${name.substringAfterLast('/')}]\n")
                    append(content.take(8000))
                    append("\n\n")
                }
            }
        }.trim()
    }

    private fun extractEpubText(file: File): String {
        val chapters = mutableListOf<Pair<String, String>>()
        ZipInputStream(file.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val lower = entry.name.lowercase(Locale.US)
                if (!entry.isDirectory && (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm"))) {
                    val raw = zis.readBytes().toString(Charsets.UTF_8)
                    val clean = raw.replace(HTML_TAG_REGEX, " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (clean.isNotBlank()) {
                        chapters.add(entry.name to clean)
                    }
                }
                entry = zis.nextEntry
            }
        }
        return chapters.sortedBy { it.first }
            .joinToString("\n\n") { it.second }
            .trim()
    }

    private fun extractRtfText(file: File): String {
        val raw = file.readText(Charsets.UTF_8)
        return raw
            .replace("\\par", "\n")
            .replace("\\line", "\n")
            .replace(RTF_CTRL_REGEX, "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun parseXmlTextNodes(inputStream: InputStream): String {
        val sb = StringBuilder()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.TEXT -> {
                    val t = parser.text?.trim()
                    if (!t.isNullOrEmpty()) {
                        sb.append(t).append(' ')
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.lowercase(Locale.US).orEmpty()
                    if (tag.endsWith(":p") || tag == "p" || tag == "br" || tag == "row" || tag == "tr") {
                        sb.append('\n')
                    }
                }
            }
            event = parser.next()
        }
        return sb.toString().replace(Regex(" +\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun classifyKind(mimeType: String, ext: String): AttachmentKind {
        val lowerMime = mimeType.lowercase(Locale.US)
        return when {
            lowerMime == "image/gif" || ext == "gif" -> AttachmentKind.GIF
            lowerMime == "image/webp" || ext == "webp" -> AttachmentKind.STICKER
            lowerMime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "heic", "bmp") -> AttachmentKind.IMAGE
            lowerMime == "application/pdf" || ext == "pdf" -> AttachmentKind.PDF
            else -> AttachmentKind.DOCUMENT
        }
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "epub" -> "application/epub+zip"
            "rtf" -> "application/rtf"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "csv" -> "text/csv"
            else -> "text/plain"
        }
    }

    private val TEXT_CODE_EXTENSIONS = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "jsonl", "xml", "yaml", "yml",
        "html", "htm", "css", "js", "jsx", "ts", "tsx", "kt", "kts", "java", "py",
        "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "sh", "bash", "sql",
        "toml", "ini", "cfg", "conf", "log", "env", "gradle", "swift", "dart",
    )
}
