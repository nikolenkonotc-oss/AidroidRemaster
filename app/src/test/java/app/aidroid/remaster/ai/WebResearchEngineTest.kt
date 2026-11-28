package app.aidroid.remaster.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebResearchEngineTest {
    @Test
    fun extractsMainArticleAndRemovesScriptsNavigationAndFooter() {
        val html = """
            <html><head><title>Article &amp; facts</title>
            <meta name="description" content="A useful description &amp; context."></head>
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
        assertEquals("A useful description & context.", WebHtmlParser.extractDescription(html))
    }

    @Test
    fun normalizesPlainText() {
        assertEquals("A clean sentence.", WebHtmlParser.normalizePlainText("A   clean   sentence ."))
    }

    @Test
    fun parsesDnsOverHttpsAnswersAndFiltersPrivateAddresses() {
        val response = """
            {
              "Status": 0,
              "Answer": [
                {"name":"example.com","type":1,"TTL":60,"data":"1.1.1.1"},
                {"name":"example.com","type":1,"TTL":60,"data":"192.168.1.10"},
                {"name":"example.com","type":1,"TTL":60,"data":"127.0.0.1"},
                {"name":"example.com","type":28,"TTL":60,"data":"2606:4700:4700::1111"}
              ]
            }
        """.trimIndent()

        val ipv4 = PublicDnsAnswerParser.parse(response, expectedType = 1)
        val ipv6 = PublicDnsAnswerParser.parse(response, expectedType = 28)

        assertEquals(listOf("1.1.1.1"), ipv4.map { it.hostAddress })
        assertEquals(1, ipv6.size)
        assertTrue(ipv6.single().hostAddress.contains(":"))
    }

    @Test
    fun returnsNoDnsAnswersForNonZeroResolverStatus() {
        val response = """{"Status":3,"Answer":[{"type":1,"data":"1.1.1.1"}]}"""
        assertTrue(PublicDnsAnswerParser.parse(response, expectedType = 1).isEmpty())
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
