package app.aidroid.remaster.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMessageParserTest {

    @Test
    fun separatesParagraphsAndHeadings() {
        val blocks = parseMarkdownMessage("# Заголовок\n\nПервый абзац.\n\nВторой абзац.")
        assertEquals(
            listOf(
                MarkdownMessageBlock.Heading("Заголовок", 1),
                MarkdownMessageBlock.Paragraph("Первый абзац."),
                MarkdownMessageBlock.Paragraph("Второй абзац.")
            ),
            blocks
        )
    }

    @Test
    fun parsesListsQuotesAndDividers() {
        val blocks = parseMarkdownMessage(
            "> Важное замечание\n\n- первый пункт\n- второй пункт\n\n1. шаг один\n2) шаг два\n\n---"
        )
        assertEquals(MarkdownMessageBlock.Quote("Важное замечание"), blocks[0])
        assertEquals(MarkdownMessageBlock.Bullet("первый пункт"), blocks[1])
        assertEquals(MarkdownMessageBlock.Bullet("второй пункт"), blocks[2])
        assertEquals(MarkdownMessageBlock.Numbered("1", "шаг один"), blocks[3])
        assertEquals(MarkdownMessageBlock.Numbered("2", "шаг два"), blocks[4])
        assertEquals(MarkdownMessageBlock.Divider, blocks[5])
    }

    @Test
    fun preservesCodeBlockContentAndLanguage() {
        val blocks = parseMarkdownMessage("Пример:\n\n```kotlin\nval answer = 42\nprintln(answer)\n```")
        val code = blocks.filterIsInstance<MarkdownMessageBlock.Code>().single()
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42\nprintln(answer)", code.text)
    }

    @Test
    fun keepsUnclosedCodeFenceAsCodeInsteadOfBreakingMessage() {
        val blocks = parseMarkdownMessage("```sh\necho ready")
        val code = blocks.single() as MarkdownMessageBlock.Code
        assertEquals("sh", code.language)
        assertEquals("echo ready", code.text)
    }

    @Test
    fun plainTextStillProducesReadableParagraphs() {
        val blocks = parseMarkdownMessage("Обычный текст без markdown.\nПродолжение той же мысли.")
        assertTrue(blocks.single() is MarkdownMessageBlock.Paragraph)
        assertEquals(
            "Обычный текст без markdown. Продолжение той же мысли.",
            (blocks.single() as MarkdownMessageBlock.Paragraph).text
        )
    }
}
