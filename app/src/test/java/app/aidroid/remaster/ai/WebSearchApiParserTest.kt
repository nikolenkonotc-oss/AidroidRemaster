package app.aidroid.remaster.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchApiParserTest {
    @Test
    fun parsesTavilyResultsAndPublishedDates() {
        val response = Json.parseToJsonElement(
            """
            {
              "results": [
                {
                  "title": "OpenAI",
                  "url": "https://openai.com/",
                  "content": "Official OpenAI website.",
                  "published_date": "2026-11-27"
                },
                {
                  "title": "Unsafe",
                  "url": "http://127.0.0.1/private",
                  "content": "Must be filtered."
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val hits = WebSearchApiParser.parseTavily(response)

        assertEquals(1, hits.size)
        assertEquals("OpenAI", hits.single().title)
        assertEquals("https://openai.com/", hits.single().url)
        assertEquals("Official OpenAI website.", hits.single().snippet)
        assertEquals("2026-11-27", hits.single().publishedAt)
        assertEquals("Tavily", hits.single().source)
    }

    @Test
    fun parsesBraveResultsAndDeduplicatesUrls() {
        val response = Json.parseToJsonElement(
            """
            {
              "web": {
                "results": [
                  {
                    "title": "Brave Search",
                    "url": "https://search.brave.com/",
                    "description": "Search the web.",
                    "age": "2 days ago"
                  },
                  {
                    "title": "Duplicate",
                    "url": "https://search.brave.com/",
                    "description": "Duplicate result."
                  }
                ]
              }
            }
            """.trimIndent()
        ).jsonObject

        val hits = WebSearchApiParser.parseBrave(response)

        assertEquals(1, hits.size)
        assertEquals("Brave Search", hits.single().title)
        assertEquals("Brave Search", hits.single().source)
        assertEquals("2 days ago", hits.single().publishedAt)
    }

    @Test
    fun emptyTavilyOrBraveResponseReturnsNoHits() {
        assertTrue(WebSearchApiParser.parseTavily(Json.parseToJsonElement("""{"results":[]}""").jsonObject).isEmpty())
        assertTrue(WebSearchApiParser.parseBrave(Json.parseToJsonElement("""{"web":{"results":[]}}""").jsonObject).isEmpty())
    }
}
