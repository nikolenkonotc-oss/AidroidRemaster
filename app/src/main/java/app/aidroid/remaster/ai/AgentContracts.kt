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
    val supportsReasoning: Boolean = false,
    // OpenAI-compatible chat APIs do not necessarily implement the Images API.
    val supportsImageApi: Boolean = false
)

@Serializable
data class AgentPreferences(
    val activeProviderId: String? = null,
    val reasoningMode: ReasoningMode = ReasoningMode.BALANCED,
    val enabledTools: List<String> = listOf(
        "calculator",
        "current_datetime",
        "web_search",
        "image_analysis",
        "file_analysis",
        "image_generation",
        "code_execution",
        "voice_input",
        "voice_output"
    ),
    val systemPrompt: String = "",
    val codeRunnerUrl: String = "",
    // Null means choose the active image-capable provider, then direct OpenAI.
    val imageProviderId: String? = null,
    val imageGenerationModel: String = "gpt-image-2.6-flare",
    val imageEditModel: String = "gpt-image-2.6-sunburst"
)

data class LlmImageAttachment(
    val name: String,
    val mimeType: String,
    val base64: String
)

data class LlmMessage(
    val role: String,
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
    val providerContent: JsonArray? = null,
    val images: List<LlmImageAttachment> = emptyList()
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

enum class AgentActionType {
    CODE_EXECUTION
}

data class AgentExternalAction(
    val id: String,
    val chatId: Long,
    val type: AgentActionType,
    val title: String,
    val subject: String = "",
    val body: String = "",
    val stdin: String = ""
)

data class AgentToolResult(
    val text: String,
    val attachments: List<app.aidroid.remaster.data.attachments.StoredAttachment> = emptyList(),
    val action: AgentExternalAction? = null
)
