package app.aidroid.remaster.ai

import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
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
    val snippet: String,
    val source: String = "",
    val publishedAt: String = ""
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

    fun search(
        query: String,
        freshness: String = "any",
        mwmblApiKey: String? = null
    ): List<WebSearchHit> {
        AgentSafetyPolicy.validateSearchQuery(query)
        require(query.isNotBlank() && query.length <= 600) {
            "Поисковый запрос должен содержать от 1 до 600 символов."
        }

        val found = linkedMapOf<String, WebSearchHit>()
        val errors = mutableListOf<String>()

        // Mwmbl is the structured-JSON primary provider. Stop here when it gives
        // a useful set: this avoids spending requests on HTML-scraping fallbacks.
        val mwmblAttempt = runCatching { searchMwmbl(query, mwmblApiKey) }
        val mwmblHits = mwmblAttempt.getOrElse {
            errors += "Mwmbl: ${it.message}"
            emptyList()
        }
        mwmblHits.forEach { found.putIfAbsent(it.url, it) }
        if (mwmblHits.isEmpty() && mwmblAttempt.isSuccess) {
            errors += "Mwmbl: API не вернул подходящих веб-результатов"
        }
        if (found.size >= 5 && freshness == "any") return found.values.take(8)

        // Legacy providers are fallback-only. They are intentionally not queried
        // when the primary API already returned enough usable results.
        runCatching { searchDuckDuckGo(query, freshness) }
            .onSuccess { hits ->
                hits.forEach { found.putIfAbsent(it.url, it) }
                if (hits.isEmpty()) errors += "DuckDuckGo: страница не содержала распознанных результатов"
            }
            .onFailure { errors += "DuckDuckGo: ${it.message}" }

        if (found.size < 5) {
            runCatching { searchMojeek(query) }
                .onSuccess { hits ->
                    hits.forEach { found.putIfAbsent(it.url, it) }
                    if (hits.isEmpty()) errors += "Mojeek: страница не содержала распознанных результатов"
                }
                .onFailure { errors += "Mojeek: ${it.message}" }
        }

        val needsSupplement = found.size < 5 || freshness != "any"
        val newsHits = if (needsSupplement) {
            runCatching { searchGoogleNews(query, freshness) }
                .onFailure { errors += "Google News RSS: ${it.message}" }
                .getOrElse { emptyList() }
        } else emptyList()
        newsHits.forEach { found.putIfAbsent(it.url, it) }
        if (newsHits.isEmpty() && needsSupplement) {
            errors += "Google News RSS: лента не вернула подходящих материалов"
        }

        if (found.size < 5 && freshness == "any") {
            runCatching { searchWikipedia(query) }
                .onSuccess { hits ->
                    hits.forEach { found.putIfAbsent(it.url, it) }
                    if (hits.isEmpty()) errors += "Wikipedia: справочный поиск не нашёл страниц"
                }
                .onFailure { errors += "Wikipedia: ${it.message}" }
        }

        if (found.isEmpty()) {
            val detail = errors.take(4).joinToString("; ").ifBlank { "источники не вернули результатов" }
            throw IOException("Поиск не получил результатов. Возможны ограничения API, DNS или CAPTCHA. Подробности: $detail")
        }
        val ordered = if (freshness != "any") newsHits + found.values else found.values.toList()
        return ordered.distinctBy { it.url }.take(8)
    }

    @Synchronized
    private fun searchMwmbl(query: String, apiKey: String?): List<WebSearchHit> {
        val now = System.currentTimeMillis()
        val waitMillis = (1_050L - (now - lastMwmblRequestAt)).coerceAtLeast(0L)
        if (waitMillis > 0) Thread.sleep(waitMillis)
        lastMwmblRequestAt = System.currentTimeMillis()

        val url = "https://api.mwmbl.org/api/v2/search/?q=" +
            URLEncoder.encode(query, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .apply { if (!apiKey.isNullOrBlank()) header("X-API-Key", apiKey.trim()) }
            .get()
            .build()

        searchClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (body.length > MAX_SEARCH_BYTES) throw IOException("ответ Mwmbl превышает лимит размера")
            if (response.code == 429) {
                throw IOException("HTTP 429: Mwmbl ограничил частоту запросов или исчерпан месячный лимит")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            if (body.isBlank()) throw IOException("Mwmbl вернул пустой ответ")
            return WebHtmlParser.parseMwmblSearch(body)
        }
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

    private fun searchWikipedia(query: String): List<WebSearchHit> {
        val language = Locale.getDefault().language
            .takeIf { it.matches(Regex("[a-z]{2,3}")) }
            ?: "en"
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://$language.wikipedia.org/w/api.php?action=query&list=search" +
            "&srsearch=$encodedQuery&format=json&utf8=1&srlimit=6"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AidroidRemaster/0.2 (Android; MediaWiki search client)")
            .header("Accept", "application/json")
            .get()
            .build()
        searchClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.length > MAX_SEARCH_BYTES) throw IOException("ответ слишком большой")
            return WebHtmlParser.parseWikipediaSearch(body, language)
        }
    }

    private fun searchGoogleNews(query: String, freshness: String): List<WebSearchHit> {
        val locale = Locale.getDefault()
        val language = locale.language.ifBlank { "en" }
        val country = locale.country.ifBlank { "US" }.uppercase(Locale.ROOT)
        val languageTag = locale.toLanguageTag().takeIf { it.isNotBlank() && it != "und" }
            ?: "$language-$country"
        val freshnessFilter = when (freshness) {
            "day" -> " when:1d"
            "week" -> " when:7d"
            "month" -> " when:30d"
            "year" -> " when:1y"
            else -> ""
        }
        val encodedQuery = URLEncoder.encode(query + freshnessFilter, Charsets.UTF_8.name())
        val url = "https://news.google.com/rss/search?q=$encodedQuery" +
            "&hl=${URLEncoder.encode(languageTag, Charsets.UTF_8.name())}" +
            "&gl=$country&ceid=$country:${URLEncoder.encode(language, Charsets.UTF_8.name())}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml,application/xml,text/xml")
            .header("Accept-Language", "${language},en;q=0.8")
            .get()
            .build()
        return WebHtmlParser.parseGoogleNewsRss(executeSearchHtml(request))
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
        @Volatile private var lastMwmblRequestAt: Long = 0L
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
            val rawHost = uri.host?.removePrefix("[")?.removeSuffix("]")
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
        // Prefer Android's configured DNS, but don't treat a resolver outage as a
        // permanent search outage. Every returned address is still checked below.
        val systemAddresses = runCatching { Dns.SYSTEM.lookup(hostname) }
            .getOrDefault(emptyList())
            .filter(::isPublicAddress)
        if (systemAddresses.isNotEmpty()) return systemAddresses

        val dohAddresses = runCatching { DnsOverHttpsResolver.lookup(hostname) }
            .getOrElse { error ->
                throw UnknownHostException(
                    "Системный DNS не разрешил домен, а резервный DNS-over-HTTPS тоже недоступен: ${error.message ?: "ошибка DNS"}"
                )
            }
            .filter(::isPublicAddress)

        if (dohAddresses.isEmpty()) {
            throw UnknownHostException("Для домена не найден публичный IP-адрес. Проверь DNS и доступность интернета.")
        }
        return dohAddresses
    }
}

