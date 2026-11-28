
package app.aidroid.remaster.ai

import android.app.PendingIntent
import android.content.BroadcastReceiver
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
 * Termux bridge. It never accepts arbitrary shell text: ordinary commands are
 * tokenized and allowlisted. Root mode exposes only bounded config-file reads/writes.
 * Results are returned to the calling coroutine through a one-shot PendingIntent.
 */
object TermuxCommandTools {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr/bin/"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private const val MAX_OUTPUT_CHARS = 12_000
    private const val MAX_ROOT_FILE_BYTES = 64 * 1024
    private const val MAX_ROOT_WRITE_CHARS = 32 * 1024
    private const val ROOT_BACKUP_DIR = "/data/local/tmp/aidroid-backups"

    private val pendingResults = ConcurrentHashMap<Int, CompletableDeferred<TermuxCommandResult>>()
    private val allowedCommands = setOf(
        "pwd", "ls", "df", "du", "stat", "find", "cat", "head", "tail", "grep",
        "getprop", "uname", "id", "whoami", "date", "pkg", "git",
        "termux-battery-status", "termux-wifi-connectioninfo"
    )
    private val configRoots = listOf("/system/etc/", "/vendor/etc/", "/product/etc/", "/odm/etc/")
    private val readableConfigExtensions = setOf("conf", "xml", "prop", "properties", "json", "txt", "rc")
    private val writableConfigExtensions = setOf("conf", "xml", "prop", "properties", "json", "txt")
    private val blockedConfigSegments = setOf(
        "security", "sepolicy", "init", "init.rc", "fstab", "passwd", "shadow",
        "keys", "keymaster", "credentials", "credential", "boot", "recovery", "vold", "ueventd"
    )

