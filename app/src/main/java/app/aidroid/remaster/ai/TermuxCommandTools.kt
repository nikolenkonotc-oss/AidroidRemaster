package app.aidroid.remaster.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Optional Termux bridge. It runs only non-root Termux commands from a strict command
 * allowlist. Commands are passed as executable + argv (never through sh -c), and every
 * invocation requires a visible per-command approval.
 */
object TermuxCommandTools {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr/bin/"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"

    private val allowedCommands = setOf(
        "pwd", "ls", "df", "du", "stat", "find", "cat", "head", "tail", "grep",
        "getprop", "uname", "id", "whoami", "date", "pkg", "git",
        "termux-battery-status", "termux-wifi-connectioninfo"
    )

    fun definitions(enabled: Set<String>): List<LlmTool> {
        if ("termux_command" !in enabled) return emptyList()
        return listOf(
            LlmTool(
                name = "termux_run_command",
                description = "Отправляет одну команду в установленный Termux и открывает его окно с результатом. Поддерживаются только безопасные диагностические/просмотровые команды из списка: pwd, ls, df, du, stat, find, cat, head, tail, grep, getprop, uname, id, whoami, date, pkg (list-installed/list-all/search/show/update), git (status/log/diff/branch/rev-parse/show), termux-battery-status, termux-wifi-connectioninfo. Shell, скрипты, root/su/sudo, удаление, изменение прав и системных настроек заблокированы. Каждая команда показывается пользователю и требует подтверждения.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("command") {
                            put("type", "string")
                            put("description", "Одна команда Termux, например ls -la или df -h.")
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("command")) }
                    put("additionalProperties", false)
                }
            )
        )
    }

    fun prepare(arguments: JsonObject, chatId: Long): AgentToolResult {
        val command = arguments["command"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Не указана команда Termux.")
        val tokens = validateAndTokenize(command)
        return AgentToolResult(
            text = "Команда Termux подготовлена и ожидает подтверждения.",
            action = AgentExternalAction(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                type = AgentActionType.TERMUX_COMMAND,
                title = "Выполнить команду в Termux?",
                subject = tokens.first(),
                body = command.trim()
            )
        )
    }

    fun execute(context: Context, action: AgentExternalAction): AgentToolResult {
        require(action.type == AgentActionType.TERMUX_COMMAND) {
            "Передано действие другого типа в обработчик Termux."
        }
        val tokens = validateAndTokenize(action.body)
        val executable = tokens.first()
        require(executable == action.subject) { "Команда изменилась после подтверждения." }
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_PREFIX + executable)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", tokens.drop(1).toTypedArray())
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }
        return try {
            context.startService(intent)
            AgentToolResult(
                "Команда отправлена в Termux: ${action.body}\nTermux открыл сессию для выполнения. Вывод команды отображается в Termux, а не передаётся обратно в чат."
            )
        } catch (error: SecurityException) {
            throw IllegalStateException(
                "Termux отклонил внешний запуск. Установи Termux из совместимого источника, включи allow-external-apps=true в ~/.termux/termux.properties и выдай Aidroid разрешение RUN_COMMAND.",
                error
            )
        } catch (error: Exception) {
            throw IllegalStateException(
                "Не удалось запустить Termux. Проверь, что приложение Termux установлено и разрешает внешние команды.",
                error
            )
        }
    }

    internal fun validateAndTokenize(command: String): List<String> {
        require(command.isNotBlank() && command.length <= 500) {
            "Команда должна содержать от 1 до 500 символов."
        }
        require(command.none { it.isISOControl() && it != '\t' }) {
            "Переводы строк и управляющие символы в команде запрещены."
        }
        require(command.none { it in setOf(';', '&', '|', '>', '<', '`', '$') }) {
            "Операторы shell, перенаправления и подстановки запрещены. Отправляй одну команду без конвейеров."
        }

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (char in command.trim()) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' && quote != '\'' -> escaped = true
                quote != null && char == quote -> quote = null
                quote == null && (char == '\'' || char == '"') -> quote = char
                quote == null && char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        require(!escaped && quote == null) { "В команде не закрыты кавычки или escape-последовательность." }
        if (current.isNotEmpty()) tokens += current.toString()
        require(tokens.isNotEmpty() && tokens.size <= 42) { "Слишком много аргументов команды." }

        val executable = tokens.first()
        require(executable.matches(Regex("[A-Za-z0-9._-]{1,80}")) && executable in allowedCommands) {
            "Команда '$executable' не входит в безопасный список Termux."
        }
        val args = tokens.drop(1)
        require(args.all { it.length <= 256 && it.none { c -> c.isISOControl() } }) {
            "Аргумент команды слишком длинный или содержит управляющие символы."
        }
        require(args.none { it in setOf(";", "|", "&&", "||", ">", ">>", "<", "`") }) {
            "Shell-операторы в аргументах запрещены."
        }
        when (executable) {
            "find" -> require(args.none {
                it in setOf("-delete", "-exec", "-execdir", "-ok", "-okdir") ||
                    it.startsWith("-exec") || it.startsWith("-ok")
            }) { "Изменяющие файлы параметры find отключены." }
            "pkg" -> require(args.firstOrNull() in setOf("list-installed", "list-all", "search", "show", "update")) {
                "Через агента доступны только просмотр и обновление списка пакетов Termux."
            }
            "git" -> require(args.firstOrNull() in setOf("status", "log", "diff", "branch", "rev-parse", "show")) {
                "Через агента доступны только read-only команды Git."
            }
        }
        return tokens
    }
}
