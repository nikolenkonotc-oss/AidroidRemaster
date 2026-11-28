package app.aidroid.remaster.ai

import android.app.Activity
import android.app.IntentService
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Runs commands inside the user's Termux installation.
 *
 * Ordinary commands run through the Termux RUN_COMMAND bridge. Explicit su -c
 * commands run in the Aidroid process so KernelSU grants root to Aidroid rather
 * than to the separate Termux package. Every privileged or mutating action
 * requires an in-app confirmation; destructive partition operations are blocked.
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
        "termux-battery-status", "termux-wifi-connectioninfo"
    )

    fun definitions(enabled: Set<String>): List<LlmTool> {
        if ("termux_command" !in enabled) return emptyList()
        return listOf(
            LlmTool(
                name = "termux_run_command",
                description = "Выполняет обычные команды через Termux и возвращает stdout, stderr и код завершения в чат. Команда вида su -c '...' запускается отдельно из Aidroid через root-разрешение KernelSU и всегда требует подтверждения Aidroid. ADB-команды запускаются через Termux и также требуют подтверждения. Без подтверждения выполняется только ограниченный read-only список, если включён termux_auto_safe. Простые зависания ограничены тайм-аутом; не утверждай успех без полученного результата.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("command") {
                            put("type", "string")
                            put("description", "Команда, например pwd, ls -la /storage/emulated/0, su -c 'ls -la /system/etc' или adb shell getprop.")
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
            // ADB and root always require an explicit in-app approval, even for
            // diagnostic commands. They are intentionally absent from readOnlyCommands.
            else -> true
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
            "root" -> "Выполнить root-команду через KernelSU?"
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
        if (clean.startsWith("su ", ignoreCase = true) ||
            clean.equals("su", ignoreCase = true)
        ) {
            return executeRootCommand(clean)
        }
        ensureTermuxInstalled(context)
        val result = executeTermux(
            context = context,
            arguments = listOf("-c", clean)
        )
        return result.asToolResult(clean)
    }

    /**
     * Extracts the payload from the explicit "su -c ..." form. The Android
     * su binary is then launched by Aidroid itself, so KernelSU authorizes the
     * Aidroid package instead of Termux.
     */
    internal fun rootCommandPayload(command: String): String {
        val clean = command.trim()
        val prefix = Regex("""(?is)^su\s+(?:-c|--command)\s+""")
            .find(clean)
            ?: throw IllegalArgumentException("Для root используй формат: su -c 'команда'.")
        val payload = clean.substring(prefix.range.last + 1).trim()
        val unquoted = if (
            payload.length >= 2 &&
            ((payload.first() == '\'' && payload.last() == '\'') ||
                (payload.first() == '"' && payload.last() == '"'))
        ) payload.substring(1, payload.length - 1) else payload
        require(unquoted.isNotBlank()) { "После su -c не указана команда." }
        return unquoted
    }

    private suspend fun executeRootCommand(command: String): AgentToolResult = withContext(Dispatchers.IO) {
        val payload = rootCommandPayload(command)
        val process = try {
            ProcessBuilder("su", "-c", payload)
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            throw IllegalStateException(
                "Не удалось запустить su из Aidroid. Проверь, что root-доступ для Aidroid разрешён в KernelSU.",
                error
            )
        }

        val output = ByteArrayOutputStream()
        val reader = Thread {
            process.inputStream.use { stream ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    synchronized(output) {
                        val remaining = MAX_OUTPUT_CHARS - output.size()
                        if (remaining > 0) output.write(buffer, 0, minOf(count, remaining))
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "AidroidRootOutput"
            start()
        }

        val finished = process.waitFor(ROOT_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            reader.join(1_000)
            throw IllegalStateException(
                "Root-команда не завершилась за $ROOT_COMMAND_TIMEOUT_SECONDS секунд. Процесс остановлен; проверь запрос KernelSU и повтори с более короткой командой."
            )
        }

        reader.join(2_000)
        val outputText = synchronized(output) { output.toString("UTF-8").trim() }
        val exitCode = process.exitValue()
        val result = buildString {
            appendLine("Root через KernelSU: $command")
            appendLine("Код завершения: $exitCode")
            if (outputText.isNotBlank()) {
                appendLine("Вывод:")
                append(outputText)
            } else {
                append("Команда завершилась без вывода.")
            }
        }
        AgentToolResult(result.takeLast(MAX_OUTPUT_CHARS))
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
            Regex("""\brm\b[^\n;]*\s-(?:[^\s]*r[^\s]*|--recursive)(?:\s|$)"""),
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
        val info = try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        } catch (_: Exception) {
            throw IllegalStateException("Termux не установлен или Android не видит пакет com.termux.")
        }

        if (context.checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException(
                "У Aidroid нет разрешения RUN_COMMAND. Открой Android → Приложения → Aidroid → Разрешения → Дополнительные разрешения → «Запуск команд в Termux»."
            )
        }

        val version = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?""")
            .find(info.versionName.orEmpty())
            ?.groupValues
            ?.drop(1)
            ?.mapNotNull { it.toIntOrNull() }
        if (version == null || version[0] == 0 && (version.getOrElse(1) { 0 } < 109)) {
            throw IllegalStateException(
                "Нужен Termux версии 0.109 или новее для возврата результата команды. Обнови Termux и повтори запрос."
            )
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
            // Use the current explicit AppShell runner instead of the deprecated
            // RUN_COMMAND_BACKGROUND compatibility flag.
            putExtra("com.termux.RUN_COMMAND_RUNNER", "app-shell")
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Aidroid Remaster")
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

        val commandLine = arguments.joinToString(" ")
        val timeout = if (Regex("(^|\\s)adb(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(commandLine)) {
            45_000L
        } else {
            TERMUX_COMMAND_TIMEOUT_MS
        }
        return try {
            withTimeout(timeout) { deferred.await() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            throw IllegalStateException(
                "Termux не вернул результат за ${timeout / 1000} секунд. Проверь allow-external-apps=true, разрешение RUN_COMMAND и работу Termux. Команда не считается успешно выполненной без результата.",
                error
            )
        } finally {
            pendingResults.remove(executionId)
            callback.cancel()
        }
    }

    private const val TERMUX_COMMAND_TIMEOUT_MS = 30_000L
    private const val ROOT_COMMAND_TIMEOUT_SECONDS = 30L

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