    fun definitions(enabled: Set<String>, rootAccessEnabled: Boolean = false): List<LlmTool> {
        if ("termux_command" !in enabled) return emptyList()
        return buildList {
            add(
                LlmTool(
                    name = "termux_run_command",
                    description = "Запускает одну команду в Termux и возвращает stdout, stderr и exit code в чат. Read-only команды из ограниченного списка могут запускаться без подтверждения, если пользователь включил безопасный автозапуск. Разрешены: pwd, ls, df, du, stat, find, cat, head, tail, grep, getprop, uname, id, whoami, date, pkg (list-installed/list-all/search/show/update), git (status/log/diff/branch/rev-parse/show), termux-battery-status, termux-wifi-connectioninfo. Shell-операторы, скрипты, произвольные бинарники, удаление и опасные изменяющие команды запрещены.",
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
            if (rootAccessEnabled) {
                add(
                    LlmTool(
                        name = "termux_root_list_config",
                        description = "Просматривает имена и размеры файлов в некритичной конфигурационной папке Android через su. Разрешены только /system/etc, /vendor/etc, /product/etc и /odm/etc и их подпапки, кроме security/init/keys и других заблокированных зон. Каждый вызов требует подтверждения Aidroid и root-менеджера.",
                        parameters = buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("path") {
                                    put("type", "string")
                                    put("description", "Путь каталога, например /system/etc или /vendor/etc.")
                                }
                            }
                            putJsonArray("required") { add(JsonPrimitive("path")) }
                            put("additionalProperties", false)
                        }
                    )
                )
                add(
                    LlmTool(
                        name = "termux_root_read_config",
                        description = "Читает по прямому запросу пользователя небольшой текстовый конфигурационный файл через уже настроенный su в Termux. Только файлы с разрешёнными расширениями внутри /system/etc, /vendor/etc, /product/etc или /odm/etc. Каждый запрос требует подтверждения Aidroid и root-менеджера. Не используй для секретов, ключей, паролей или загрузочных/security-конфигураций.",
                        parameters = buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("path") {
                                    put("type", "string")
                                    put("description", "Абсолютный путь к некритичному текстовому конфигу, например /system/etc/example.conf.")
                                }
                            }
                            putJsonArray("required") { add(JsonPrimitive("path")) }
                            put("additionalProperties", false)
                        }
                    )
                )
                add(
                    LlmTool(
                        name = "termux_root_write_config",
                        description = "Изменяет по прямому запросу пользователя небольшой конфигурационный файл в разрешённых системных каталогах. Перед записью создаёт резервную копию в /data/local/tmp/aidroid-backups. Каждая запись требует подтверждения Aidroid и root-менеджера. Загрузочные, security, init, fstab, sepolicy, credential, key и другие критичные файлы запрещены; произвольные root-команды недоступны.",
                        parameters = buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("path") {
                                    put("type", "string")
                                    put("description", "Абсолютный путь к некритичному конфигу в /system/etc, /vendor/etc, /product/etc или /odm/etc.")
                                }
                                putJsonObject("content") {
                                    put("type", "string")
                                    put("description", "Полное новое содержимое файла, максимум 32 КБ.")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("path"))
                                add(JsonPrimitive("content"))
                            }
                            put("additionalProperties", false)
                        }
                    )
                )
            }
        }
    }

    fun isReadOnlyCommand(command: String): Boolean {
        val tokens = validateAndTokenize(command)
        val args = tokens.drop(1)
        return when (tokens.first()) {
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
            else -> true
        }
    }

    fun prepare(arguments: JsonObject, chatId: Long): AgentToolResult {
        val command = arguments["command"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Не указана команда Termux.")
        val tokens = validateAndTokenize(command)
        return AgentToolResult(
            text = "Команда Termux ожидает подтверждения.",
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

    fun prepareRootList(arguments: JsonObject, chatId: Long): AgentToolResult {
        val path = validateConfigDirectory(arguments["path"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val payload = buildJsonObject { put("path", path) }.toString()
        return AgentToolResult(
            text = "Просмотр системной конфигурационной папки ожидает подтверждения.",
            action = AgentExternalAction(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                type = AgentActionType.TERMUX_COMMAND,
                title = "Просмотреть системную конфигурационную папку через root?",
                subject = "root_list",
                body = payload
            )
        )
    }

    fun prepareRootRead(arguments: JsonObject, chatId: Long): AgentToolResult {
        val path = validateConfigPath(arguments["path"]?.jsonPrimitive?.contentOrNull.orEmpty(), write = false)
        val payload = buildJsonObject { put("path", path) }.toString()
        return AgentToolResult(
            text = "Чтение системного конфигурационного файла ожидает подтверждения.",
            action = AgentExternalAction(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                type = AgentActionType.TERMUX_COMMAND,
                title = "Прочитать системный конфиг через root?",
                subject = "root_read",
                body = payload
            )
        )
    }

    fun prepareRootWrite(arguments: JsonObject, chatId: Long): AgentToolResult {
        val path = validateConfigPath(arguments["path"]?.jsonPrimitive?.contentOrNull.orEmpty(), write = true)
        val content = arguments["content"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Не задано новое содержимое файла.")
        require(content.isNotEmpty() && content.length <= MAX_ROOT_WRITE_CHARS && '\u0000' !in content) {
            "Новое содержимое должно быть непустым, не содержать NUL и занимать не больше 32 КБ."
        }
        val payload = buildJsonObject {
            put("path", path)
            put("content", content)
        }.toString()
        return AgentToolResult(
            text = "Изменение системного конфигурационного файла ожидает подтверждения.",
            action = AgentExternalAction(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                type = AgentActionType.TERMUX_COMMAND,
                title = "Изменить конфигурационный файл через root?",
                subject = "root_write",
                body = payload
            )
        )
    }

    suspend fun execute(context: Context, action: AgentExternalAction): AgentToolResult {
        require(action.type == AgentActionType.TERMUX_COMMAND) {
            "Передано действие другого типа в обработчик Termux."
        }
        return when (action.subject) {
            "root_list" -> executeRootList(context, action)
            "root_read" -> executeRootRead(context, action)
            "root_write" -> executeRootWrite(context, action)
            else -> executeCommand(context, action.body)
        }
    }

    suspend fun executeCommand(context: Context, command: String): AgentToolResult {
        val tokens = validateAndTokenize(command)
        val result = executeTermux(
            context = context,
            executablePath = TERMUX_PREFIX + tokens.first(),
            arguments = tokens.drop(1)
        )
        return result.asToolResult(command)
    }

    private suspend fun executeRootList(context: Context, action: AgentExternalAction): AgentToolResult {
        val payload = Json.parseToJsonElement(action.body).jsonObject
        val path = validateConfigDirectory(payload["path"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val quoted = quoteShell(path)
        val script = """
            target=$quoted
            resolved=$(/system/bin/readlink -f "${'$'}target") || exit 20
            [ "${'$'}resolved" = "${'$'}target" ] || exit 21
            case "${'$'}resolved" in /system/etc|/system/etc/*|/vendor/etc|/vendor/etc/*|/product/etc|/product/etc/*|/odm/etc|/odm/etc/*) ;; *) exit 22;; esac
            [ -d "${'$'}resolved" ] || exit 23
            /system/bin/ls -la "${'$'}resolved"
        """.trimIndent()
        val result = executeTermux(
            context,
            executablePath = "/system/bin/su",
            arguments = listOf("-c", script)
        )
        return result.asToolResult("root list $path", "Содержимое каталога $path")
    }

    private suspend fun executeRootRead(context: Context, action: AgentExternalAction): AgentToolResult {
        val payload = Json.parseToJsonElement(action.body).jsonObject
        val path = validateConfigPath(payload["path"]?.jsonPrimitive?.contentOrNull.orEmpty(), write = false)
        val quoted = quoteShell(path)
        val script = """
            target=$quoted
            resolved=$(/system/bin/readlink -f "${'$'}target") || exit 20
            [ "${'$'}resolved" = "${'$'}target" ] || exit 21
            case "${'$'}resolved" in /system/etc/*|/vendor/etc/*|/product/etc/*|/odm/etc/*) ;; *) exit 22;; esac
            [ -f "${'$'}resolved" ] || exit 23
            size=$(/system/bin/wc -c < "${'$'}resolved")
            [ "${'$'}size" -le $MAX_ROOT_FILE_BYTES ] || exit 24
            /system/bin/cat "${'$'}resolved"
        """.trimIndent()
        val result = executeTermux(
            context,
            executablePath = "/system/bin/su",
            arguments = listOf("-c", script)
        )
        return result.asToolResult("root read $path", "Содержимое $path")
    }

    private suspend fun executeRootWrite(context: Context, action: AgentExternalAction): AgentToolResult {
        val payload = Json.parseToJsonElement(action.body).jsonObject
        val path = validateConfigPath(payload["path"]?.jsonPrimitive?.contentOrNull.orEmpty(), write = true)
        val content = payload["content"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Не задано новое содержимое файла.")
        require(content.isNotEmpty() && content.length <= MAX_ROOT_WRITE_CHARS && '\u0000' !in content) {
            "Новое содержимое должно быть непустым, не содержать NUL и занимать не больше 32 КБ."
        }
        val backupName = "config-${UUID.randomUUID()}.bak"
        val backup = "$ROOT_BACKUP_DIR/$backupName"
        val quoted = quoteShell(path)
        val quotedBackup = quoteShell(backup)
        val script = """
            target=$quoted
            backup=$quotedBackup
            resolved=$(/system/bin/readlink -f "${'$'}target") || exit 20
            [ "${'$'}resolved" = "${'$'}target" ] || exit 21
            case "${'$'}resolved" in /system/etc/*|/vendor/etc/*|/product/etc/*|/odm/etc/*) ;; *) exit 22;; esac
            [ -f "${'$'}resolved" ] || exit 23
            size=$(/system/bin/wc -c < "${'$'}resolved")
            [ "${'$'}size" -le $MAX_ROOT_FILE_BYTES ] || exit 24
            /system/bin/mkdir -p $ROOT_BACKUP_DIR || exit 25
            /system/bin/cp -p "${'$'}resolved" "${'$'}backup" || exit 25
            /system/bin/cat > "${'$'}resolved" || exit 26
            echo "BACKUP:${'$'}backup"
        """.trimIndent()
        val result = executeTermux(
            context,
            executablePath = "/system/bin/su",
            arguments = listOf("-c", script),
            stdin = content
        )
        return result.asToolResult("root write $path", "Файл $path изменён. Резервная копия: $backup")
    }

    private suspend fun executeTermux(
        context: Context,
        executablePath: String,
        arguments: List<String>,
        stdin: String? = null
    ): TermuxCommandResult {
        val executionId = uniqueExecutionId()
        val deferred = CompletableDeferred<TermuxCommandResult>()
        pendingResults[executionId] = deferred
        val callbackIntent = Intent(context, TermuxCommandResultReceiver::class.java).apply {
            action = "app.aidroid.remaster.TERMUX_RESULT"
            putExtra("aidroid_execution_id", executionId)
        }
        val callback = PendingIntent.getBroadcast(
            context,
            executionId,
            callbackIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", executablePath)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments.toTypedArray())
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
            if (stdin != null) putExtra("com.termux.RUN_COMMAND_STDIN", stdin)
        }
        try {
            context.startService(intent)
        } catch (error: SecurityException) {
            pendingResults.remove(executionId)
            callback.cancel()
            throw IllegalStateException(
                "Termux отклонил внешний запуск. Разреши Aidroid разрешение RUN_COMMAND и включи allow-external-apps=true в ~/.termux/termux.properties.",
                error
            )
        } catch (error: Exception) {
            pendingResults.remove(executionId)
            callback.cancel()
            throw IllegalStateException(
                "Не удалось запустить Termux. Проверь, что Termux установлен и разрешает внешние команды.",
                error
            )
        }
        return try {
            withTimeout(90_000L) { deferred.await() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            throw IllegalStateException("Termux не вернул результат за 90 секунд. Проверь, не ожидает ли root-менеджер подтверждения.", error)
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
                it in setOf("-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprint0", "-fprintf", "-fls") ||
                    it.startsWith("-exec") || it.startsWith("-ok") || it.startsWith("-fprint")
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

    internal fun validateConfigDirectory(path: String): String {
        val value = path.trim().trimEnd('/')
        require(value.isNotBlank() && value.length <= 300 && value.startsWith("/") && '\u0000' !in value && '\\' !in value) {
            "Укажи абсолютный корректный путь к конфигурационной папке."
        }
        val roots = listOf("/system/etc", "/vendor/etc", "/product/etc", "/odm/etc")
        require(roots.any { value == it || value.startsWith("$it/") }) {
            "Root-доступ ограничен конфигурационными папками /system/etc, /vendor/etc, /product/etc и /odm/etc."
        }
        val segments = value.removePrefix("/").split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." || it.lowercase() in blockedConfigSegments }) {
            "Этот каталог относится к критичной системной или security-конфигурации и заблокирован."
        }
        return value
    }

    internal fun validateConfigPath(path: String, write: Boolean): String {
        val value = path.trim()
        require(value.isNotBlank() && value.length <= 300 && value.startsWith("/") && '\u0000' !in value && '\\' !in value) {
            "Укажи абсолютный корректный путь к конфигурационному файлу."
        }
        val segments = value.removePrefix("/").split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Путь содержит недопустимые сегменты."
        }
        require(configRoots.any { value.startsWith(it) }) {
            "Root-доступ ограничен конфигурационными файлами внутри /system/etc, /vendor/etc, /product/etc и /odm/etc."
        }
        require(segments.none { it.lowercase() in blockedConfigSegments }) {
            "Этот путь относится к критичной системной или security-конфигурации и заблокирован."
        }
        if (write) {
            val blockedWriteSegments = setOf("permissions", "sysconfig", "selinux", "vintf", "default-permissions", "privapp-permissions")
            require(segments.none { segment ->
                segment.lowercase() in blockedWriteSegments ||
                    listOf("permission", "privapp", "security", "credential", "key", "cert", "manifest").any { marker ->
                        segment.lowercase().contains(marker)
                    }
            }) { "Изменение системных security/permission конфигураций заблокировано." }
        }
        val extension = value.substringAfterLast('.', "").lowercase()
        val allowed = if (write) writableConfigExtensions else readableConfigExtensions
        require(extension in allowed) {
            "Для этой root-операции не разрешён тип файла .$extension."
        }
        val name = segments.last().lowercase()
        require(name !in setOf("build.prop", "default.prop", "init.rc", "fstab", "passwd", "shadow", "sepolicy")) {
            "Этот системный файл критичен и заблокирован."
        }
        return value
    }

    private fun quoteShell(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun TermuxCommandResult.asToolResult(label: String, successPrefix: String? = null): AgentToolResult {
        if (internalErrorCode != -1) {
            throw IllegalStateException(internalErrorMessage.ifBlank { "Termux сообщил внутреннюю ошибку $internalErrorCode." })
        }
        val combined = buildString {
            appendLine("Команда: $label")
            appendLine("Код завершения: $exitCode")
            if (stdout.isNotBlank()) { appendLine("STDOUT:"); appendLine(stdout) }
            if (stderr.isNotBlank()) { appendLine("STDERR:"); appendLine(stderr) }
        }.trim().takeLast(MAX_OUTPUT_CHARS)
        val resultText = if (combined.isBlank()) "Команда завершилась без вывода. Код: $exitCode" else combined
        val title = successPrefix?.let { "$it\n\n$resultText" } ?: resultText
        return AgentToolResult(title)
    }
}

data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val internalErrorCode: Int,
    val internalErrorMessage: String
)

class TermuxCommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.aidroid.remaster.TERMUX_RESULT") return
        val id = intent.getIntExtra("aidroid_execution_id", -1)
        if (id < 0) return
        val bundle: Bundle? = intent.getBundleExtra("result")
        if (bundle == null) {
            TermuxCommandTools.completeResult(
                id,
                TermuxCommandResult("", "", -1, 1, "Termux вернул пустой пакет результата.")
            )
            return
        }
        TermuxCommandTools.completeResult(
            id,
            TermuxCommandResult(
                stdout = bundle.getString("stdout").orEmpty(),
                stderr = bundle.getString("stderr").orEmpty(),
                exitCode = bundle.getInt("exitCode", -1),
                internalErrorCode = bundle.getInt("err", -1),
                internalErrorMessage = bundle.getString("errmsg").orEmpty()
            )
        )
    }
}
