package app.aidroid.remaster.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * User-scoped filesystem access through Android's Storage Access Framework.
 * All paths are relative to one of the directory trees explicitly selected by the user.
 * Mutations are represented as FILE_OPERATION actions and always require confirmation.
 */
object AndroidFileTools {
    const val ALL_FILES_ROOT = "aidroid://all-files"
    private const val MAX_LIST_ITEMS = 100
    private const val MAX_TEXT_BYTES = 64 * 1024
    private const val MAX_COPY_BYTES = 512L * 1024L * 1024L

    fun definitions(enabled: Set<String>, rootsCount: Int): List<LlmTool> {
        if ("android_file_access" !in enabled) return emptyList()
        val rootDescription = if (rootsCount == 0) {
            "Пользователь ещё не выбрал папку в настройках агента."
        } else {
            "Доступно корневых папок: $rootsCount. Индексы начинаются с 0."
        }
        return listOf(
            LlmTool(
                name = "android_file_list",
                description = "Просматривает содержимое выбранной пользователем папки и её подпапок. path — относительный путь внутри root_index, пустая строка означает корень. $rootDescription",
                parameters = objectSchema(
                    "root_index" to integerSchema("Индекс доступной корневой папки, начиная с 0."),
                    "path" to stringSchema("Относительный путь папки внутри выбранного корня."),
                    required = listOf("root_index")
                )
            ),
            LlmTool(
                name = "android_file_read",
                description = "Читает небольшой текстовый файл (до 64 КБ) из выбранной пользователем папки. Не использовать для чтения секретов без прямой просьбы пользователя. $rootDescription",
                parameters = objectSchema(
                    "root_index" to integerSchema("Индекс доступной корневой папки, начиная с 0."),
                    "path" to stringSchema("Относительный путь текстового файла."),
                    required = listOf("root_index", "path")
                )
            ),
            LlmTool(
                name = "android_file_create_folder",
                description = "Создаёт одну новую папку внутри выбранного пользователем корня. Изменение требует отдельного подтверждения. $rootDescription",
                parameters = objectSchema(
                    "root_index" to integerSchema("Индекс доступной корневой папки, начиная с 0."),
                    "parent" to stringSchema("Относительный путь существующей родительской папки; пустая строка означает корень."),
                    "name" to stringSchema("Имя новой папки без символов / и \\."),
                    required = listOf("root_index", "name")
                )
            ),
            LlmTool(
                name = "android_file_copy",
                description = "Копирует один файл в другую папку внутри того же выбранного корня. Папки целиком не копируются; перезапись существующего файла запрещена. Операция требует подтверждения. $rootDescription",
                parameters = objectSchema(
                    "root_index" to integerSchema("Индекс доступной корневой папки, начиная с 0."),
                    "source" to stringSchema("Относительный путь исходного файла."),
                    "destination" to stringSchema("Относительный путь папки назначения; пустая строка означает корень."),
                    "new_name" to stringSchema("Необязательное новое имя копии."),
                    required = listOf("root_index", "source", "destination")
                )
            ),
            LlmTool(
                name = "android_file_move",
                description = "Перемещает один файл в другую папку внутри того же выбранного корня. Папки целиком не перемещаются; перезапись запрещена. Сначала копирует, затем удаляет исходник. Операция требует подтверждения. $rootDescription",
                parameters = objectSchema(
                    "root_index" to integerSchema("Индекс доступной корневой папки, начиная с 0."),
                    "source" to stringSchema("Относительный путь исходного файла."),
                    "destination" to stringSchema("Относительный путь папки назначения; пустая строка означает корень."),
                    "new_name" to stringSchema("Необязательное новое имя файла."),
                    required = listOf("root_index", "source", "destination")
                )
            ),
            LlmTool(
                name = "android_file_delete",
                description = "Удаляет один файл внутри выбранного корня. Папки и массовое удаление не поддерживаются. Всегда требуется отдельное подтверждение с показом точного пути. $rootDescription",
                parameters = objectSchema(
                    "root_index" to integerSchema("Индекс доступной корневой папки, начиная с 0."),
                    "path" to stringSchema("Точный относительный путь файла для удаления."),
                    required = listOf("root_index", "path")
                )
            )
        )
    }

