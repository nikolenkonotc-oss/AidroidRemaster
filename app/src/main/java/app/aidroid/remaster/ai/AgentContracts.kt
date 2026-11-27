package app.aidroid.remaster.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ProviderType {
    OPENAI,
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
    OLLAMA
}

@Serializable
enum class ReasoningMode(val label: String, val description: String) {
    FAST("Быстро", "Меньше задержка и расход токенов"),
    BALANCED("Сбалансированно", "Оптимальный режим для повседневных задач"),
    DEEP("Глубоко", "Больше ресурсов на сложные задачи")
}

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val model: String,
    val enabled: Boolean = true,
    val supportsReasoning: Boolean = false
)

@Serializable
data class AgentPreferences(
    val activeProviderId: String? = null,
    val reasoningMode: ReasoningMode = ReasoningMode.BALANCED,
    val enabledTools: List<String> = listOf("calculator", "current_datetime"),
    val systemPrompt: String = ""
)

data class LlmMessage(
    val role: String,
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
    val providerContent: JsonArray? = null
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
)

data class LlmTool(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val tools: List<LlmTool>,
    val reasoningMode: ReasoningMode,
    val supportsReasoning: Boolean
)

data class LlmResponse(
    val text: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val providerContent: JsonArray? = null
)

data class AvailableModel(
    val id: String,
    val displayName: String = id
)
