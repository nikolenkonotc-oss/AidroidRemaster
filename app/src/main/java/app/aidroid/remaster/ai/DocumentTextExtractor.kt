package app.aidroid.remaster.ai

import android.content.Context
import android.util.Xml
import app.aidroid.remaster.data.attachments.AttachmentStorage
import app.aidroid.remaster.data.attachments.StoredAttachment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile

class DocumentTextExtractor(context: Context, private val storage: AttachmentStorage) {
    private val appContext = context.applicationContext

    suspend fun extract(attachment: StoredAttachment): String {
        val file = storage.resolveFile(attachment)
        require(file.length() <= MAX_DOCUMENT_BYTES) { "Документ превышает лимит 25 МБ." }
        val extension = attachment.displayName.substringAfterLast('.', "").lowercase()
        val extracted = when {
            attachment.mimeType.startsWith("text/") -> readText(file)
            extension in TEXT_EXTENSIONS -> readText(file)
            extension == "pdf" || attachment.mimeType == "application/pdf" -> extractPdf(file)
            extension in OFFICE_EXTENSIONS -> extractOffice(file, extension)
            else -> throw IllegalArgumentException(
                "Формат .$extension пока не поддерживается для чтения. Поддерживаются PDF, DOCX, XLSX, PPTX, TXT, MD, CSV, JSON, XML и HTML."
            )
        }
        return extracted.trim().take(MAX_EXTRACTED_CHARS)
    }

    private fun readText(file: File): String =
        file.inputStream().bufferedReader(Charsets.UTF_8).use { it.readText().take(MAX_EXTRACTED_CHARS) }

    private fun extractPdf(file: File): String {
        PDFBoxResourceLoader.init(appContext)
        PDDocument.load(file).use { document ->
            require(!document.isEncrypted) { "PDF защищён паролем и не может быть прочитан." }
            require(document.numberOfPages <= MAX_PDF_PAGES) {
                "В PDF слишком много страниц. Максимум для одного анализа: $MAX_PDF_PAGES."
            }
            return PDFTextStripper().apply {
                startPage = 1
                endPage = document.numberOfPages
            }.getText(document)
        }
    }

    private fun extractOffice(file: File, extension: String): String {
        val output = StringBuilder()
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().filter { entry ->
                when (extension) {
                    "docx" -> entry.name == "word/document.xml"
                    "pptx" -> entry.name.matches(Regex("ppt/slides/slide[0-9]+\\.xml"))
                    "xlsx" -> entry.name == "xl/sharedStrings.xml" ||
                        entry.name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml"))
                    else -> false
                }
            }.sortedBy { it.name }.toList()

            if (entries.isEmpty()) throw IllegalArgumentException("В документе не найдено поддерживаемое содержимое.")
            entries.forEach { entry ->
                if (output.length >= MAX_EXTRACTED_CHARS) return@forEach
                zip.getInputStream(entry).use { stream ->
                    output.append(extractXmlText(stream)).append('\n')
                }
            }
        }
        return output.toString()
    }

    private fun extractXmlText(stream: InputStream): String {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val output = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && output.length < MAX_EXTRACTED_CHARS) {
            if (event == XmlPullParser.TEXT) {
                val value = parser.text
                if (!value.isNullOrBlank()) output.append(value).append(' ')
            }
            event = parser.next()
        }
        return output.toString()
    }

    companion object {
        private const val MAX_DOCUMENT_BYTES = 25L * 1024L * 1024L
        private const val MAX_EXTRACTED_CHARS = 18_000
        private const val MAX_PDF_PAGES = 35
        private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log")
        private val OFFICE_EXTENSIONS = setOf("docx", "xlsx", "pptx")
    }
}
