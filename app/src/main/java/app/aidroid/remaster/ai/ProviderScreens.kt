package app.aidroid.remaster.ai

import app.aidroid.remaster.GlassPanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ProviderModelsScreen(manager: ProviderManager) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(manager.providers()) }
    var activeId by remember { mutableStateOf(manager.activeProviderId()) }
    var showEditor by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ProviderConfig?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        providers = manager.providers()
        activeId = manager.activeProviderId()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Модели и провайдеры", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Подключай облачные API, собственные endpoint-ы и локальные AI-серверы.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ПОДДЕРЖИВАЕМЫЕ ПОДКЛЮЧЕНИЯ", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("OpenAI · Anthropic · Gemini · OpenAI-compatible · Ollama · llama.cpp",
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("Для своего сервера укажи базовый URL и точный ID модели. API-ключи хранятся отдельно в Android Keystore.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (providers.isEmpty()) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Провайдеры ещё не добавлены", fontWeight = FontWeight.SemiBold)
                    Text("Добавь API-подключение или локальный сервер, чтобы получать ответы прямо в чатах.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        providers.forEach { provider ->
            val active = provider.id == activeId
            GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 23, highlighted = active) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(provider.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (active) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                            Text(provider.type.displayName(), color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { editTarget = provider; showEditor = true }) {
                            Icon(Icons.Filled.Edit, "Изменить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(provider.model, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    Text(provider.baseUrl, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!active) {
                            Button(onClick = {
                                manager.setActiveProvider(provider.id)
                                refresh()
                                status = "Активная модель: ${provider.model}"
                            }, modifier = Modifier.weight(1f)) {
                                Text("Использовать")
                            }
                        } else {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                                shape = RoundedCornerShape(50)) {
                                Text("АКТИВНАЯ", modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        IconButton(onClick = {
                            manager.removeProvider(provider.id)
                            refresh()
                            status = "Подключение удалено"
                        }) {
                            Icon(Icons.Filled.DeleteOutline, "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { editTarget = null; showEditor = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp)
        ) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить провайдера")
        }

        Text(
            "Локальные серверы: укажи IP компьютера или устройства в одной Wi-Fi сети, например http://192.168.1.20:11434 для Ollama. 127.0.0.1 указывает на сам телефон, а не на компьютер.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        AnimatedVisibility(status != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            status?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showEditor) {
        ProviderEditorDialog(
            manager = manager,
            initial = editTarget,
            onDismiss = { showEditor = false; editTarget = null },
            onSaved = { refresh(); showEditor = false; editTarget = null; status = "Настройки провайдера сохранены" }
        )
    }
}

@Composable
private fun ProviderEditorDialog(
    manager: ProviderManager,
    initial: ProviderConfig?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val client = remember { ProviderHttpClient() }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: ProviderType.OPENAI) }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by remember(initial?.id) { mutableStateOf(initial?.baseUrl ?: defaultBaseUrl(ProviderType.OPENAI)) }
    var model by remember(initial?.id) { mutableStateOf(initial?.model.orEmpty()) }
    var apiKey by remember(initial?.id) { mutableStateOf("") }
    var supportsReasoning by remember(initial?.id) { mutableStateOf(initial?.supportsReasoning ?: false) }
    var supportsImageApi by remember(initial?.id) { mutableStateOf(initial?.supportsImageApi ?: false) }
    var typeMenu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<AvailableModel>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое подключение" else "Настройка провайдера",
            fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.displayName(), modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        ProviderType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName()) },
                                onClick = {
                                    type = option
                                    baseUrl = defaultBaseUrl(option)
                                    supportsReasoning = false
                                    supportsImageApi = false
                                    if (name.isBlank()) name = option.displayName()
                                    typeMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Базовый URL API") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("ID модели") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text(if (initial == null) "API-ключ" else "Новый API-ключ (необязательно)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Поддержка reasoning", fontWeight = FontWeight.Medium)
                        Text("Включай только для моделей с поддержкой этого параметра.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = supportsReasoning, onCheckedChange = { supportsReasoning = it })
                }
                if (type == ProviderType.OPENAI_COMPATIBLE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Поддерживает OpenAI Images API", fontWeight = FontWeight.Medium)
                            Text("Включай, только если шлюз поддерживает /v1/images/generations и /v1/images/edits. Совместимость чата этого не гарантирует.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = supportsImageApi, onCheckedChange = { supportsImageApi = it })
                    }
                } else if (type == ProviderType.OPENAI) {
                    Text("OpenAI напрямую: Images API доступен при наличии прав и активного тарифа.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            loading = true
                            message = null
                            try {
                                val candidate = ProviderConfig(
                                    id = initial?.id ?: manager.newProviderId(),
                                    name = name.ifBlank { type.displayName() },
                                    type = type,
                                    baseUrl = baseUrl,
                                    model = model.ifBlank { "model-id" },
                                    supportsReasoning = supportsReasoning,
                                    supportsImageApi = type == ProviderType.OPENAI || supportsImageApi
                                )
                                val key = apiKey.ifBlank { initial?.let { manager.apiKey(it.id).orEmpty() }.orEmpty() }
                                models = client.listModels(candidate, key)
                                if (model.isBlank() && models.isNotEmpty()) model = models.first().id
                                message = if (models.isEmpty()) "Соединение установлено, но список моделей пуст. Введи ID вручную."
                                    else "Найдено моделей: ${models.size}"
                            } catch (error: Exception) {
                                message = error.message ?: "Не удалось подключиться."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading && baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Проверить соединение и загрузить модели")
                }
                if (models.isNotEmpty()) {
                    Text("Доступные модели", fontWeight = FontWeight.SemiBold)
                    models.take(12).forEach { available ->
                        TextButton(onClick = { model = available.id }, modifier = Modifier.fillMaxWidth()) {
                            Text(available.id, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (model == available.id) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                message?.let {
                    Text(it, color = if (it.startsWith("Найдено") || it.startsWith("Соединение")) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank() &&
                    (type == ProviderType.OLLAMA || type == ProviderType.OPENAI_COMPATIBLE || apiKey.isNotBlank() || (initial != null && !manager.apiKey(initial.id).isNullOrBlank())),
                onClick = {
                    runCatching {
                        manager.saveProvider(
                            ProviderConfig(
                                id = initial?.id ?: manager.newProviderId(),
                                name = name,
                                type = type,
                                baseUrl = baseUrl,
                                model = model,
                                supportsReasoning = supportsReasoning,
                                supportsImageApi = type == ProviderType.OPENAI || supportsImageApi
                            ),
                            apiKey
                        )
                    }.onSuccess { onSaved() }.onFailure { message = it.message ?: "Проверь настройки подключения." }
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun AgentWorkspaceScreen(manager: ProviderManager) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var providers by remember { mutableStateOf(manager.providers()) }
    var preferences by remember { mutableStateOf(manager.agentPreferences()) }
    var providerMenu by remember { mutableStateOf(false) }
    var imageProviderMenu by remember { mutableStateOf(false) }
    var savedNotice by remember { mutableStateOf<String?>(null) }
    var tavilyKey by remember { mutableStateOf(manager.toolCredential("tavily_search").orEmpty()) }
    var tavilyKeyVisible by remember { mutableStateOf(false) }
    var braveKey by remember { mutableStateOf(manager.toolCredential("brave_search").orEmpty()) }
    var braveKeyVisible by remember { mutableStateOf(false) }
    var imageGenerationModel by remember { mutableStateOf(preferences.imageGenerationModel) }
    var imageEditModel by remember { mutableStateOf(preferences.imageEditModel) }
    var codeRunnerUrl by remember { mutableStateOf(preferences.codeRunnerUrl) }
    var endpointError by remember { mutableStateOf<String?>(null) }

    val fileTreePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                val current = manager.agentPreferences()
                manager.saveAgentPreferences(
                    current.copy(androidFileRoots = (current.androidFileRoots + uri.toString()).distinct())
                )
                preferences = manager.agentPreferences()
                savedNotice = "Доступ к папке сохранён"
            }.onFailure { error ->
                savedNotice = error.message ?: "Не удалось сохранить доступ к папке"
            }
        }
    }

    fun update(next: AgentPreferences) {
        preferences = next
        manager.saveAgentPreferences(next)
        savedNotice = "Настройки сохранены"
    }

    fun toggleTool(id: String, enabled: Boolean) {
        val nextTools = preferences.enabledTools.toMutableSet().apply {
            if (enabled) add(id) else remove(id)
        }
        update(preferences.copy(enabledTools = nextTools.toList()))
    }

    val active = providers.firstOrNull { it.id == preferences.activeProviderId && it.enabled }
    val imageProviders = providers.filter { provider ->
        provider.enabled &&
            provider.type in setOf(ProviderType.OPENAI, ProviderType.OPENAI_COMPATIBLE) &&
            (provider.type == ProviderType.OPENAI || provider.supportsImageApi) &&
            !manager.apiKey(provider.id).isNullOrBlank()
    }
    val automaticImageProvider = imageProviders.firstOrNull { it.id == active?.id }
        ?: imageProviders.firstOrNull { it.type == ProviderType.OPENAI }
    val selectedImageProvider = if (preferences.imageProviderId == null) {
        automaticImageProvider
    } else {
        imageProviders.firstOrNull { it.id == preferences.imageProviderId }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Рабочее пространство агента", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Подключай инструменты, управляй доступом и выбирай, какие действия разрешены агенту.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "АКТИВНАЯ МОДЕЛЬ",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Box {
                    OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            active?.let { "${it.name} · ${it.model}" } ?: "Выбрать провайдера",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        providers.filter { it.enabled }.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} · ${item.model}") },
                                onClick = {
                                    update(preferences.copy(activeProviderId = item.id))
                                    providerMenu = false
                                }
                            )
                        }
                    }
                }
                if (active == null) {
                    Text("Сначала добавь подключение в разделе «Модели».", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Все запросы из чатов будут отправляться через эту модель.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(
                    "РЕЖИМ РАЗМЫШЛЕНИЯ",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                ReasoningMode.entries.forEach { mode ->
                    FilterChip(
                        selected = preferences.reasoningMode == mode,
                        onClick = { update(preferences.copy(reasoningMode = mode)) },
                        label = {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(mode.label)
                                Text(mode.description, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    if (active?.supportsReasoning == true) {
                        "Нативный reasoning включён для этой модели."
                    } else {
                        "Нативный reasoning выключен для этой модели. Включай его в карточке провайдера только при подтверждённой поддержке."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "ИНСТРУМЕНТЫ АГЕНТА",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Text(
                    "УПРАВЛЕНИЕ ANDROID",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                ToolPreferenceRow(
                    title = "Постоянное разрешение на управление",
                    description = "Разрешить агенту самостоятельно выполнять перечисленные ниже низкорисковые действия без отдельного окна каждый раз. Можно отозвать здесь в любой момент.",
                    checked = preferences.androidAutonomyEnabled,
                    onChecked = { update(preferences.copy(androidAutonomyEnabled = it)) }
                )
                Text(
                    "Разрешение действует только внутри Aidroid и не даёт root-доступ. Android всё равно сам запросит специальные разрешения, если они нужны.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Состояние устройства",
                    description = "Модель, Android, батарея, яркость, тайм-аут, автоповорот и уровни громкости",
                    checked = "android_device_status" in preferences.enabledTools,
                    onChecked = { toggleTool("android_device_status", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Открытие системных настроек",
                    description = "26 быстрых разделов и стандартные Android Settings actions; критичные экраны заблокированы",
                    checked = "android_open_settings" in preferences.enabledTools,
                    onChecked = { toggleTool("android_open_settings", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Быстрые системные панели",
                    description = "Открывать панели Wi-Fi, подключений к интернету, громкости и NFC",
                    checked = "android_open_settings_panel" in preferences.enabledTools,
                    onChecked = { toggleTool("android_open_settings_panel", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Управление громкостью",
                    description = "Мультимедиа, звонок, будильник, уведомления и системные звуки",
                    checked = "android_set_media_volume" in preferences.enabledTools,
                    onChecked = { toggleTool("android_set_media_volume", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Параметры экрана",
                    description = "Яркость, тайм-аут отключения экрана, автояркость и автоповорот",
                    checked = "android_set_display_parameter" in preferences.enabledTools,
                    onChecked = { toggleTool("android_set_display_parameter", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Доступ к файловой системе",
                    description = "Просмотр папок, чтение текстовых файлов, создание папок, копирование, перемещение и удаление отдельных файлов",
                    checked = "android_file_access" in preferences.enabledTools,
                    onChecked = { toggleTool("android_file_access", it) }
                )
                Text(
                    "Выбрано корневых папок: ${preferences.androidFileRoots.size}. Агент видит только папки, которые ты явно выбрал через системный экран Android.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { fileTreePicker.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("Выбрать папку для доступа")
                }
                if (preferences.androidFileRoots.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            preferences.androidFileRoots.forEach { raw ->
                                runCatching {
                                    context.contentResolver.releasePersistableUriPermission(
                                        android.net.Uri.parse(raw),
                                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                }
                            }
                            update(preferences.copy(androidFileRoots = emptyList()))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Отозвать доступ ко всем папкам", color = MaterialTheme.colorScheme.error)
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Команды через Termux",
                    description = "Запуск разрешённых диагностических команд в Termux с отдельным подтверждением каждой команды",
                    checked = "termux_command" in preferences.enabledTools,
                    onChecked = { toggleTool("termux_command", it) }
                )
                Text(
                    "Нужен установленный Termux. В его файле ~/.termux/termux.properties включи allow-external-apps=true и выдай Aidroid разрешение RUN_COMMAND. Команды запускаются без root; произвольные shell-скрипты и опасные системные команды заблокированы.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                Text(
                    "ОСТАЛЬНЫЕ ИНСТРУМЕНТЫ",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                ToolPreferenceRow(
                    title = "Калькулятор",
                    description = "Безопасное локальное вычисление арифметики",
                    checked = "calculator" in preferences.enabledTools,
                    onChecked = { toggleTool("calculator", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Дата и время",
                    description = "Точное время и часовой пояс устройства",
                    checked = "current_datetime" in preferences.enabledTools,
                    onChecked = { toggleTool("current_datetime", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Поиск в интернете",
                    description = "Бесплатный поиск без ключей, открытие сайтов и извлечение основного текста",
                    checked = "web_search" in preferences.enabledTools,
                    onChecked = { toggleTool("web_search", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Анализ изображений",
                    description = "Передавать прикреплённые фото мультимодальной модели",
                    checked = "image_analysis" in preferences.enabledTools,
                    onChecked = { toggleTool("image_analysis", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Анализ файлов",
                    description = "Локально читать PDF, DOCX, XLSX, PPTX, TXT, CSV и другие текстовые форматы",
                    checked = "file_analysis" in preferences.enabledTools,
                    onChecked = { toggleTool("file_analysis", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Генерация и редактирование изображений",
                    description = "OpenAI Image API; для правки приложи исходное изображение",
                    checked = "image_generation" in preferences.enabledTools,
                    onChecked = { toggleTool("image_generation", it) }
                )
                if (imageProviders.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { imageProviderMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                selectedImageProvider?.let { "Изображения: ${it.name}" } ?: "Выбрать провайдера изображений",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(expanded = imageProviderMenu, onDismissRequest = { imageProviderMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        automaticImageProvider?.let { "Автоматически · ${it.name}" }
                                            ?: "Автоматически · не настроено"
                                    )
                                },
                                onClick = {
                                    update(preferences.copy(imageProviderId = null))
                                    imageProviderMenu = false
                                }
                            )
                            imageProviders.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text("${provider.name} · ${provider.model}") },
                                    onClick = {
                                        update(preferences.copy(imageProviderId = provider.id))
                                        imageProviderMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        "Для изображений используется выбранный здесь провайдер, независимо от модели чата. У OpenAI-compatible подключений включи поддержку Images API в настройках самого провайдера.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = imageGenerationModel,
                        onValueChange = { imageGenerationModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("ID модели генерации") },
                        supportingText = { Text("По умолчанию: gpt-image-2.6-flare") }
                    )
                    OutlinedTextField(
                        value = imageEditModel,
                        onValueChange = { imageEditModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("ID модели редактирования") },
                        supportingText = { Text("По умолчанию: gpt-image-2.6-sunburst") }
                    )
                    OutlinedButton(
                        onClick = {
                            update(
                                preferences.copy(
                                    imageGenerationModel = imageGenerationModel.trim().ifBlank { "gpt-image-2.6-flare" },
                                    imageEditModel = imageEditModel.trim().ifBlank { "gpt-image-2.6-sunburst" }
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить параметры изображений")
                    }
                    Text(
                        "Если шлюз не знает эти ID, укажи точные названия моделей, которые поддерживает его Image API. Список моделей чата не подтверждает наличие Image API.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Text(
                        "Пока нет доступного провайдера изображений. Добавь OpenAI или включи поддержку OpenAI Images API для совместимого шлюза и сохрани API-ключ.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (preferences.imageProviderId != null && selectedImageProvider == null) {
                    Text(
                        "Выбранный провайдер изображений отключён, удалён или потерял API-ключ. Выбери другой.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Выполнение кода",
                    description = "Python и JavaScript в отдельном Piston-сервере, с подтверждением перед запуском",
                    checked = "code_execution" in preferences.enabledTools,
                    onChecked = { toggleTool("code_execution", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Голосовой ввод",
                    description = "Кнопка микрофона в поле сообщения",
                    checked = "voice_input" in preferences.enabledTools,
                    onChecked = { toggleTool("voice_input", it) }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Озвучивание ответов",
                    description = "Чтение ответа голосом через синтез речи Android",
                    checked = "voice_output" in preferences.enabledTools,
                    onChecked = { toggleTool("voice_output", it) }
                )
                Text(
                    "Постоянное разрешение отключено по умолчанию. Оно распространяется только на обычные действия Android. Операции с файлами и команды Termux всегда требуют отдельного подтверждения. Системные и разрушительные операции остаются ограниченными.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ПОИСК В ИНТЕРНЕТЕ", fontWeight = FontWeight.Bold)
                Text(
                    "Основной поиск работает через Tavily Search API. Введи собственный ключ Tavily. Brave Search можно подключить как резервный источник: он будет вызван только если Tavily не настроен, вернул ошибку или не дал результатов. Старые HTML-поисковики и Mwmbl больше не используются.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = tavilyKey,
                    onValueChange = { tavilyKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tavily API key") },
                    singleLine = true,
                    visualTransformation = if (tavilyKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { tavilyKeyVisible = !tavilyKeyVisible }) {
                            Text(if (tavilyKeyVisible) "Скрыть" else "Показать")
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = {
                            manager.setToolCredential("tavily_search", tavilyKey)
                            savedNotice = if (tavilyKey.isBlank()) "Ключ Tavily удалён" else "Ключ Tavily сохранён"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить Tavily") }
                    OutlinedButton(
                        onClick = {
                            tavilyKey = ""
                            manager.setToolCredential("tavily_search", "")
                            savedNotice = "Ключ Tavily удалён"
                        }
                    ) { Text("Удалить") }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                OutlinedTextField(
                    value = braveKey,
                    onValueChange = { braveKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Brave Search API key (резерв)") },
                    singleLine = true,
                    visualTransformation = if (braveKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { braveKeyVisible = !braveKeyVisible }) {
                            Text(if (braveKeyVisible) "Скрыть" else "Показать")
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = {
                            manager.setToolCredential("brave_search", braveKey)
                            savedNotice = if (braveKey.isBlank()) "Ключ Brave удалён" else "Ключ Brave сохранён"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить Brave") }
                    OutlinedButton(
                        onClick = {
                            braveKey = ""
                            manager.setToolCredential("brave_search", "")
                            savedNotice = "Ключ Brave удалён"
                        }
                    ) { Text("Удалить") }
                }
                Text(
                    "Оба ключа хранятся в защищённом хранилище приложения. Если Tavily не настроен, поиск автоматически попробует Brave при наличии его ключа. Если оба ключа пусты, веб-поиск сообщит, какой ключ нужно добавить.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("СЕРВЕР ВЫПОЛНЕНИЯ КОДА", fontWeight = FontWeight.Bold)
                Text(
                    "Укажи адрес собственного Piston-сервера. Код будет передаваться только после подтверждения запуска. Для внешнего сервера используй HTTPS.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = codeRunnerUrl,
                    onValueChange = { codeRunnerUrl = it; endpointError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL Piston-сервера") },
                    placeholder = { Text("https://server.example") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        runCatching { manager.saveCodeRunnerUrl(codeRunnerUrl) }
                            .onSuccess {
                                preferences = manager.agentPreferences()
                                endpointError = null
                                savedNotice = "Адрес сервера сохранён"
                            }
                            .onFailure { endpointError = it.message ?: "Проверь URL сервера." }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить адрес сервера") }
                endpointError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Если Piston установлен на компьютере в той же сети, можно указать его локальный адрес. Не указывай 127.0.0.1 для другого устройства.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ИНСТРУКЦИИ АГЕНТА", fontWeight = FontWeight.Bold)
                Text(
                    "Задай специализацию и стиль работы. Не помещай сюда пароли или API-ключи.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = preferences.systemPrompt,
                    onValueChange = { update(preferences.copy(systemPrompt = it.take(4000))) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7,
                    placeholder = { Text("Например: помогай разбирать учебные темы и объясняй сложное по шагам.") }
                )
            }
        }
        AnimatedVisibility(savedNotice != null, enter = fadeIn(), exit = fadeOut()) {
            Text(savedNotice.orEmpty(), color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ToolPreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun ProviderType.displayName(): String = when (this) {
    ProviderType.OPENAI -> "OpenAI · Responses API"
    ProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible API"
    ProviderType.ANTHROPIC -> "Anthropic · Claude API"
    ProviderType.GEMINI -> "Google · Gemini API"
    ProviderType.OLLAMA -> "Ollama · локальный сервер"
}

private fun defaultBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.OPENAI -> "https://api.openai.com"
    ProviderType.OPENAI_COMPATIBLE -> "https://example.com/v1"
    ProviderType.ANTHROPIC -> "https://api.anthropic.com"
    ProviderType.GEMINI -> "https://generativelanguage.googleapis.com"
    ProviderType.OLLAMA -> "http://127.0.0.1:11434"
}
