package app.aidroid.remaster.ai

import android.app.Activity
import android.app.IntentService
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs commands inside the user's Termux installation.
 *
 * Every command is passed as one argument to bash -lc. A small, conservative
 * read-only subset may run without an approval dialog. All other commands,
 * including su and adb, are displayed in full and require confirmation.
 */
object TermuxCommandTools {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr/bin/"
    private const val TERMUX_BASH = TERMUX_PREFIX + "bash"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private const val MAX_COMMAND_CHARS = 8_192
    private const val MAX_OUTPUT_CHARS = 18_000

    private val pendingResults = ConcurrentHashMap<Int, CompletableDeferred<TermuxCommandResult>>()

    private val readOnlyCommands = setOf(
        "pwd", "ls", "df", "du", "stat", "find", "cat", "head", "tail", "grep",
        "getprop", "uname", "id", "whoami", "date", "git", "pkg",
        "termux-battery-status", "termux-wifi-connectioninfo", "adb"
    )

    fun definitions(enabled: Set<String>): List<LlmTool> {
        if ("termux_command" !in enabled) return emptyList()
        return listOf(
            LlmTool(
                name = "termux_run_command",
                description = "Выполняет команду или shell-выражение через bash внутри Termux. Результат stdout, stderr и exit code возвращается в чат. Простые read-only команды из безопасного списка могут выполняться без подтверждения, если пользователь включил termux_auto_safe. Любые неизвестные или изменяющие команды требуют отдельного подтверждения Aidroid. Можно использовать su -c для root, если устройство действительно имеет root и настроен root-менеджер; можно использовать adb shell, если в Termux установлен android-tools и настроено ADB-подключение. Не утверждай, что root или ADB сработали, пока не получен результат.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("command") {
                            put("type", "string")
                            put("description", "Полная команда Termux, например ls -la /storage/emulated/0, su -c 'id' или adb shell getprop.")
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("command")) }
                    put("additionalProperties", false)
                }
            )
        )
    }

    /**
     * Returns true only for one simple command from the allowlisted read-only set.
     * Shell operators, substitutions, scripts, root commands and unrecognized flags
     * always fall back to the explicit approval flow.
     */
    fun isReadOnlyCommand(command: String): Boolean {
        val tokens = runCatching { tokenizeSimpleCommand(command) }.getOrNull() ?: return false
        if (tokens.isEmpty() || tokens.first() !in readOnlyCommands) return false
        val executable = tokens.first()
        val args = tokens.drop(1)
        return when (executable) {
            "find" -> args.none {
                it in setOf("-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprint0", "-fprintf", "-fls") ||
                    it.startsWith("-exec") || it.startsWith("-ok") || it.startsWith("-fprint")
            }
            "pkg" -> args.firstOrNull() in setOf("list-installed", "list-all", "search", "show")
            "git" -> when (args.firstOrNull()) {
                "status", "log", "rev-parse", "show" -> true
                "diff" -> args.none {
                    it == "--ext-diff" || it == "--textconv" ||
                        it == "--output" || it.startsWith("--output=")
                }
                "branch" -> args.none {
                    it in setOf("-d", "-D", "-m", "-M", "-c", "-C", "--delete", "--move", "--copy", "--edit-description") ||
                        it.startsWith("--set-upstream-to")
                }
                else -> false
            }
            "date" -> args.none { it == "-s" || it == "--set" || it.startsWith("--set=") }
            "adb" -> isReadOnlyAdb(args)
            "su" -> {
                if (args.size == 2 && args[0] == "-c") {
                    val inner = tokenizeSimpleCommand(args[1])
                    inner.isNotEmpty() && inner.first() !in setOf("su", "sudo") &&
                        isReadOnlyCommand(args[1])
                } else false
            }
            else -> true
        }
    }

    private fun isReadOnlyAdb(args: List<String>): Boolean {
        return when (args.firstOrNull()) {
            "version", "devices", "get-state", "help" -> true
            "shell" -> {
                val remote = args.drop(1)
                if (remote.isEmpty() || remote.any {
                        it in setOf(";", "&&", "||", "|", ">", ">>", "<", "`", "$")
                    }) {
                    false
                } else {
                    when (remote.firstOrNull()) {
                        "getprop", "id", "whoami", "ls", "df", "du", "mount", "ps" -> true
                        "pm" -> remote.getOrNull(1) == "list"
                        "cmd" -> remote.getOrNull(1) == "package" && remote.getOrNull(2) == "list"
                        "settings" -> remote.getOrNull(1) == "get"
                        "dumpsys" -> remote.getOrNull(1) in setOf("battery", "window", "activity", "display")
                        else -> false
                    }
                }
            }
            else -> false
        }
    }

    fun prepare(arguments: JsonObject, chatId: Long): AgentToolResult {
        val command = arguments["command"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Не указана команда Termux.")
        val clean = validateShellCommand(command)
        val subject = when {
            Regex("^(su|sudo)(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(clean) -> "root"
            Regex("^adb(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(clean) -> "adb"
            else -> "shell"
        }
        val title = when (subject) {
            "root" -> "Выполнить root-команду в Termux?"
            "adb" -> "Выполнить ADB-команду в Termux?"
            else -> "Выполнить команду в Termux?"
        }
        return AgentToolResult(
            text = "Команда ожидает подтверждения.",
            action = AgentExternalAction(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                type = AgentActionType.TERMUX_COMMAND,
                title = title,
                subject = subject,
                body = clean
            )
        )
    }

    suspend fun execute(context: Context, action: AgentExternalAction): AgentToolResult {
        require(action.type == AgentActionType.TERMUX_COMMAND) {
            "Передано действие другого типа в обработчик Termux."
        }
        return executeCommand(context, action.body)
    }

    suspend fun executeCommand(context: Context, command: String): AgentToolResult {
        val clean = validateShellCommand(command)
        ensureTermuxInstalled(context)
        val result = executeTermux(
            context = context,
            arguments = listOf("-lc", clean)
        )
        return result.asToolResult(clean)
    }

    private fun validateShellCommand(command: String): String {
        val clean = command.trim()
        require(clean.isNotBlank() && clean.length <= MAX_COMMAND_CHARS) {
            "Команда должна содержать от 1 до $MAX_COMMAND_CHARS символов."
        }
        require('\u0000' !in clean && clean.none { it.isISOControl() && it !in setOf('\n', '\r', '\t') }) {
            "Команда содержит недопустимые управляющие символы."
        }
        val normalized = clean.lowercase()
        val destructivePatterns = listOf(
            Regex("""\brm\s+-(?:rf|fr)\s+/(?:\s|$)"""),
            Regex("""\bdd\b.*\bof\s*=\s*/dev/block/"""),
            Regex("""\bmkfs(?:\.[a-z0-9]+)?\s+/dev/"""),
            Regex("""\bwipefs\b.*?/dev/"""),
            Regex("""\bfastboot\s+flash\b"""),
            Regex("""\bflash_image\b"""),
            Regex("""\bparted\s+/dev/block/""")
        )
        require(destructivePatterns.none { it.containsMatchIn(normalized) }) {
            "Команда содержит операцию, способную стереть разделы или повредить систему. Такой запуск заблокирован."
        }
        return clean
    }

    private fun tokenizeSimpleCommand(command: String): List<String> {
        val clean = command.trim()
        if (clean.isBlank() || clean.length > 500) return emptyList()
        if (clean.any { it.isISOControl() && it != '\t' }) return emptyList()
        if (clean.any { it in setOf(';', '&', '|', '>', '<', '`', '$', '\n', '\r') }) return emptyList()

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (char in clean) {
            when {
                escaped -> { current.append(char); escaped = false }
                char == '\\' && quote != '\'' -> escaped = true
                quote != null && char == quote -> quote = null
                quote == null && (char == '\'' || char == '"') -> quote = char
                quote == null && char.isWhitespace() -> {
                    if (current.isNotEmpty()) { tokens += current.toString(); current.clear() }
                }
                else -> current.append(char)
            }
        }
        if (escaped || quote != null) return emptyList()
        if (current.isNotEmpty()) tokens += current.toString()
        if (tokens.isEmpty() || tokens.size > 42) return emptyList()
        if (tokens.any { token -> token.length > 256 || token.any { char -> char.isISOControl() } }) return emptyList()
        return tokens
    }

    private fun ensureTermuxInstalled(context: Context) {
        try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        } catch (_: Exception) {
            throw IllegalStateException("Termux не установлен. Установи Termux и повтори запрос.")
        }
    }

    private suspend fun executeTermux(
        context: Context,
        arguments: List<String>
    ): TermuxCommandResult {
        val executionId = uniqueExecutionId()
        val deferred = CompletableDeferred<TermuxCommandResult>()
        pendingResults[executionId] = deferred
        val callbackIntent = Intent(context, TermuxCommandResultService::class.java).apply {
            putExtra("aidroid_execution_id", executionId)
        }
        val callback = PendingIntent.getService(
            context,
            executionId,
            callbackIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments.toTypedArray())
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
        }
        try {
            context.startService(intent)
        } catch (error: SecurityException) {
            pendingResults.remove(executionId)
            callback.cancel()
            throw IllegalStateException(
                "Termux отклонил запуск. Проверь разрешение Aidroid RUN_COMMAND и allow-external-apps=true в ~/.termux/termux.properties.",
                error
            )
        } catch (error: Exception) {
            pendingResults.remove(executionId)
            callback.cancel()
            throw IllegalStateException(
                "Не удалось запустить Termux. Проверь установку приложения и разрешение RUN_COMMAND.",
                error
            )
        }

        val timeout = if (Regex("(^|\\s)(su|sudo)(\\s|$)").containsMatchIn(arguments.joinToString(" "))) 300_000L else 180_000L
        return try {
            withTimeout(timeout) { deferred.await() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            throw IllegalStateException(
                "Termux не вернул результат за ${timeout / 1000} секунд. Если выполнялась su-команда, проверь всплывающий запрос root-менеджера; если adb — проверь ADB-подключение. Команда не считается успешно выполненной без результата.",
                error
            )
        } finally {
            pendingResults.remove(executionId)
            callback.cancel()
        }
    }

    private fun uniqueExecutionId(): Int {
        var id: Int
        do {
            id = UUID.randomUUID().hashCode() and Int.MAX_VALUE
        } while (pendingResults.containsKey(id))
        return id
    }

    fun completeResult(executionId: Int, result: TermuxCommandResult) {
        pendingResults.remove(executionId)?.complete(result)
    }

    private fun TermuxCommandResult.asToolResult(label: String): AgentToolResult {
        if (internalErrorCode != Activity.RESULT_OK) {
            throw IllegalStateException(
                internalErrorMessage.ifBlank { "Termux не смог запустить команду (внутренняя ошибка $internalErrorCode)." }
            )
        }
        val combined = buildString {
            appendLine("Команда: $label")
            appendLine("Код завершения: $exitCode")
            if (stdout.isNotBlank()) { appendLine("STDOUT:"); appendLine(stdout) }
            if (stderr.isNotBlank()) { appendLine("STDERR:"); appendLine(stderr) }
        }.trim()
        return AgentToolResult(
            if (combined.isBlank()) "Команда завершилась без вывода. Код: $exitCode"
            else combined.takeLast(MAX_OUTPUT_CHARS)
        )
    }
}

data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val internalErrorCode: Int,
    val internalErrorMessage: String
)

/**
 * Termux RUN_COMMAND documentation returns results to a Service PendingIntent.
 */
class TermuxCommandResultService : IntentService("TermuxCommandResultService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val id = intent.getIntExtra("aidroid_execution_id", -1)
        if (id < 0) return
        val bundle: Bundle? = intent.getBundleExtra("result")
        if (bundle == null) {
            TermuxCommandTools.completeResult(
                id,
                TermuxCommandResult("", "", -1, 1, "Termux вернул пакет без result Bundle.")
            )
            return
        }
        TermuxCommandTools.completeResult(
            id,
            TermuxCommandResult(
                stdout = bundle.getString("stdout").orEmpty(),
                stderr = bundle.getString("stderr").orEmpty(),
                exitCode = bundle.getInt("exitCode", -1),
                internalErrorCode = bundle.getInt("err", Activity.RESULT_OK),
                internalErrorMessage = bundle.getString("errmsg").orEmpty()
            )
        )
    }
}
