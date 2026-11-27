package app.aidroid.remaster.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebResearchEngineTest {
    @Test
    fun parsesDuckDuckGoSearchResultsAndUnwrapsTargetUrl() {
        val html = """
            <div class="result">
              <a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Farticle">Example article</a>
              <a class="result__snippet">A useful summary &amp; details.</a>
            </div>
        """.trimIndent()

        val result = WebHtmlParser.parseDuckDuckGo(html).single()

        assertEquals("Example article", result.title)
        assertEquals("https://example.com/article", result.url)
        assertEquals("A useful summary & details.", result.snippet)
    }

    @Test
    fun parsesMojeekResults() {
        val html = """
            <a class="ob" href="https://example.com/page">Example page</a>
            <p class="s">Page description</p>
        """.trimIndent()

        val result = WebHtmlParser.parseMojeek(html).single()
        assertEquals("https://example.com/page", result.url)
        assertEquals("Page description", result.snippet)
    }

    @Test
    fun extractsMainArticleAndRemovesScriptsNavigationAndFooter() {
        val html = """
            <html><head><title>Article &amp; facts</title></head>
            <body>
              <nav>Navigation must not appear</nav>
              <main><h1>Heading</h1><p>Useful <b>article</b> text.</p>
              <script>secretScript()</script></main>
              <footer>Footer must not appear</footer>
            </body></html>
        """.trimIndent()

        val (title, content) = WebHtmlParser.extractPage(html)

        assertEquals("Article & facts", title)
        assertTrue(content.contains("Useful article text."))
        assertFalse(content.contains("secretScript"))
        assertFalse(content.contains("Navigation must not appear"))
        assertFalse(content.contains("Footer must not appear"))
    }

    @Test
    fun rejectsLocalAndNonHttpUrls() {
        assertFalse(WebResearchEngine.isSafeUrlSyntax("http://127.0.0.1:8080/"))
        assertFalse(WebResearchEngine.isSafeUrlSyntax("http://localhost/"))
        assertFalse(WebResearchEngine.isSafeUrlSyntax("http://192.168.1.1/"))
        assertFalse(WebResearchEngine.isSafeUrlSyntax("file:///etc/passwd"))
        assertTrue(WebResearchEngine.isSafeUrlSyntax("https://example.com/article"))
    }
}