    fun executeRead(
        context: Context,
        roots: List<String>,
        name: String,
        arguments: JsonObject
    ): AgentToolResult {
        val rootIndex = arguments["root_index"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Не указан индекс корневой папки.")
        if (roots.getOrNull(rootIndex) == ALL_FILES_ROOT) {
            requireAllFilesAccess()
            return when (name) {
                "android_file_list" -> listSharedStorage(context, roots, arguments)
                "android_file_read" -> readSharedTextFile(roots, arguments)
                else -> throw IllegalArgumentException("Неизвестная операция чтения файлов.")
            }
        }
        return when (name) {
            "android_file_list" -> listDirectory(context, roots, arguments)
            "android_file_read" -> readTextFile(context, roots, arguments)
            else -> throw IllegalArgumentException("Неизвестная операция чтения файлов.")
        }
    }

    fun prepare(name: String, arguments: JsonObject, chatId: Long): AgentToolResult {
        val rootIndex = arguments["root_index"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Не указан индекс папки.")
        val payload = when (name) {
            "android_file_create_folder" -> {
                val parent = safePath(arguments.stringOrEmpty("parent"))
                val folderName = safeName(arguments.string("name"))
                buildJsonObject {
                    put("root_index", rootIndex)
                    put("parent", parent)
                    put("name", folderName)
                }
            }
            "android_file_copy", "android_file_move" -> {
                val source = safePath(arguments.string("source"), allowEmpty = false)
                val destination = safePath(arguments.string("destination"))
                val newName = arguments.stringOrEmpty("new_name").takeIf { it.isNotBlank() }?.let(::safeName)
                buildJsonObject {
                    put("root_index", rootIndex)
                    put("source", source)
                    put("destination", destination)
                    if (newName != null) put("new_name", newName)
                }
            }
            "android_file_delete" -> {
                val target = safePath(arguments.string("path"), allowEmpty = false)
                buildJsonObject {
                    put("root_index", rootIndex)
                    put("path", target)
                }
            }
            else -> throw IllegalArgumentException("Неизвестная операция с файлами.")
        }
        val subject = when (name) {
            "android_file_create_folder" -> "create_folder"
            "android_file_copy" -> "copy_file"
            "android_file_move" -> "move_file"
            "android_file_delete" -> "delete_file"
            else -> throw IllegalArgumentException("Неизвестная операция с файлами.")
        }
        val title = when (subject) {
            "create_folder" -> "Создать папку «${payload.string("name")}»?"
            "copy_file" -> "Скопировать «${payload.string("source")}» в «${payload.string("destination")}»?"
            "move_file" -> "Переместить «${payload.string("source")}» в «${payload.string("destination")}»?"
            else -> "Удалить файл «${payload.string("path")}»?"
        }
        return AgentToolResult(
            text = "Ожидается подтверждение операции с файлами.",
            action = AgentExternalAction(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                type = AgentActionType.FILE_OPERATION,
                title = title,
                subject = subject,
                body = payload.toString()
            )
        )
    }

    fun execute(context: Context, action: AgentExternalAction, roots: List<String>): AgentToolResult {
        require(action.type == AgentActionType.FILE_OPERATION) {
            "Передано действие другого типа в обработчик файлов."
        }
        val payload = Json.parseToJsonElement(action.body).jsonObject
        val rootIndex = payload["root_index"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Не удалось определить корневую папку.")
        val rootUri = roots.getOrNull(rootIndex)
            ?: throw IllegalStateException("Выбранная корневая папка больше недоступна. Выдай доступ заново в настройках агента.")
        if (rootUri == ALL_FILES_ROOT) {
            requireAllFilesAccess()
            return executeSharedStorage(action, payload)
        }
        val root = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            ?: throw IllegalStateException("Не удалось открыть выбранную папку.")
        require(root.canWrite()) { "У Aidroid нет права изменять выбранную папку." }

        return when (action.subject) {
            "create_folder" -> {
                val parentPath = payload.string("parent")
                val name = safeName(payload.string("name"))
                val parent = resolve(root, parentPath)
                require(parent.isDirectory && parent.canWrite()) { "Родительская папка недоступна для записи." }
                require(parent.findFile(name) == null) { "Файл или папка с таким именем уже существует; перезапись запрещена." }
                val created = parent.createDirectory(name)
                    ?: throw IllegalStateException("Android не смог создать папку.")
                AgentToolResult("Создана папка «${created.name}».")
            }
            "copy_file", "move_file" -> {
                val sourcePath = safePath(payload.string("source"), allowEmpty = false)
                val destinationPath = safePath(payload.string("destination"))
                val source = resolve(root, sourcePath)
                require(source.isFile && source.canRead()) { "Источник должен быть доступным файлом, не папкой." }
                val destination = resolve(root, destinationPath)
                require(destination.isDirectory && destination.canWrite()) { "Папка назначения недоступна для записи." }
                val targetName = safeName(payload.stringOrEmpty("new_name").ifBlank { source.name.orEmpty() })
                require(destination.findFile(targetName) == null) {
                    "В папке назначения уже существует «$targetName». Перезапись запрещена."
                }
                val copy = copyFile(context, source, destination, targetName)
                if (action.subject == "move_file") {
                    if (!source.delete()) {
                        copy.delete()
                        throw IllegalStateException("Копия создана, но исходный файл не удалось удалить. Копия отменена.")
                    }
                    AgentToolResult("Файл перемещён в «${destinationPath.ifBlank { "/" }}/$targetName».")
                } else {
                    AgentToolResult("Файл скопирован в «${destinationPath.ifBlank { "/" }}/$targetName».")
                }
            }
            "delete_file" -> {
                val path = safePath(payload.string("path"), allowEmpty = false)
                val target = resolve(root, path)
                require(target.isFile) { "Удалять можно только отдельные файлы. Удаление папок и массовые операции отключены." }
                val name = target.name.orEmpty()
                require(target.delete()) { "Android не смог удалить файл." }
                AgentToolResult("Файл «$path» удалён.")
            }
            else -> throw IllegalArgumentException("Неизвестное действие с файлами.")
        }
    }

    internal fun safePath(value: String, allowEmpty: Boolean = true): String {
        require(value.length <= 512) { "Путь слишком длинный." }
        val clean = value.trim().replace('\\', '/').trim('/')
        if (clean.isEmpty()) {
            require(allowEmpty) { "Путь не должен быть пустым." }
            return ""
        }
        val segments = clean.split('/')
        require(segments.all { it.isNotBlank() && it != "." && it != ".." && '\u0000' !in it }) {
            "Путь содержит недопустимые сегменты."
        }
        return segments.joinToString("/")
    }

    private fun safeName(value: String): String {
        val name = value.trim()
        require(name.isNotBlank() && name.length <= 180) { "Имя файла или папки должно содержать от 1 до 180 символов." }
        require(name !in setOf(".", "..") && '/' !in name && '\\' !in name && '\u0000' !in name) {
            "Имя не должно содержать разделители пути."
        }
        return name
    }

    private fun listDirectory(context: Context, roots: List<String>, args: JsonObject): AgentToolResult {
        val (root, index) = getRoot(context, roots, args)
        val path = safePath(args.stringOrEmpty("path"))
        val directory = resolve(root, path)
        require(directory.isDirectory && directory.canRead()) { "Папка не найдена или недоступна для чтения." }
        val entries = directory.listFiles().sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty().lowercase() }
        ).take(MAX_LIST_ITEMS)
        val result = buildString {
            appendLine("Содержимое папки root_index=$index, path=/$path")
            appendLine("Элементов: ${entries.size}${if (entries.size == MAX_LIST_ITEMS) " (показаны первые $MAX_LIST_ITEMS)" else ""}")
            entries.forEach { item ->
                val type = if (item.isDirectory) "ПАПКА" else "ФАЙЛ"
                val size = if (item.isFile) item.length().coerceAtLeast(0L).toString() + " байт" else "—"
                appendLine("$type | ${item.name.orEmpty()} | $size")
            }
        }
        return AgentToolResult(result)
    }

    private fun readTextFile(context: Context, roots: List<String>, args: JsonObject): AgentToolResult {
        val (root, _) = getRoot(context, roots, args)
        val path = safePath(args.string("path"), allowEmpty = false)
        val file = resolve(root, path)
        require(file.isFile && file.canRead()) { "Файл не найден или недоступен для чтения." }
        val extension = file.name.orEmpty().substringAfterLast('.', "").lowercase()
        val textExtensions = setOf("txt", "md", "json", "xml", "kt", "java", "py", "js", "html", "css", "yaml", "yml", "log", "csv", "toml", "properties", "sh", "gradle")
        require(file.type.orEmpty().startsWith("text/") || extension in textExtensions) {
            "Разрешено читать только текстовые файлы. Для других типов используй анализ вложений."
        }
        require(file.length() in 1..MAX_TEXT_BYTES.toLong()) { "Размер текстового файла должен быть не больше 64 КБ." }
        val bytes = context.contentResolver.openInputStream(file.uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_TEXT_BYTES) { "Файл превысил лимит 64 КБ при чтении." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalStateException("Не удалось открыть файл.")
        val text = bytes.toString(Charsets.UTF_8)
        require(text.none { it == '\u0000' }) { "Файл похож на бинарный, чтение как текста отменено." }
        return AgentToolResult("[Файл: $path]\n$text")
    }


    private fun requireAllFilesAccess() {
        check(Environment.isExternalStorageManager()) {
            "У Aidroid нет разрешения «Доступ ко всем файлам». Выдай его в настройках приложения."
        }
    }

    private fun sharedStorageRoot(): File = Environment.getExternalStorageDirectory().canonicalFile

    private fun resolveShared(root: File, path: String, allowRoot: Boolean = true): File {
        val clean = safePath(path, allowEmpty = allowRoot)
        val candidate = File(root, clean).canonicalFile
        val rootPath = root.canonicalPath
        require(candidate.canonicalPath == rootPath ||
            candidate.canonicalPath.startsWith(rootPath + File.separator)) {
            "Путь выходит за пределы общего накопителя."
        }
        return candidate
    }

    private fun listSharedStorage(
        context: Context,
        roots: List<String>,
        args: JsonObject
    ): AgentToolResult {
        val index = args["root_index"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Не указан индекс корневой папки.")
        require(roots.getOrNull(index) == ALL_FILES_ROOT) { "Корень общего накопителя больше не активен." }
        val path = safePath(args.stringOrEmpty("path"))
        val directory = resolveShared(sharedStorageRoot(), path)
        require(directory.isDirectory && directory.canRead()) { "Папка не найдена или Android запретил к ней доступ." }
        val entries = directory.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ).orEmpty().take(MAX_LIST_ITEMS)
        return AgentToolResult(buildString {
            appendLine("Общий накопитель, path=/$path")
            appendLine("Элементов: ${entries.size}${if (entries.size == MAX_LIST_ITEMS) " (показаны первые $MAX_LIST_ITEMS)" else ""}")
            entries.forEach { item ->
                appendLine("${if (item.isDirectory) "ПАПКА" else "ФАЙЛ"} | ${item.name} | ${if (item.isFile) item.length().coerceAtLeast(0L).toString() + " байт" else "—"}")
            }
        })
    }

    private fun readSharedTextFile(
        roots: List<String>,
        args: JsonObject
    ): AgentToolResult {
        val index = args["root_index"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Не указан индекс корневой папки.")
        require(roots.getOrNull(index) == ALL_FILES_ROOT) { "Корень общего накопителя больше не активен." }
        val path = safePath(args.string("path"), allowEmpty = false)
        val file = resolveShared(sharedStorageRoot(), path, allowRoot = false)
        require(file.isFile && file.canRead()) { "Файл не найден или Android запретил к нему доступ." }
        val extension = file.extension.lowercase()
        val textExtensions = setOf("txt", "md", "json", "xml", "kt", "java", "py", "js", "html", "css", "yaml", "yml", "log", "csv", "toml", "properties", "sh", "gradle", "conf", "prop")
        require(extension in textExtensions) { "Разрешено читать только файлы известных текстовых форматов." }
        require(file.length() in 1..MAX_TEXT_BYTES.toLong()) { "Размер файла должен быть не больше 64 КБ." }
        val bytes = FileInputStream(file).use { it.readBytes() }
        require(bytes.size <= MAX_TEXT_BYTES) { "Файл превысил лимит 64 КБ при чтении." }
        val text = bytes.toString(Charsets.UTF_8)
        require(text.none { it == '\u0000' }) { "Файл похож на бинарный, чтение как текста отменено." }
        return AgentToolResult("[Файл: $path]\n$text")
    }

    private fun executeSharedStorage(
        action: AgentExternalAction,
        payload: JsonObject
    ): AgentToolResult {
        val root = sharedStorageRoot()
        fun target(path: String, allowRoot: Boolean = false) = resolveShared(root, path, allowRoot)
        return when (action.subject) {
            "create_folder" -> {
                val parent = target(safePath(payload.stringOrEmpty("parent")), allowRoot = true)
                val name = safeName(payload.string("name"))
                require(parent.isDirectory && parent.canWrite()) { "Родительская папка недоступна для записи." }
                val created = File(parent, name)
                require(!created.exists()) { "Файл или папка с таким именем уже существует; перезапись запрещена." }
                require(created.mkdir()) { "Не удалось создать папку." }
                AgentToolResult("Создана папка «${created.name}».")
            }
            "copy_file", "move_file" -> {
                val sourcePath = safePath(payload.string("source"), allowEmpty = false)
                val destinationPath = safePath(payload.string("destination"))
                val source = target(sourcePath)
                val destination = target(destinationPath, allowRoot = true)
                require(source.isFile && source.canRead()) { "Источник должен быть доступным файлом." }
                require(source.length() in 0..MAX_COPY_BYTES) { "Размер файла превышает лимит 512 МБ." }
                require(destination.isDirectory && destination.canWrite()) { "Папка назначения недоступна для записи." }
                val targetName = safeName(payload.stringOrEmpty("new_name").ifBlank { source.name })
                val output = File(destination, targetName)
                require(!output.exists()) { "В папке назначения уже существует «$targetName». Перезапись запрещена." }
                try {
                    FileInputStream(source).use { input ->
                        FileOutputStream(output).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= MAX_COPY_BYTES) { "Файл превысил лимит копирования 512 МБ." }
                                out.write(buffer, 0, count)
                            }
                            out.fd.sync()
                        }
                    }
                } catch (error: Exception) {
                    output.delete()
                    throw error
                }
                if (action.subject == "move_file" && !source.delete()) {
                    output.delete()
                    throw IllegalStateException("Копия создана, но исходный файл не удалось удалить. Копия отменена.")
                }
                AgentToolResult(
                    if (action.subject == "move_file") "Файл перемещён в /$destinationPath/$targetName."
                    else "Файл скопирован в /$destinationPath/$targetName."
                )
            }
            "delete_file" -> {
                val path = safePath(payload.string("path"), allowEmpty = false)
                val file = target(path)
                require(file.isFile) { "Удалять можно только отдельные файлы. Удаление папок и массовые операции отключены." }
                require(file.delete()) { "Android не смог удалить файл." }
                AgentToolResult("Файл «$path» удалён.")
            }
            else -> throw IllegalArgumentException("Неизвестное действие с файлами.")
        }
    }

