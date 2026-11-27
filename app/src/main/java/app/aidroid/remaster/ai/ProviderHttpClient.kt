package app.aidroid.remaster.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ProviderHttpClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listModels(config: ProviderConfig, apiKey: String?): List<AvailableModel> =
        withContext(Dispatchers.IO) {
            val url = when (config.type) {
                ProviderType.GEMINI -> "${config.baseUrl.trimEnd('/')}/v1beta/models?key=${encode(apiKey.orEmpty())}"
                ProviderType.ANTHROPIC -> "${config.baseUrl.trimEnd('/')}/v1/models"
                ProviderType.OLLAMA -> "${config.baseUrl.trimEnd('/')}/api/tags"
                else -> openAiEndpoint(config.baseUrl, "/models")
            }
            val request = Request.Builder().url(url).apply {
                when (config.type) {
                    ProviderType.GEMINI -> Unit
                    ProviderType.ANTHROPIC -> {
                        header("x-api-key", apiKey.orEmpty())
                        header("anthropic-version", ANTHROPIC_VERSION)
                    }
                    ProviderType.OLLAMA -> if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey")
                    else -> if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey")
                }
            }.get().build()

            val root = execute(request)
            val values = when (config.type) {
                ProviderType.GEMINI -> root["models"]?.jsonArray.orEmpty().mapNotNull { item ->
                    val model = item.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                        ?.removePrefix("models/") ?: return@mapNotNull null
                    val methods = item.jsonObject["supportedGenerationMethods"]?.jsonArray
                    if (methods != null && methods.none { it.jsonPrimitive.contentOrNull == "generateContent" }) null
                    else AvailableModel(model)
                }
                ProviderType.ANTHROPIC -> root["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                    item.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.let { AvailableModel(it) }
                }
                ProviderType.OLLAMA -> root["models"]?.jsonArray.orEmpty().mapNotNull { item ->
                    item.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.let { AvailableModel(it) }
                }
                else -> root["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                    item.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.let { AvailableModel(it) }
                }
            }
            values.sortedBy { it.id.lowercase() }
        }

    suspend fun generate(
        config: ProviderConfig,
        apiKey: String?,
        request: LlmRequest
    ): LlmResponse = withContext(Dispatchers.IO) {
        when (config.type) {
            ProviderType.OPENAI -> openAiResponses(config, apiKey.orEmpty(), request)
            ProviderType.OPENAI_COMPATIBLE, ProviderType.OLLAMA -> openAiChatCompletions(config, apiKey, request)
            ProviderType.ANTHROPIC -> anthropicMessages(config, apiKey.orEmpty(), request)
            ProviderType.GEMINI -> geminiGenerate(config, apiKey.orEmpty(), request)
        }
    }

    private fun openAiResponses(config: ProviderConfig, apiKey: String, request: LlmRequest): LlmResponse {
        val input = buildJsonArray {
            request.messages.forEach { message ->
                if (message.role == "tool") {
                    add(buildJsonObject {
                        put("type", "function_call_output")
                        put("call_id", message.toolCallId.orEmpty())
                        put("output", message.content)
                    })
                } else {
                    if (message.content.isNotBlank() || message.images.isNotEmpty()) {
                        add(buildJsonObject {
                            put("role", message.role)
                            if (message.images.isEmpty()) put("content", message.content)
                            else put("content", buildJsonArray {
                                if (message.content.isNotBlank()) {
                                    add(buildJsonObject {
                                        put("type", "input_text")
                                        put("text", message.content)
                                    })
                                }
                                message.images.forEach { image ->
                                    add(buildJsonObject {
                                        put("type", "input_image")
                                        put("image_url", "data:${image.mimeType};base64,${image.base64}")
                                    })
                                }
                            })
                        })
                    }
                    message.toolCalls.forEach { call ->
                        add(buildJsonObject {
                            put("type", "function_call")
                            put("call_id", call.id)
                            put("name", call.name)
                            put("arguments", Json.encodeToString(call.arguments))
                        })
                    }
                }
            }
        }
        val payload = buildJsonObject {
            put("model", request.model)
            put("input", input)
            put("max_output_tokens", 2048)
            if (request.tools.isNotEmpty()) put("tools", responseTools(request.tools))
            if (config.supportsReasoning) {
                putJsonObject("reasoning") { put("effort", request.reasoningMode.toApiValue()) }
            }
        }
        val response = post(
            openAiEndpoint(config.baseUrl, "/responses"),
            payload,
            mapOf("Authorization" to "Bearer $apiKey")
        )
        val output = response["output"]?.jsonArray.orEmpty()
        val text = buildString {
            output.forEach { item ->
                val obj = item.jsonObject
                if (obj["type"]?.jsonPrimitive?.contentOrNull == "message") {
                    obj["content"]?.jsonArray.orEmpty().forEach { block ->
                        val blockObj = block.jsonObject
                        if (blockObj["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                            append(blockObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        }
                    }
                }
            }
        }
        val calls = output.mapNotNull { item ->
            val obj = item.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "function_call") return@mapNotNull null
            val args = obj["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
            LlmToolCall(
                id = obj["call_id"]?.jsonPrimitive?.contentOrNull
                    ?: obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                arguments = parseObject(args)
            )
        }
        return LlmResponse(text.trim(), calls)
    }

    private fun openAiChatCompletions(
        config: ProviderConfig,
        apiKey: String?,
        request: LlmRequest
    ): LlmResponse {
        val messages = buildJsonArray {
            request.messages.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    if (message.images.isNotEmpty()) {
                        put("content", buildJsonArray {
                            if (message.content.isNotBlank()) {
                                add(buildJsonObject { put("type", "text"); put("text", message.content) })
                            }
                            message.images.forEach { image ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:${image.mimeType};base64,${image.base64}")
                                        put("detail", "auto")
                                    }
                                })
                            }
                        })
                    } else if (message.content.isNotBlank()) {
                        put("content", message.content)
                    }
                    if (message.name != null) put("name", message.name)
                    if (message.toolCallId != null) put("tool_call_id", message.toolCallId)
                    if (message.toolCalls.isNotEmpty()) {
                        putJsonArray("tool_calls") {
                            message.toolCalls.forEach { call ->
                                add(buildJsonObject {
                                    put("id", call.id)
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", call.name)
                                        put("arguments", Json.encodeToString(call.arguments))
                                    }
                                })
                            }
                        }
                    }
                })
            }
        }
        val payload = buildJsonObject {
            put("model", request.model)
            put("messages", messages)
            put("stream", false)
            put("max_tokens", 2048)
            if (request.tools.isNotEmpty()) put("tools", chatTools(request.tools))
            if (config.supportsReasoning) put("reasoning_effort", request.reasoningMode.toApiValue())
        }
        val headers = if (apiKey.isNullOrBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")
        val response = post(openAiEndpoint(config.baseUrl, "/chat/completions"), payload, headers)
        val message = response["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject
            ?: throw IllegalStateException("Провайдер вернул пустой ответ.")
        val content = when (val value = message["content"]) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            is JsonArray -> value.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
            else -> ""
        }
        val calls = message["tool_calls"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.jsonObject
            val function = obj["function"]?.jsonObject ?: return@mapNotNull null
            LlmToolCall(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = function["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                arguments = parseObject(function["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty())
            )
        }
        return LlmResponse(content.trim(), calls)
    }

    private fun anthropicMessages(
        config: ProviderConfig,
        apiKey: String,
        request: LlmRequest
    ): LlmResponse {
        val system = request.messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
        val sourceMessages = request.messages.filterNot { it.role == "system" }
        val messages = buildJsonArray {
            var index = 0
            while (index < sourceMessages.size) {
                val message = sourceMessages[index]
                if (message.role == "tool") {
                    // Anthropic expects tool results in a single user content array after the assistant tool_use.
                    val toolResults = buildJsonArray {
                        while (index < sourceMessages.size && sourceMessages[index].role == "tool") {
                            val result = sourceMessages[index]
                            add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", result.toolCallId.orEmpty())
                                put("content", result.content)
                                if (result.content.startsWith("Ошибка инструмента:")) put("is_error", true)
                            })
                            index++
                        }
                    }
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", toolResults)
                    })
                } else {
                    add(buildJsonObject {
                        put("role", if (message.role == "assistant") "assistant" else "user")
                        when {
                            message.role == "assistant" && message.providerContent != null -> {
                                // Preserve Claude's original thinking/signature blocks across tool-use turns.
                                putJsonArray("content") { message.providerContent?.forEach { add(it) } }
                            }
                            message.role == "assistant" && message.toolCalls.isNotEmpty() -> {
                                putJsonArray("content") {
                                    if (message.content.isNotBlank()) {
                                        add(buildJsonObject { put("type", "text"); put("text", message.content) })
                                    }
                                    message.toolCalls.forEach { call ->
                                        add(buildJsonObject {
                                            put("type", "tool_use")
                                            put("id", call.id)
                                            put("name", call.name)
                                            put("input", call.arguments)
                                        })
                                    }
                                }
                            }
                            else -> {
                                if (message.images.isEmpty()) {
                                    put("content", message.content)
                                } else {
                                    putJsonArray("content") {
                                        if (message.content.isNotBlank()) {
                                            add(buildJsonObject { put("type", "text"); put("text", message.content) })
                                        }
                                        message.images.forEach { image ->
                                            add(buildJsonObject {
                                                put("type", "image")
                                                putJsonObject("source") {
                                                    put("type", "base64")
                                                    put("media_type", image.mimeType)
                                                    put("data", image.base64)
                                                }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    })
                    index++
                }
            }
        }
        val legacyManualThinking = request.model.contains("4-5", ignoreCase = true) ||
            request.model.contains("3-7", ignoreCase = true)
        val payload = buildJsonObject {
            put("model", request.model)
            put("max_tokens", if (request.reasoningMode == ReasoningMode.DEEP) 8192 else 4096)
            if (system.isNotBlank()) put("system", system)
            put("messages", messages)
            if (request.tools.isNotEmpty()) put("tools", anthropicTools(request.tools))
            if (config.supportsReasoning) {
                if (legacyManualThinking) {
                    if (request.reasoningMode == ReasoningMode.DEEP) {
                        putJsonObject("thinking") {
                            put("type", "enabled")
                            put("budget_tokens", 2048)
                        }
                    }
                } else {
                    putJsonObject("thinking") { put("type", "adaptive") }
                    putJsonObject("output_config") {
                        put("effort", request.reasoningMode.toApiValue())
                    }
                }
            }
        }
        val response = post(
            "${config.baseUrl.trimEnd('/')}/v1/messages",
            payload,
            mapOf("x-api-key" to apiKey, "anthropic-version" to ANTHROPIC_VERSION)
        )
        val content = response["content"]?.jsonArray ?: JsonArray(emptyList())
        val text = content.mapNotNull { block ->
            val obj = block.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                obj["text"]?.jsonPrimitive?.contentOrNull
            } else null
        }.joinToString("")
        val calls = content.mapNotNull { block ->
            val obj = block.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "tool_use") return@mapNotNull null
            LlmToolCall(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                arguments = obj["input"]?.jsonObject ?: buildJsonObject { }
            )
        }
        return LlmResponse(text.trim(), calls, providerContent = content)
    }

    private fun geminiGenerate(
        config: ProviderConfig,
        apiKey: String,
        request: LlmRequest
    ): LlmResponse {
        require(apiKey.isNotBlank()) { "Для Gemini нужен API-ключ." }
        val system = request.messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
        val contents = buildJsonArray {
            request.messages.filterNot { it.role == "system" }.forEach { message ->
                val role = if (message.role == "assistant") "model" else "user"
                add(buildJsonObject {
                    put("role", role)
                    when {
                        message.role == "assistant" && message.providerContent != null -> {
                            // Preserve Gemini thoughtSignature and native function-call parts on tool continuation.
                            putJsonArray("parts") { message.providerContent?.forEach { add(it) } }
                        }
                        message.role == "tool" -> {
                            putJsonArray("parts") {
                                add(buildJsonObject {
                                    putJsonObject("functionResponse") {
                                        put("name", message.name ?: "tool")
                                        putJsonObject("response") { put("result", message.content) }
                                    }
                                })
                            }
                        }
                        else -> {
                            putJsonArray("parts") {
                                if (message.content.isNotBlank()) add(buildJsonObject { put("text", message.content) })
                                message.images.forEach { image ->
                                    add(buildJsonObject {
                                        putJsonObject("inlineData") {
                                            put("mimeType", image.mimeType)
                                            put("data", image.base64)
                                        }
                                    })
                                }
                                message.toolCalls.forEach { call ->
                                    add(buildJsonObject {
                                        putJsonObject("functionCall") {
                                            put("name", call.name)
                                            put("args", call.arguments)
                                        }
                                    })
                                }
                            }
                        }
                    }
                })
            }
        }
        val gemini3OrLater = request.model.startsWith("gemini-3", ignoreCase = true) ||
            request.model.startsWith("gemini-4", ignoreCase = true)
        val payload = buildJsonObject {
            if (system.isNotBlank()) putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
            }
            put("contents", contents)
            if (request.tools.isNotEmpty()) put("tools", geminiTools(request.tools))
            if (config.supportsReasoning) putJsonObject("generationConfig") {
                putJsonObject("thinkingConfig") {
                    if (gemini3OrLater) {
                        put("thinkingLevel", when (request.reasoningMode) {
                            ReasoningMode.FAST -> "low"
                            ReasoningMode.BALANCED -> "medium"
                            ReasoningMode.DEEP -> "high"
                        })
                    } else {
                        put("thinkingBudget", when (request.reasoningMode) {
                            ReasoningMode.FAST -> 1024
                            ReasoningMode.BALANCED -> 8192
                            ReasoningMode.DEEP -> 16384
                        })
                    }
                }
            }
        }
        val url = "${config.baseUrl.trimEnd('/')}/v1beta/models/${encode(request.model)}:generateContent?key=${encode(apiKey)}"
        val response = post(url, payload)
        val parts = response["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
        val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
        val calls = parts.mapNotNull { part ->
            val call = part.jsonObject["functionCall"]?.jsonObject ?: return@mapNotNull null
            LlmToolCall(
                id = "gemini-${System.nanoTime()}",
                name = call["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                arguments = call["args"]?.jsonObject ?: buildJsonObject { }
            )
        }
        return LlmResponse(text.trim(), calls, providerContent = parts)
    }

    private fun post(url: String, payload: JsonObject, headers: Map<String, String> = emptyMap()): JsonObject {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            post(Json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
        }.build()
        return execute(request)
    }

    private fun execute(request: Request): JsonObject {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    json.parseToJsonElement(body).jsonObject["error"]?.let { error ->
                        error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                            ?: error.jsonPrimitive.contentOrNull
                    }
                }.getOrNull()
                throw IllegalStateException(
                    detail?.takeIf { it.isNotBlank() }
                        ?: "Ошибка API (${response.code}). Проверь адрес, модель и API-ключ."
                )
            }
            return runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw IllegalStateException("Сервер вернул ответ в неизвестном формате.") }
        }
    }

    private fun chatTools(tools: List<LlmTool>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", ToolSchemaNormalizer.forStrictFunction(tool.parameters))
                }
            })
        }
    }

    private fun responseTools(tools: List<LlmTool>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(buildJsonObject {
                put("type", "function")
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", ToolSchemaNormalizer.forStrictFunction(tool.parameters))
                put("strict", true)
            })
        }
    }

    private fun anthropicTools(tools: List<LlmTool>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("input_schema", ToolSchemaNormalizer.forStrictFunction(tool.parameters))
                put("strict", true)
            })
        }
    }

    private fun geminiTools(tools: List<LlmTool>): JsonArray = buildJsonArray {
        add(buildJsonObject {
            putJsonArray("functionDeclarations") {
                tools.forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", ToolSchemaNormalizer.forFunctionDeclaration(tool.parameters))
                    })
                }
            }
        })
    }

    private fun parseObject(value: String): JsonObject =
        runCatching { json.parseToJsonElement(value).jsonObject }
            .getOrDefault(buildJsonObject { })

    private fun openAiEndpoint(baseUrl: String, path: String): String {
        val root = baseUrl.trimEnd('/')
        return if (root.endsWith("/v1")) "$root$path" else "$root/v1$path"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun ReasoningMode.toApiValue(): String = when (this) {
        ReasoningMode.FAST -> "low"
        ReasoningMode.BALANCED -> "medium"
        ReasoningMode.DEEP -> "high"
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