/**
 * DNS-over-HTTPS fallback with pinned resolver bootstrap IPs.
 * TLS still validates the resolver hostname; the pinned IPs are only used to
 * reach the resolver when the device's ordinary DNS is unavailable.
 */
private object DnsOverHttpsResolver {
    private data class Resolver(
        val hostname: String,
        val endpoint: String,
        val bootstrapAddresses: List<String>
    )

    private val resolvers = listOf(
        Resolver(
            hostname = "cloudflare-dns.com",
            endpoint = "https://cloudflare-dns.com/dns-query",
            bootstrapAddresses = listOf("1.1.1.1", "1.0.0.1")
        ),
        Resolver(
            hostname = "dns.google",
            endpoint = "https://dns.google/resolve",
            bootstrapAddresses = listOf("8.8.8.8", "8.8.4.4")
        )
    )

    private val clients = resolvers.associate { resolver ->
        resolver.hostname to OkHttpClient.Builder()
            .dns(BootstrapDns(resolver.hostname, resolver.bootstrapAddresses))
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }

    fun lookup(hostname: String): List<InetAddress> {
        val failures = mutableListOf<String>()
        for (type in listOf(1, 28)) {
            for (resolver in resolvers) {
                val result = runCatching { query(resolver, hostname, type) }
                val addresses = result.getOrNull().orEmpty().filter(::isPublicAddress)
                if (addresses.isNotEmpty()) return addresses.distinctBy { it.hostAddress }
                result.exceptionOrNull()?.let { failures += "${resolver.hostname}: ${it.message}" }
            }
        }
        val detail = failures.firstOrNull()?.take(180)
        throw UnknownHostException(
            "Публичный DNS-over-HTTPS не вернул адрес${detail?.let { " ($it)" } ?: ""}."
        )
    }

    private fun query(resolver: Resolver, hostname: String, type: Int): List<InetAddress> {
        val encodedHost = URLEncoder.encode(hostname, Charsets.UTF_8.name())
        val url = "${resolver.endpoint}?name=$encodedHost&type=$type"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .header("User-Agent", "AidroidRemaster/1.0")
            .get()
            .build()

        val client = clients.getValue(resolver.hostname)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            return PublicDnsAnswerParser.parse(body, type)
        }
    }

    private class BootstrapDns(
        private val resolverHost: String,
        private val addresses: List<String>
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname.trimEnd('.').equals(resolverHost, ignoreCase = true)) {
                return addresses.map { parseNumericAddress(it) }
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }
}

internal object PublicDnsAnswerParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, expectedType: Int): List<InetAddress> {
        val root = json.parseToJsonElement(body).jsonObject
        val status = root["Status"]?.jsonPrimitive?.intOrNull
        if (status != null && status != 0) return emptyList()

