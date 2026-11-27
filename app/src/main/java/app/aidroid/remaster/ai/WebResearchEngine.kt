package app.aidroid.remaster.ai

import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WebSearchHit(
    val title: String,
    val url: String,
    val snippet: String
)

data class WebPageContent(
    val url: String,
    val title: String,
    val description: String,
    val contentType: String,
    val text: String
)

/**
 * Keyless web search and page extraction.
 *
 * Search uses public HTML result pages, not paid APIs. Those pages are not stable APIs and
 * may throttle automated requests, so providers are tried independently and failures are
 * reported instead of fabricating results.
 */
class WebResearchEngine {
    private val searchClient = OkHttpClient.Builder()
        .dns(PublicOnlyDns)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val pageClient = OkHttpClient.Builder()
        .dns(PublicOnlyDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun search(query: String, freshness: String = "any"): List<WebSearchHit> {
        AgentSafetyPolicy.validateSearchQuery(query)
        require(query.isNotBlank() && query.length <= 600) { "Поисковый запрос должен содержать от 1 до 600 символов." }

        val found = linkedMapOf<String, WebSearchHit>()
        val errors = mutableListOf<String>()

        runCatching { searchDuckDuckGo(query, freshness) }
            .onSuccess { hits -> hits.forEach { found.putIfAbsent(it.url, it) } }
            .onFailure { errors += "DuckDuckGo: ${it.message}" }

        if (found.size < 5) {
            runCatching { searchMojeek(query) }
                .onSuccess { hits -> hits.forEach { found.putIfAbsent(it.url, it) } }
                .onFailure { errors += "Mojeek: ${it.message}" }
        }

        if (found.isEmpty()) {
            val detail = errors.take(2).joinToString("; ").ifBlank { "источники не вернули результатов" }
            throw IOException("Бесплатные поисковые источники сейчас недоступны ($detail). Попробуй ещё раз позже.")
        }
        return found.values.take(8)
    }

    fun openPage(input: String): WebPageContent {
        require(input.length <= 2_048) { "Ссылка слишком длинная." }
        var current = validateUri(URI(input.trim()))
        repeat(MAX_REDIRECTS + 1) { hop ->
            val request = Request.Builder()
                .url(current.toASCIIString())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json,application/xml;q=0.9,*/*;q=0.1")
                .header("Accept-Language", "en-US,en;q=0.8,ru;q=0.6")
                .get()
                .build()

            var redirect: URI? = null
            var page: WebPageContent? = null
            pageClient.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    val location = response.header("Location")
                        ?: throw IOException("Сайт вернул перенаправление без адреса.")
                    redirect = current.resolve(location)
                } else {
                    if (!response.isSuccessful) {
                        throw IOException("Сайт вернул HTTP ${response.code}.")
                    }
                    val body = response.body ?: throw IOException("Сайт вернул пустой ответ.")
                    val type = body.contentType()?.let { it.toString().substringBefore(';').lowercase(Locale.ROOT) }
                        ?: response.header("Content-Type").orEmpty().substringBefore(';').lowercase(Locale.ROOT)
                    val supported = type in SUPPORTED_CONTENT_TYPES || type.isBlank()
                    if (!supported) {
                        throw IOException("Формат $type пока не поддерживается. Можно читать HTML и обычный текст.")
                    }
                    if (body.contentLength() > MAX_PAGE_BYTES) {
                        throw IOException("Страница слишком большая для безопасного чтения.")
                    }
                    val bytes = body.byteStream().use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8_192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_PAGE_BYTES) {
                                throw IOException("Страница превышает лимит размера.")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    val raw = String(bytes, charset)
                    val (title, text) = if (type == "text/html" || type == "application/xhtml+xml" || type.isBlank()) {
                        WebHtmlParser.extractPage(raw)
                    } else {
                        Pair("", WebHtmlParser.normalizePlainText(raw))
                    }
                    if (text.isBlank()) throw IOException("На странице не удалось извлечь читаемый текст.")
                    page = WebPageContent(
                        url = current.toASCIIString(),
                        title = title.ifBlank { current.host },
                        description = if (type.contains("html")) WebHtmlParser.extractDescription(raw) else "",
                        contentType = type.ifBlank { "text/html" },
                        text = text.take(MAX_EXTRACTED_TEXT_CHARS)
                    )
                }
            }

            if (page != null) return page!!
            current = validateUri(redirect ?: throw IOException("Не удалось обработать перенаправление."))
            if (hop == MAX_REDIRECTS) throw IOException("Слишком много перенаправлений.")
        }
        throw IOException("Не удалось открыть страницу.")
    }

    private fun searchDuckDuckGo(query: String, freshness: String): List<WebSearchHit> {
        val form = FormBody.Builder()
            .add("q", query)
            .add("kp", "1")
            .apply {
                val range = when (freshness) {
                    "day" -> "d"
                    "week" -> "w"
                    "month" -> "m"
                    "year" -> "y"
                    else -> ""
                }
                if (range.isNotBlank()) add("df", range)
            }
            .build()
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.8,ru;q=0.6")
            .post(form)
            .build()
        val html = executeSearchHtml(request)
        if (html.contains("anomaly.js", ignoreCase = true) ||
            html.contains("select all squares", ignoreCase = true) ||
            html.contains("unfortunately, bots use", ignoreCase = true)
        ) throw IOException("поисковик показал проверку браузера")
        return WebHtmlParser.parseDuckDuckGo(html)
    }

    private fun searchMojeek(query: String): List<WebSearchHit> {
        val url = "https://www.mojeek.com/search?q=" + URLEncoder.encode(query, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.8,ru;q=0.6")
            .get()
            .build()
        val html = executeSearchHtml(request)
        return WebHtmlParser.parseMojeek(html)
    }

    private fun executeSearchHtml(request: Request): String {
        searchClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("пустой ответ")
            if (body.contentLength() > MAX_SEARCH_BYTES) throw IOException("ответ слишком большой")
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_SEARCH_BYTES) throw IOException("ответ слишком большой")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            return String(bytes, charset)
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 4
        private const val MAX_PAGE_BYTES = 1_500_000L
        private const val MAX_SEARCH_BYTES = 1_000_000L
        private const val MAX_EXTRACTED_TEXT_CHARS = 18_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36 AidroidRemaster/1.0"
        private val SUPPORTED_CONTENT_TYPES = setOf(
            "text/html", "application/xhtml+xml", "text/plain", "application/json",
            "application/xml", "text/xml", "application/ld+json"
        )

        internal fun isSafeUrlSyntax(input: String): Boolean =
            runCatching { validateUri(URI(input.trim())) }.isSuccess

        private fun validateUri(uri: URI): URI {
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            require(scheme == "https" || scheme == "http") { "Разрешены только HTTP- и HTTPS-страницы." }
            require(uri.rawUserInfo.isNullOrBlank()) { "Ссылки с логином или паролем запрещены." }
            val rawHost = uri.host?.removePrefix("[").removeSuffix("]")
                ?: throw IllegalArgumentException("В ссылке не найден домен.")
            val host = if (rawHost.contains(':')) rawHost.lowercase(Locale.ROOT)
                else IDN.toASCII(rawHost).lowercase(Locale.ROOT)
            require(uri.port == -1 || uri.port == 80 || uri.port == 443) {
                "Разрешены только стандартные веб-порты 80 и 443."
            }
            require(host != "localhost" && !host.endsWith(".localhost") &&
                !host.endsWith(".local") && !host.endsWith(".internal") &&
                !host.endsWith(".lan") && !host.endsWith(".home") && !host.endsWith(".test")
            ) { "Локальные и внутренние адреса недоступны из веб-инструмента." }

            if (host.contains(':') || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
                val address = InetAddress.getByName(host)
                require(isPublicAddress(address)) { "Частные, локальные и служебные IP-адреса запрещены." }
            }
            return uri.normalize()
        }
    }
}

private object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) }) {
            throw UnknownHostException("Домен указывает на локальный или непубличный IP-адрес.")
        }
        return addresses
    }
}

private fun isPublicAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress ||
        address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress
    ) return false

    val bytes = address.address
    if (address is Inet4Address) {
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        val c = bytes[2].toInt() and 0xff
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false
        if (a == 100 && b in 64..127) return false
        if (a == 169 && b == 254) return false
        if (a == 172 && b in 16..31) return false
        if (a == 192 && (b == 0 || b == 168)) return false
        if (a == 198 && b in 18..19) return false
        if (a == 198 && b == 51 && c == 100) return false
        if (a == 203 && b == 0 && c == 113) return false
    }
    if (address is Inet6Address) {
        val first = bytes[0].toInt() and 0xff
        if ((first and 0xfe) == 0xfc) return false
        if (first == 0x20 && (bytes[1].toInt() and 0xff) == 0x01 &&
            (bytes[2].toInt() and 0xff) == 0x0d && (bytes[3].toInt() and 0xff) == 0xb8
        ) return false
    }
    return true
}

internal object WebHtmlParser {
    fun parseDuckDuckGo(html: String, limit: Int = 8): List<WebSearchHit> =
        parseResultAnchors(html, "result__a", "result__snippet", limit)

    fun parseMojeek(html: String, limit: Int = 8): List<WebSearchHit> =
        parseResultAnchors(html, "ob", "s", limit)

    private fun parseResultAnchors(
        html: String,
        linkClass: String,
        snippetClass: String,
        limit: Int
    ): List<WebSearchHit> {
        val anchors = Regex("""(?is)<a\b([^>]*)>(.*?)</a>""").findAll(html).toList()
        val hits = linkedMapOf<String, WebSearchHit>()
        anchors.forEachIndexed { index, anchor ->
            val attributes = anchor.groupValues[1]
            val classes = attribute(attributes, "class").orEmpty().split(Regex("""\s+"""))
            if (linkClass !in classes) return@forEachIndexed
            val rawHref = attribute(attributes, "href").orEmpty()
            val target = if (linkClass == "result__a") unwrapDuckDuckGo(rawHref) else decodeHtml(rawHref)
            if (!WebResearchEngine.isSafeUrlSyntax(target)) return@forEachIndexed
            val title = stripHtml(anchor.groupValues[2]).take(300)
            if (title.isBlank()) return@forEachIndexed
            val nextResultStart = anchors.drop(index + 1).firstOrNull { next ->
                attribute(next.groupValues[1], "class").orEmpty().split(Regex("""\s+""")).contains(linkClass)
            }?.range?.first ?: html.length
            val tailEnd = minOf(nextResultStart, anchor.range.last + 2_400, html.length)
            val tail = html.substring((anchor.range.last + 1).coerceAtMost(html.length), tailEnd.coerceAtLeast(anchor.range.last + 1))
            val snippet = extractClassText(tail, snippetClass).take(900)
            hits.putIfAbsent(target, WebSearchHit(title, target, snippet))
            if (hits.size >= limit) return hits.values.toList()
        }
        return hits.values.toList()
    }

    fun extractPage(html: String): Pair<String, String> {
        val title = Regex("""(?is)<title\b[^>]*>(.*?)</title>""")
            .find(html)?.groupValues?.get(1)?.let(::stripHtml).orEmpty().take(300)

        val source = listOf("article", "main", "body").firstNotNullOfOrNull { tag ->
            Regex("""(?is)<${tag}\b[^>]*>(.*?)</${tag}\s*>""")
                .find(html)?.groupValues?.get(1)
        } ?: html
        return title to stripHtml(source)
    }

    fun extractDescription(html: String): String {
        val metas = Regex("""(?is)<meta\b([^>]*)>""").findAll(html)
        val match = metas.firstOrNull {
            val attrs = it.groupValues[1]
            attribute(attrs, "name").equals("description", ignoreCase = true) ||
                attribute(attrs, "property").equals("og:description", ignoreCase = true)
        } ?: return ""
        return decodeHtml(attribute(match.groupValues[1], "content").orEmpty()).take(500)
    }

    fun normalizePlainText(input: String): String =
        input.replace("\u0000", "").replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n").trim()

    private fun extractClassText(source: String, token: String): String {
        val elements = Regex("""(?is)<([a-z0-9]+)\b([^>]*)>(.*?)</\1\s*>""")
        val element = elements.findAll(source).firstOrNull { match ->
            attribute(match.groupValues[2], "class").orEmpty().split(Regex("""\s+""")).contains(token)
        } ?: return ""
        return stripHtml(element.groupValues[3])
    }

    private fun attribute(attributes: String, name: String): String? {
        val pattern = Regex("""(?is)\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
        val match = pattern.find(attributes) ?: return null
        return (1..3).firstNotNullOfOrNull { match.groups[it]?.value }?.let(::decodeHtml)
    }

    private fun unwrapDuckDuckGo(href: String): String {
        val decoded = decodeHtml(href)
        val wrapped = Regex("""[?&]uddg=([^&]+)""").find(decoded)?.groupValues?.get(1) ?: return decoded
        return runCatching { URLDecoder.decode(wrapped, Charsets.UTF_8.name()) }.getOrDefault(wrapped)
    }

    private fun stripHtml(input: String): String {
        var source = input
        source = Regex("""(?is)<!--.*?-->""").replace(source, " ")
        source = Regex("""(?is)<(script|style|noscript|template|svg|iframe|nav|footer|header|aside|form|button)\b[^>]*>.*?</\1\s*>""")
            .replace(source, " ")
        source = Regex("""(?i)<br\s*/?>|</(p|div|section|article|main|h[1-6]|li|tr|blockquote)>""")
            .replace(source, "\n")
        source = Regex("""(?i)<li\b[^>]*>""").replace(source, "• ")
        source = Regex("""(?is)<[^>]+>""").replace(source, " ")
        return normalizePlainText(decodeHtml(source))
    }

    private fun decodeHtml(input: String): String {
        val named = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
            "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—",
            "hellip" to "…", "copy" to "©", "reg" to "®"
        )
        return Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""").replace(input) { match ->
            val entity = match.groupValues[1]
            when {
                entity.startsWith("#x", true) -> runCatching {
                    String(Character.toChars(entity.substring(2).toInt(16)))
                }.getOrDefault(match.value)
                entity.startsWith("#") -> runCatching {
                    String(Character.toChars(entity.substring(1).toInt()))
                }.getOrDefault(match.value)
                else -> named[entity.lowercase(Locale.ROOT)] ?: match.value
            }
        }
    }
}
