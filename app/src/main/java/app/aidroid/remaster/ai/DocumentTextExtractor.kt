package app.aidroid.remaster.ai

import android.content.Context
import app.aidroid.remaster.data.attachments.AttachmentStorage
import app.aidroid.remaster.data.attachments.StoredAttachment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentTextExtractor(context: Context, private val storage: AttachmentStorage) {
    private val appContext = context.applicationContext

    suspend fun extract(attachment: StoredAttachment): String = withContext(Dispatchers.IO) {
        val file = storage.resolveFile(attachment)
        require(file.length() <= MAX_DOCUMENT_BYTES) { "Документ превышает лимит 25 МБ." }
        val extension = attachment.displayName.substringAfterLast('.', "").lowercase()
        val extracted = when {
            extension == "pdf" || attachment.mimeType == "application/pdf" -> extractPdf(file)
            extension == "zip" || attachment.mimeType in ZIP_MIME_TYPES -> extractZipArchive(file)
            extension in OFFICE_EXTENSIONS -> extractOffice(file, extension)
            extension in TEXT_EXTENSIONS || attachment.mimeType.startsWith("text/") -> {
                val text = readText(file)
                if (extension in HTML_EXTENSIONS || attachment.mimeType.equals("text/html", ignoreCase = true)) {
                    extractHtmlText(text)
                } else {
                    text
                }
            }
            else -> throw IllegalArgumentException(
                "Формат .$extension пока не поддерживается для чтения. Поддерживаются ZIP (текстовые файлы внутри), PDF, DOCX, XLSX, PPTX, TXT, MD, CSV, JSON, XML и HTML."
            )
        }
        extracted.trim().take(MAX_EXTRACTED_CHARS)
    }

    private fun readText(file: File): String =
        file.inputStream().bufferedReader(Charsets.UTF_8).use { it.readText().take(MAX_EXTRACTED_CHARS) }

    private fun extractZipArchive(file: File): String =
        ZipFile(file).use { zip ->
            val candidates = zip.entries().asSequence()
                .filter { !it.isDirectory && isSupportedArchiveTextFile(it.name) }
                .take(MAX_ARCHIVE_ENTRIES)
                .toList()
            require(candidates.isNotEmpty()) {
                "В ZIP не найдено поддерживаемых текстовых файлов. Можно прочитать TXT, MD, CSV, JSON, XML, HTML и исходный код."
            }

            var totalBytesRead = 0
            buildString {
                for (entry in candidates) {
                    if (length >= MAX_EXTRACTED_CHARS || totalBytesRead >= MAX_ARCHIVE_TOTAL_BYTES) break
                    if (entry.size > MAX_ARCHIVE_ENTRY_BYTES) continue
                    val remainingBytes = minOf(
                        MAX_ARCHIVE_ENTRY_BYTES,
                        MAX_ARCHIVE_TOTAL_BYTES - totalBytesRead
                    )
                    val bytes = zip.getInputStream(entry).use { input ->
                        readBoundedArchiveEntry(input, remainingBytes)
                    } ?: continue
                    totalBytesRead += bytes.size
                    if (bytes.isEmpty() || !looksLikeText(bytes)) continue
                    val rawText = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
                    val extension = entry.name.substringAfterLast('.', "").lowercase()
                    val text = if (extension in HTML_EXTENSIONS) extractHtmlText(rawText) else rawText
                    if (text.isBlank()) continue

                    if (isNotEmpty()) append("\n\n")
                    append("[Файл в архиве: ")
                        .append(entry.name.replace(Regex("[\\r\\n\\p{Cntrl}]"), " ").take(180))
                        .append("]\n")
                    append(text.take((MAX_EXTRACTED_CHARS - length).coerceAtLeast(0)))
                }
            }.also { extracted ->
                require(extracted.isNotBlank()) {
                    "В ZIP найдены файлы, но внутри нет читаемого текста."
                }
            }
        }

    private fun isSupportedArchiveTextFile(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in ARCHIVE_TEXT_EXTENSIONS
    }

    private fun readBoundedArchiveEntry(input: InputStream, maxBytes: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 8_192))
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > maxBytes) return null
            output.write(buffer, 0, count)
        }
    }

    private fun looksLikeText(bytes: ByteArray): Boolean {
        val sampleSize = minOf(bytes.size, 4_096)
        if (sampleSize == 0) return false
        val controlBytes = bytes.take(sampleSize).count { value ->
            val code = value.toInt() and 0xff
            code == 0 || (code < 32 && code !in setOf(9, 10, 13))
        }
        return controlBytes <= sampleSize / 20
    }

    private fun extractHtmlText(input: String): String {
        var text = input
        text = Regex("""(?is)<!--.*?-->""").replace(text, " ")
        text = Regex("""(?is)<(script|style|noscript|template|svg)\b[^>]*>.*?</\1\s*>""")
            .replace(text, " ")
        text = Regex("""(?i)<br\s*/?>|</(p|div|section|article|main|h[1-6]|li|tr|blockquote)>""")
            .replace(text, "\n")
        text = Regex("""(?i)<li\b[^>]*>""").replace(text, "• ")
        text = Regex("""(?is)<[^>]+>""").replace(text, " ")
        return decodeEntities(text)
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun decodeEntities(value: String): String {
        val named = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
            "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—",
            "hellip" to "…", "copy" to "©", "reg" to "®"
        )
        return Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""").replace(value) { match ->
            val entity = match.groupValues[1]
            when {
                entity.startsWith("#x", true) -> runCatching {
                    String(Character.toChars(entity.substring(2).toInt(16)))
                }.getOrDefault(match.value)
                entity.startsWith("#") -> runCatching {
                    String(Character.toChars(entity.substring(1).toInt()))
                }.getOrDefault(match.value)
                else -> named[entity.lowercase()] ?: match.value
            }
        }
    }

    private fun extractPdf(file: File): String {
        PDFBoxResourceLoader.init(appContext)
        PDDocument.load(file).use { document ->
            require(!document.isEncrypted) { "PDF защищён паролем и не может быть прочитан." }
            require(document.numberOfPages <= MAX_PDF_PAGES) {
                "В PDF слишком много страниц. Максимум для одного анализа: $MAX_PDF_PAGES."
            }
            val stripper = PDFTextStripper()
            return buildString {
                for (page in 1..document.numberOfPages) {
                    if (length >= MAX_EXTRACTED_CHARS) break
                    stripper.startPage = page
                    stripper.endPage = page
                    val pageText = stripper.getText(document).trim()
                    if (pageText.isNotBlank()) {
                        append("[Страница ").append(page).append("]\n")
                        append(pageText).append("\n\n")
                    }
                }
            }.take(MAX_EXTRACTED_CHARS)
        }
    }

    private fun extractOffice(file: File, extension: String): String =
        ZipFile(file).use { zip ->
            when (extension) {
                "docx" -> {
                    val entry = zip.getEntry("word/document.xml")
                        ?: throw IllegalArgumentException("В DOCX не найден основной текст документа.")
                    zip.getInputStream(entry).use { stream ->
                        extractXmlBlocks(stream, "p").joinToString("\n").take(MAX_EXTRACTED_CHARS)
                    }
                }
                "pptx" -> {
                    val slides = zip.entries().asSequence()
                        .filter { it.name.matches(Regex("""ppt/slides/slide[0-9]+\.xml""")) }
                        .sortedBy { it.name.substringAfter("slide").substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
                        .take(MAX_OFFICE_ENTRIES)
                        .toList()
                    require(slides.isNotEmpty()) { "В PPTX не найдено слайдов с текстом." }
                    buildString {
                        slides.forEachIndexed { index, entry ->
                            if (length >= MAX_EXTRACTED_CHARS) return@forEachIndexed
                            val paragraphs = zip.getInputStream(entry).use { extractXmlBlocks(it, "p") }
                            if (paragraphs.isNotEmpty()) {
                                append("[Слайд ").append(index + 1).append("]\n")
                                append(paragraphs.joinToString("\n")).append("\n\n")
                            }
                        }
                    }.take(MAX_EXTRACTED_CHARS)
                }
                "xlsx" -> extractWorkbook(zip)
                else -> throw IllegalArgumentException("Этот формат Office не поддерживается.")
            }
        }

    private fun extractWorkbook(zip: ZipFile): String {
        val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            zip.getInputStream(entry).use { extractXmlBlocks(it, "si") }
        }.orEmpty()
        val sheets = zip.entries().asSequence()
            .filter { it.name.matches(Regex("""xl/worksheets/sheet[0-9]+\.xml""")) }
            .sortedBy { it.name.substringAfter("sheet").substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
            .take(MAX_OFFICE_ENTRIES)
            .toList()
        require(sheets.isNotEmpty()) { "В XLSX не найдено листов с данными." }

        return buildString {
            sheets.forEachIndexed { index, entry ->
                if (length >= MAX_EXTRACTED_CHARS) return@forEachIndexed
                val rows = zip.getInputStream(entry).use { extractWorksheetRows(it, sharedStrings) }
                if (rows.isNotEmpty()) {
                    append("[Лист ").append(index + 1).append("]\n")
                    append(rows.joinToString("\n")).append("\n\n")
                }
            }
        }.take(MAX_EXTRACTED_CHARS)
    }

    private fun extractWorksheetRows(stream: InputStream, sharedStrings: List<String>): List<String> {
        val parser = newParser(stream)
        val rows = mutableListOf<String>()
        var rowNumber = ""
        var cellReference: String? = null
        var cellType: String? = null
        var cellText = StringBuilder()
        var readingCellValue = false
        var rowCells = mutableListOf<String>()
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && rows.sumOf { it.length } < MAX_EXTRACTED_CHARS) {
            when (event) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "row" -> {
                        rowNumber = parser.getAttributeValue(null, "r").orEmpty()
                        rowCells = mutableListOf()
                    }
                    "c" -> {
                        cellReference = parser.getAttributeValue(null, "r")
                        cellType = parser.getAttributeValue(null, "t")
                        cellText = StringBuilder()
                    }
                    "v", "t" -> if (cellReference != null) readingCellValue = true
                }
                XmlPullParser.TEXT -> if (cellReference != null && readingCellValue) {
                    parser.text?.let(cellText::append)
                }
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "v", "t" -> readingCellValue = false
                    "c" -> {
                        val raw = cellText.toString().trim()
                        val value = when (cellType) {
                            "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        if (cellReference != null && value.isNotBlank()) {
                            val column = cellReference!!.takeWhile { it.isLetter() }
                            rowCells += "$column: $value"
                        }
                        cellReference = null
                        cellType = null
                        readingCellValue = false
                    }
                    "row" -> if (rowCells.isNotEmpty()) {
                        val prefix = rowNumber.takeIf { it.isNotBlank() }?.let { "Строка $it: " }.orEmpty()
                        rows += prefix + rowCells.joinToString(" | ")
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun extractXmlBlocks(stream: InputStream, blockTag: String): List<String> {
        val parser = newParser(stream)
        val blocks = mutableListOf<String>()
        var depth = 0
        var block = StringBuilder()
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && blocks.sumOf { it.length } < MAX_EXTRACTED_CHARS) {
            when (event) {
                XmlPullParser.START_TAG -> if (localName(parser.name) == blockTag) {
                    if (depth == 0) block = StringBuilder()
                    depth++
                }
                XmlPullParser.TEXT -> if (depth > 0) {
                    parser.text?.takeIf { it.isNotBlank() }?.let { block.append(it).append(' ') }
                }
                XmlPullParser.END_TAG -> if (localName(parser.name) == blockTag && depth > 0) {
                    depth--
                    if (depth == 0) {
                        val value = block.toString().replace(Regex("""\s+"""), " ").trim()
                        if (value.isNotBlank()) blocks += value
                    }
                }
            }
            event = parser.next()
        }
        return blocks
    }

    private fun newParser(stream: InputStream): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(stream, "UTF-8")
        }

    private fun localName(name: String?): String =
        name?.substringAfter(':').orEmpty()

    companion object {
        private const val MAX_DOCUMENT_BYTES = 25L * 1024L * 1024L
        private const val MAX_EXTRACTED_CHARS = 24_000
        private const val MAX_PDF_PAGES = 35
        private const val MAX_OFFICE_ENTRIES = 200
        private const val MAX_ARCHIVE_ENTRIES = 100
        private const val MAX_ARCHIVE_ENTRY_BYTES = 512 * 1024
        private const val MAX_ARCHIVE_TOTAL_BYTES = 3 * 1024 * 1024
        private val ZIP_MIME_TYPES = setOf("application/zip", "application/x-zip-compressed")
        private val ARCHIVE_TEXT_EXTENSIONS = setOf(
            "txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log",
            "kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "gradle",
            "properties", "yaml", "yml", "toml", "sql", "sh", "css"
        )
        private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log")
        private val HTML_EXTENSIONS = setOf("html", "htm")
        private val OFFICE_EXTENSIONS = setOf("docx", "xlsx", "pptx")
    }
}
