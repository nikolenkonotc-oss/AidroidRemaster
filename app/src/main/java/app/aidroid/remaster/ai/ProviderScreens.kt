package app.aidroid.remaster.ai

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
                    color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, letterSpacing = 1.1f)
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
                                    supportsReasoning = option in setOf(ProviderType.OPENAI, ProviderType.ANTHROPIC, ProviderType.GEMINI)
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
                                    supportsReasoning = supportsReasoning
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
                    (type == ProviderType.OLLAMA || apiKey.isNotBlank() || (initial != null && !manager.apiKey(initial.id).isNullOrBlank())),
                onClick = {
                    runCatching {
                        manager.saveProvider(
                            ProviderConfig(
                                id = initial?.id ?: manager.newProviderId(),
                                name = name,
                                type = type,
                                baseUrl = baseUrl,
                                model = model,
                                supportsReasoning = supportsReasoning
                            ),
                            apiKey
                        )
                    }.onSuccess(onSaved).onFailure { message = it.message ?: "Проверь настройки подключения." }
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
    var providers by remember { mutableStateOf(manager.providers()) }
    var preferences by remember { mutableStateOf(manager.agentPreferences()) }
    var providerMenu by remember { mutableStateOf(false) }
    var savedNotice by remember { mutableStateOf(false) }

    fun update(next: AgentPreferences) {
        preferences = next
        manager.saveAgentPreferences(next)
        savedNotice = true
    }

    val active = providers.firstOrNull { it.id == preferences.activeProviderId }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Рабочее пространство агента", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Выбери модель, глубину размышления и инструменты, которыми агенту разрешено пользоваться.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("АКТИВНАЯ МОДЕЛЬ", color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.1f)
                Box {
                    OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(active?.let { "${it.name} · ${it.model}" } ?: "Выбрать провайдера",
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Text("Запросы из чатов будут отправляться через эту модель.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("РЕЖИМ РАЗМЫШЛЕНИЯ", color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.1f)
                ReasoningMode.entries.forEach { mode ->
                    FilterChip(
                        selected = preferences.reasoningMode == mode,
                        onClick = { update(preferences.copy(reasoningMode = mode)) },
                        label = { Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(mode.label)
                            Text(mode.description, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    if (active?.supportsReasoning == true) "Режим передаётся нативными параметрами выбранного провайдера."
                    else "Эта модель не отмечена как поддерживающая нативный reasoning-параметр. Переключение режима само по себе не увеличит её возможности.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ИНСТРУМЕНТЫ АГЕНТА", color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.1f)
                ToolPreferenceRow(
                    title = "Калькулятор",
                    description = "Безопасное вычисление арифметических выражений",
                    checked = "calculator" in preferences.enabledTools,
                    onChecked = { enabled ->
                        update(preferences.copy(enabledTools = preferences.enabledTools.toMutableSet()
                            .apply { if (enabled) add("calculator") else remove("calculator") }.toList()))
                    }
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ToolPreferenceRow(
                    title = "Дата и время",
                    description = "Точное локальное время и часовой пояс устройства",
                    checked = "current_datetime" in preferences.enabledTools,
                    onChecked = { enabled ->
                        update(preferences.copy(enabledTools = preferences.enabledTools.toMutableSet()
                            .apply { if (enabled) add("current_datetime") else remove("current_datetime") }.toList()))
                    }
                )
                Text("Новые инструменты подключаются через реестр функций; каждое действие проверяется приложением, а не выполняется напрямую текстом модели.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ИНСТРУКЦИИ АГЕНТА", fontWeight = FontWeight.Bold)
                Text("Задай специализацию и стиль работы. Не помещай сюда пароли или API-ключи.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        AnimatedVisibility(savedNotice, enter = fadeIn(), exit = fadeOut()) {
            Text("Настройки сохранены", color = MaterialTheme.colorScheme.secondary,
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
