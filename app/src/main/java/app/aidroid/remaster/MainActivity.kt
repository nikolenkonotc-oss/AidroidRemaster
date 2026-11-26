package app.aidroid.remaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Purple = Color(0xFFAA8BFF)
private val Cyan = Color(0xFF82DCEB)
private val DarkBackground = Color(0xFF0B0A12)
private val DarkSurface = Color(0xFF171522)

private enum class Screen(val label: String) {
    HOME("Главная"),
    CHAT("Чат"),
    AGENT("Агент"),
    MODELS("Модели"),
    SETTINGS("Настройки")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AidroidApp()
        }
    }
}

@Composable
private fun AidroidApp() {
    var darkTheme by rememberSaveable { mutableStateOf(true) }
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.HOME) }

    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Purple,
            secondary = Cyan,
            background = DarkBackground,
            surface = DarkSurface,
            onBackground = Color(0xFFF4F0FF),
            onSurface = Color(0xFFF4F0FF),
            surfaceVariant = Color(0xFF242132),
            onSurfaceVariant = Color(0xFFB7B1C9)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF7050C9),
            secondary = Color(0xFF287E91),
            background = Color(0xFFF7F5FC),
            surface = Color.White,
            surfaceVariant = Color(0xFFECE8F5)
        )
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopBar(
                    onSettings = { selectedScreen = Screen.SETTINGS }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val icons = listOf(
                        Icons.Filled.Home,
                        Icons.Filled.Chat,
                        Icons.Filled.SmartToy,
                        Icons.Filled.Storage,
                        Icons.Filled.Settings
                    )

                    Screen.values().forEachIndexed { index, screen ->
                        NavigationBarItem(
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen },
                            icon = {
                                Icon(icons[index], contentDescription = screen.label)
                            },
                            label = { Text(screen.label, fontSize = 10.sp) },
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedScreen) {
                    Screen.HOME -> HomeScreen(
                        onOpenChat = { selectedScreen = Screen.CHAT },
                        onOpenModels = { selectedScreen = Screen.MODELS }
                    )
                    Screen.CHAT -> ChatScreen()
                    Screen.AGENT -> AgentScreen()
                    Screen.MODELS -> ModelsScreen()
                    Screen.SETTINGS -> SettingsScreen(
                        darkTheme = darkTheme,
                        onThemeChange = { darkTheme = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(listOf(Purple, Color(0xFF6950B9)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            Spacer(Modifier.width(11.dp))

            Column {
                Text(
                    "AIDROID",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "REMASTER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 2.sp
                )
            }
        }

        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ТВОЁ AI-ПРОСТРАНСТВО",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp
                )
                Text(
                    "Твой AI.\nТвои правила.",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 42.sp
                )
                Text(
                    "Локальные модели, разные провайдеры и управляемые действия — в одном пространстве.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF29203F),
                                    Color(0xFF171522),
                                    Color(0xFF14232D)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Cyan)
                            )
                            Text(
                                "СИСТЕМА ГОТОВА",
                                color = Cyan,
                                style = MaterialTheme.typography.labelMedium,
                                letterSpacing = 1.5.sp
                            )
                        }

                        Text(
                            "Начни разговор\nс Aidroid",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Твой персональный AI-интерфейс. Подключение моделей появится на следующем этапе.",
                            color = Color(0xFFD0C9E0),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = onOpenChat,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Открыть чат")
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.ArrowForward, null)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "БЫСТРЫЙ ДОСТУП",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Storage,
                    title = "Модели",
                    subtitle = "AI-провайдеры",
                    onClick = onOpenModels
                )
                QuickCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.SmartToy,
                    title = "Агент",
                    subtitle = "Инструменты",
                    onClick = {
                        // Раздел агента будет доступен через навигацию.
                    }
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Архитектура Aidroid", fontWeight = FontWeight.SemiBold)
                    Text(
                        "LOCAL-FIRST  •  PROVIDER-AGNOSTIC  •  USER-CONTROLLED",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "Твои данные и выбор моделей остаются под твоим контролем.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChatScreen() {
    var input by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Новый разговор",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Демонстрационный режим · AI пока не подключён",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(16.dp))

        if (messages.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("С чего начнём?")
                    Text(
                        "Напиши первое сообщение",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                message,
                                Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение...") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (input.isNotBlank()) {
                            messages.add(input.trim())
                            input = ""
                        }
                    }
                )
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        messages.add(input.trim())
                        input = ""
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Filled.Send, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun AgentScreen() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Agent Workspace",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Управление действиями и инструментами AI.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            InfoCard(
                Icons.Filled.SmartToy,
                "Agent Runtime",
                "Среда выполнения агента пока не подключена."
            )
        }
        item {
            InfoCard(
                Icons.Filled.Security,
                "Контроль действий",
                "В будущем опасные и чувствительные действия будут требовать подтверждения."
            )
        }
        item {
            InfoCard(
                Icons.Filled.History,
                "История операций",
                "Здесь появятся прозрачные журналы действий агента."
            )
        }
    }
}

@Composable
private fun ModelsScreen() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Модели и провайдеры",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Управляй подключениями и выбором AI.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            InfoCard(
                Icons.Filled.PhoneAndroid,
                "Локальные модели",
                "Подготовка поддержки GGUF и llama.cpp."
            )
        }
        item {
            InfoCard(
                Icons.Filled.Cloud,
                "Облачные провайдеры",
                "Подготовка OpenAI, Gemini, Anthropic и OpenAI-compatible API."
            )
        }
        item {
            InfoCard(
                Icons.Filled.Tune,
                "Hybrid Model Router",
                "Выбор модели вручную или по пользовательским правилам."
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Настройки",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Тёмная тема", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Внешний вид приложения",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = onThemeChange
                    )
                }
            }
        }
        item {
            InfoCard(
                Icons.Filled.Security,
                "Приватность",
                "Настройки хранения данных и разрешений появятся здесь."
            )
        }
        item {
            InfoCard(
                Icons.Filled.Info,
                "О приложении",
                "Aidroid Remaster · 0.2.0-alpha.1"
            )
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
