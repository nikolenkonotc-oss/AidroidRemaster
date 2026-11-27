package app.aidroid.remaster.ai

import app.aidroid.remaster.data.ChatRepository
import app.aidroid.remaster.data.local.ChatDao
import kotlinx.serialization.json.buildJsonObject

data class AgentRunResult(
    val success: Boolean,
    val message: String
)

class AgentRunner(
    private val providers: ProviderManager,
    private val chatDao: ChatDao,
    private val chatRepository: ChatRepository,
    private val providerClient: ProviderHttpClient = ProviderHttpClient()
) {
    suspend fun respondToChat(chatId: Long): AgentRunResult {
        return try {
            val config = providers.activeProvider()
                ?: return saveResult(
                    chatId,
                    false,
                    "Подключи ИИ-провайдера в разделе «Модели», выбери модель и назначь её активной."
                )
            val apiKey = providers.apiKey(config.id)
            if (config.type != ProviderType.OLLAMA && apiKey.isNullOrBlank()) {
                return saveResult(chatId, false, "Для подключения «${config.name}» нужен API-ключ. Добавь его в настройках провайдера.")
            }

            val preferences = providers.agentPreferences()
            val savedMessages = chatDao.getMessagesForInference(chatId)
            val history = mutableListOf(
                LlmMessage(
                    role = "system",
                    content = buildString {
                        append("Ты — AI-агент Aidroid Remaster. Отвечай на языке пользователя. ")
                        append("Будь точным, не выдумывай результаты инструментов. ")
                        append("Вызывай доступные инструменты, когда они помогают решить задачу. ")
                        if (preferences.systemPrompt.isNotBlank()) {
                            append("\n\nДополнительные инструкции пользователя:\n")
                            append(preferences.systemPrompt.take(4000))
                        }
                    }
                )
            )
            savedMessages.takeLast(MAX_CONTEXT_MESSAGES).forEach { message ->
                if (message.role == "user" || message.role == "assistant") {
                    val content = if (message.attachmentsJson != "[]") {
                        message.content + "\n\n[К сообщению прикреплены файлы. Анализ содержимого вложений пока не подключён.]"
                    } else message.content
                    history += LlmMessage(message.role, content)
                }
            }

            val availableTools = BuiltInTools.definitions(preferences.enabledTools.toSet())
            repeat(MAX_TOOL_ROUNDS) {
                val response = providerClient.generate(
                    config = config,
                    apiKey = apiKey,
                    request = LlmRequest(
                        model = config.model,
                        messages = history.toList(),
                        tools = availableTools,
                        reasoningMode = preferences.reasoningMode,
                        supportsReasoning = config.supportsReasoning
                    )
                )
                if (response.toolCalls.isEmpty()) {
                    val answer = response.text.ifBlank { "Модель вернула пустой ответ. Попробуй переформулировать запрос." }
                    return saveResult(chatId, true, answer)
                }

                history += LlmMessage("assistant", response.text, toolCalls = response.toolCalls)
                response.toolCalls.forEach { call ->
                    val toolResult = runCatching {
                        require(availableTools.any { it.name == call.name }) {
                            "Инструмент отключён или не зарегистрирован."
                        }
                        BuiltInTools.execute(call.name, call.arguments)
                    }.fold(
                        onSuccess = { it },
                        onFailure = { error -> "Ошибка инструмента: ${error.message ?: "неизвестная ошибка"}" }
                    )
                    history += LlmMessage(
                        role = "tool",
                        content = toolResult.take(MAX_TOOL_RESULT_CHARS),
                        toolCallId = call.id,
                        name = call.name
                    )
                }
            }

            saveResult(
                chatId,
                false,
                "Достигнут предел из $MAX_TOOL_ROUNDS циклов вызова инструментов. Уточни задачу или отключи лишние инструменты."
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            saveResult(
                chatId,
                false,
                "Не удалось получить ответ от ИИ: ${error.message ?: "ошибка соединения"}. Проверь интернет, адрес сервера, выбранную модель и настройки API."
            )
        }
    }

    private suspend fun saveResult(chatId: Long, success: Boolean, message: String): AgentRunResult {
        chatRepository.addAssistantMessage(chatId, message)
        return AgentRunResult(success, message)
    }

    private companion object {
        const val MAX_CONTEXT_MESSAGES = 40
        const val MAX_TOOL_ROUNDS = 5
        const val MAX_TOOL_RESULT_CHARS = 6000
    }
}
