package app.aidroid.remaster.ai

import okhttp3.Dns
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
 * Safe web-page reader. Search is performed only through configured structured APIs
 * (Tavily, with optional Brave fallback) in AgentToolExecutor.
 */
class WebResearchEngine {
    private val pageClient = OkHttpClient.Builder()
        .dns(PublicOnlyDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

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

    companion object {
        private const val MAX_REDIRECTS = 4
        private const val MAX_PAGE_BYTES = 1_500_000L
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
    fun extractPage(html: String): Pair<String, String> {
        val title = Regex("""(?is)<title\b[^>]*>(.*?)</title>""")
            .find(html)?.groupValues?.get(1)?.let(::stripHtml).orEmpty().take(300)

        val source = listOf("article", "main", "body").firstNotNullOfOrNull { tag ->
            Regex("""(?is)<\${tag}\b[^>]*>(.*?)</\${tag}\s*>""").find(html)?.groupValues?.get(1)
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

    private fun attribute(attributes: String, name: String): String? {
        val pattern = Regex("""(?is)\b\${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
        val match = pattern.find(attributes) ?: return null
        return (1..3).firstNotNullOfOrNull { match.groups[it]?.value }?.let(::decodeHtml)
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
