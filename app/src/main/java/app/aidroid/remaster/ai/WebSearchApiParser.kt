package app.aidroid.remaster.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Normalizes structured search API responses into the app's single source format.
 * Result URLs are validated before they are ever exposed to the agent.
 */
internal object WebSearchApiParser {
    fun parseTavily(response: JsonObject, limit: Int = 8): List<WebSearchHit> =
        response["results"]?.jsonArray.orEmpty().mapNotNull { item ->
            val row = item.jsonObject
            val title = row.string("title").trim()
            val url = row.string("url").trim()
            val content = row.string("content")
                .replace(Regex("""\\s+"""), " ")
                .trim()
            val publishedAt = row.string("published_date").trim()
            if (title.isBlank() || !WebResearchEngine.isSafeUrlSyntax(url)) return@mapNotNull null
            WebSearchHit(
                title = title.take(300),
                url = url,
                snippet = content.take(900),
                source = "Tavily",
                publishedAt = publishedAt.take(100)
            )
        }.distinctBy { it.url }.take(limit)

    fun parseBrave(response: JsonObject, limit: Int = 8): List<WebSearchHit> =
        response["web"]?.jsonObject?.get("results")?.jsonArray.orEmpty().mapNotNull { item ->
            val row = item.jsonObject
            val title = row.string("title").trim()
            val url = row.string("url").trim()
            val snippet = row.string("description").replace(Regex("""\\s+"""), " ").trim()
            val age = row.string("age").trim()
            if (title.isBlank() || !WebResearchEngine.isSafeUrlSyntax(url)) return@mapNotNull null
            WebSearchHit(
                title = title.take(300),
                url = url,
                snippet = snippet.take(900),
                source = "Brave Search",
                publishedAt = age.take(100)
            )
        }.distinctBy { it.url }.take(limit)

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
}