    private fun getRoot(context: Context, roots: List<String>, args: JsonObject): Pair<DocumentFile, Int> {
        val index = args["root_index"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Не указан индекс корневой папки.")
        val uri = roots.getOrNull(index)
            ?: throw IllegalStateException("Папка с индексом $index не выбрана. Открой настройки агента и добавь корневую папку.")
        val root = DocumentFile.fromTreeUri(context, Uri.parse(uri))
            ?: throw IllegalStateException("Не удалось открыть выбранную папку.")
        require(root.canRead()) { "У Aidroid больше нет доступа к выбранной папке. Выдай доступ заново." }
        return root to index
    }

    private fun resolve(root: DocumentFile, path: String): DocumentFile {
        var current = root
        safePath(path).split('/').filter { it.isNotBlank() }.forEach { segment ->
            current = current.findFile(segment)
                ?: throw IllegalArgumentException("Путь «$path» не найден внутри выбранной папки.")
        }
        return current
    }

    private fun copyFile(
        context: Context,
        source: DocumentFile,
        destination: DocumentFile,
        name: String
    ): DocumentFile {
        require(source.length() in 0..MAX_COPY_BYTES) { "Размер файла превышает лимит копирования 512 МБ." }
        val target = destination.createFile(
            source.type?.takeIf { it.isNotBlank() } ?: "application/octet-stream",
            name
        ) ?: throw IllegalStateException("Не удалось создать файл в папке назначения.")
        try {
            val input = context.contentResolver.openInputStream(source.uri)
                ?: throw IllegalStateException("Не удалось открыть исходный файл.")
            input.use { sourceStream ->
                val output = context.contentResolver.openOutputStream(target.uri, "w")
                    ?: throw IllegalStateException("Не удалось записать копию файла.")
                output.use { targetStream ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = sourceStream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_COPY_BYTES) { "Файл превысил лимит копирования 512 МБ." }
                        targetStream.write(buffer, 0, count)
                    }
                    targetStream.flush()
                }
            }
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun objectSchema(
        vararg fields: Pair<String, JsonObject>,
        required: List<String>
    ) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            fields.forEach { (name, schema) -> put(name, schema) }
        }
        putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        put("additionalProperties", false)
    }

    private fun stringSchema(description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun integerSchema(description: String) = buildJsonObject {
        put("type", "integer")
        put("minimum", 0)
        put("description", description)
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Не указано поле «$key».")

    private fun JsonObject.stringOrEmpty(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.string(key: String, default: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: default
}