        return root["Answer"]?.jsonArray.orEmpty().mapNotNull { item ->
            val answer = item.jsonObject
            if (answer["type"]?.jsonPrimitive?.intOrNull != expectedType) return@mapNotNull null
            val raw = answer["data"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val isExpectedAddress = when (expectedType) {
                1 -> raw.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}"""))
                28 -> raw.contains(':')
                else -> false
            }
            if (!isExpectedAddress) return@mapNotNull null
            runCatching { parseNumericAddress(raw) }
                .getOrNull()
                ?.takeIf(::isPublicAddress)
        }.distinctBy { it.hostAddress }
    }
}

private fun parseNumericAddress(value: String): InetAddress {
    require(value.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}""")) || value.contains(':')) {
        "DNS вернул некорректный IP-адрес."
    }
    return InetAddress.getByName(value)
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
    fun parseMwmblSearch(jsonBody: String, limit: Int = 8): List<WebSearchHit> {
        val root = Json.parseToJsonElement(jsonBody).jsonObject
        val rows = root["results"]?.jsonArray.orEmpty()
        val hits = linkedMapOf<String, WebSearchHit>()
        rows.forEach { item ->
            val row = item.jsonObject
            fun textField(name: String): String = row[name]?.jsonPrimitive?.contentOrNull.orEmpty()
            val title = textField("title").trim()
            val url = textField("url").trim()
            val snippet = (textField("content").ifBlank { textField("extract") })
                .replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            val engine = textField("engine").ifBlank { "mwmbl" }
            if (title.isBlank() || !WebResearchEngine.isSafeUrlSyntax(url)) return@forEach
            val source = if (engine.equals("mwmbl", ignoreCase = true)) "Mwmbl" else "Mwmbl ($engine)"
            hits.putIfAbsent(url, WebSearchHit(title.take(300), url, snippet.take(900), source))
            if (hits.size >= limit) return hits.values.toList()
        }
        return hits.values.toList()
    }

    fun parseDuckDuckGo(html: String, limit: Int = 8): List<WebSearchHit> =
        parseResultAnchors(html, "result__a", "result__snippet", limit, "DuckDuckGo")

    fun parseMojeek(html: String, limit: Int = 8): List<WebSearchHit> =
        parseResultAnchors(html, "ob", "s", limit, "Mojeek")

    fun parseWikipediaSearch(jsonBody: String, language: String, limit: Int = 8): List<WebSearchHit> {
        val root = Json.parseToJsonElement(jsonBody).jsonObject
        val rows = root["query"]?.jsonObject?.get("search")?.jsonArray.orEmpty()
        return rows.mapNotNull { item ->
            val row = item.jsonObject
            val title = row["title"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val pageId = row["pageid"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null
            val snippet = stripHtml(row["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty()).take(900)
            val url = "https://$language.wikipedia.org/?curid=$pageId"
            WebSearchHit(title.take(300), url, snippet, "Wikipedia ($language)")
        }.take(limit)
    }

    fun parseGoogleNewsRss(xml: String, limit: Int = 8): List<WebSearchHit> {
        val itemPattern = Regex("""(?is)<item\b[^>]*>(.*?)</item\s*>""")
        val results = linkedMapOf<String, WebSearchHit>()
        itemPattern.findAll(xml).forEach { match ->
            val item = match.groupValues[1]
            fun tag(name: String): String {
                val raw = Regex("""(?is)<$name\b[^>]*>(.*?)</$name\s*>""")
                    .find(item)?.groupValues?.get(1).orEmpty().trim()
                val unwrapped = raw.removePrefix("<![CDATA[").removeSuffix("]]>").trim()
                return unwrapped
            }
            val title = WebHtmlParser.decodeFeedText(tag("title")).take(300)
            val url = WebHtmlParser.decodeFeedText(tag("link")).trim()
            val snippet = WebHtmlParser.stripFeedHtml(WebHtmlParser.decodeFeedText(tag("description"))).take(900)
            val publishedAt = WebHtmlParser.decodeFeedText(tag("pubDate")).take(100)
            if (title.isBlank() || !WebResearchEngine.isSafeUrlSyntax(url)) return@forEach
            results.putIfAbsent(url, WebSearchHit(title, url, snippet, "Google News", publishedAt))
            if (results.size >= limit) return results.values.toList()
        }
        return results.values.toList()
    }

    private fun parseResultAnchors(
        html: String,
        linkClass: String,
        snippetClass: String,
        limit: Int,
        source: String
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
            hits.putIfAbsent(target, WebSearchHit(title, target, snippet, source))
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
            .replace(Regex("""\s+([,.;:!?])"""), "$1")
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

    internal fun decodeFeedText(input: String): String =
        decodeHtml(input.removePrefix("<![CDATA[").removeSuffix("]]>").trim())

    internal fun stripFeedHtml(input: String): String = stripHtml(input)

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
