package app.aidroid.remaster.ui

internal sealed interface MarkdownMessageBlock {
    data class Heading(val text: String, val level: Int) : MarkdownMessageBlock
    data class Paragraph(val text: String) : MarkdownMessageBlock
    data class Code(val language: String, val text: String) : MarkdownMessageBlock
    data class Bullet(val text: String) : MarkdownMessageBlock
    data class Numbered(val number: String, val text: String) : MarkdownMessageBlock
    data class Quote(val text: String) : MarkdownMessageBlock
    data object Divider : MarkdownMessageBlock
}

/**
 * A small, predictable Markdown block parser for chat messages.
 * It deliberately supports common conversational formatting without
 * trying to interpret raw HTML or execute embedded content.
 */
internal fun parseMarkdownMessage(source: String): List<MarkdownMessageBlock> {
    if (source.isBlank()) return emptyList()

    val result = mutableListOf<MarkdownMessageBlock>()
    val paragraph = mutableListOf<String>()
    val quote = mutableListOf<String>()
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
    var fence: String? = null
    var codeLanguage = ""
    val codeLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            val text = paragraph.joinToString(" ").trim()
            if (text.isNotBlank()) result += MarkdownMessageBlock.Paragraph(text)
            paragraph.clear()
        }
    }

    fun flushQuote() {
        if (quote.isNotEmpty()) {
            result += MarkdownMessageBlock.Quote(quote.joinToString(" ").trim())
            quote.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        val fenceMatch = Regex("^(```+|~~~+)(.*)$").matchEntire(trimmed)

        if (fence != null) {
            if (fenceMatch != null && fenceMatch.groupValues[1].first() == fence.first()) {
                result += MarkdownMessageBlock.Code(codeLanguage, codeLines.joinToString("\n"))
                fence = null
                codeLanguage = ""
                codeLines.clear()
            } else {
                codeLines += line
            }
            continue
        }

        if (fenceMatch != null) {
            flushParagraph()
            flushQuote()
            fence = fenceMatch.groupValues[1]
            codeLanguage = fenceMatch.groupValues[2].trim().take(24)
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            flushQuote()
            continue
        }

        val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
        if (heading != null) {
            flushParagraph()
            flushQuote()
            result += MarkdownMessageBlock.Heading(
                text = heading.groupValues[2].trim(),
                level = heading.groupValues[1].length
            )
            continue
        }

        if (Regex("^(---+|___+|\\*\\*\\*+)$").matches(trimmed)) {
            flushParagraph()
            flushQuote()
            result += MarkdownMessageBlock.Divider
            continue
        }

        val quoteMatch = Regex("^>\\s?(.*)$").matchEntire(trimmed)
        if (quoteMatch != null) {
            flushParagraph()
            quote += quoteMatch.groupValues[1]
            continue
        }
        flushQuote()

        val bullet = Regex("^[-*+]\\s+(.+)$").matchEntire(trimmed)
        if (bullet != null) {
            flushParagraph()
            result += MarkdownMessageBlock.Bullet(bullet.groupValues[1])
            continue
        }

        val numbered = Regex("^(\\d{1,3})[.)]\\s+(.+)$").matchEntire(trimmed)
        if (numbered != null) {
            flushParagraph()
            result += MarkdownMessageBlock.Numbered(
                number = numbered.groupValues[1],
                text = numbered.groupValues[2]
            )
            continue
        }

        paragraph += trimmed
    }

    flushParagraph()
    flushQuote()
    if (fence != null) {
        result += MarkdownMessageBlock.Code(codeLanguage, codeLines.joinToString("\n"))
    }
    return result
}
