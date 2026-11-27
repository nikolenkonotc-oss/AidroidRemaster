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
        assertEquals("DuckDuckGo", result.source)
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
        assertEquals("Mojeek", result.source)
    }

    @Test
    fun parsesWikipediaSearchApiResults() {
        val json = """
            {
              "query": {
                "search": [
                  {
                    "pageid": 12345,
                    "title": "Grand Theft Auto VI",
                    "snippet": "An upcoming <span class="searchmatch">video game</span>."
                  }
                ]
              }
            }
        """.trimIndent()

        val result = WebHtmlParser.parseWikipediaSearch(json, "en").single()

        assertEquals("Grand Theft Auto VI", result.title)
        assertEquals("https://en.wikipedia.org/?curid=12345", result.url)
        assertEquals("An upcoming video game.", result.snippet)
        assertEquals("Wikipedia (en)", result.source)
    }

    @Test
    fun parsesGoogleNewsRssItemsAndCdata() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss><channel>
              <item>
                <title><![CDATA[GTA VI news &amp; updates]]></title>
                <link>https://news.google.com/rss/articles/example?source=feed&amp;id=7</link>
                <description><![CDATA[<p>Latest <b>confirmed</b> details &amp; context.</p>]]></description>
                <pubDate>Sat, 28 Nov 2026 10:30:00 GMT</pubDate>
              </item>
              <item>
                <title>Unsafe local result</title>
                <link>http://127.0.0.1/private</link>
                <description>Must be ignored</description>
              </item>
            </channel></rss>
        """.trimIndent()

        val result = WebHtmlParser.parseGoogleNewsRss(xml).single()

        assertEquals("GTA VI news & updates", result.title)
        assertEquals("https://news.google.com/rss/articles/example?source=feed&id=7", result.url)
        assertEquals("Latest confirmed details & context.", result.snippet)
        assertEquals("Google News", result.source)
        assertEquals("Sat, 28 Nov 2026 10:30:00 GMT", result.publishedAt)
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
