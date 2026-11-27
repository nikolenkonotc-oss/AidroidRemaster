package app.aidroid.remaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import app.aidroid.remaster.data.ChatRepository
import app.aidroid.remaster.data.local.AidroidDatabase
import app.aidroid.remaster.data.local.ChatEntity
import app.aidroid.remaster.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val Violet = Color(0xFF9270F5)
private val VioletBright = Color(0xFFB69AFF)
private val Cyan = Color(0xFF77DCEB)

private enum class Screen(val label: String) {
    HOME("Главная"), CHAT("Чаты"), AGENT("Агент"), MODELS("Модели"), SETTINGS("Настройки")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(
            applicationContext,
            AidroidDatabase::class.java,
            "aidroid-remaster.db"
        ).build()
        val repository = ChatRepository(database.chatDao())
        setContent { AidroidApp(repository) }
    }
}

@Composable
private fun AidroidApp(repository: ChatRepository) {
    var darkTheme by rememberSaveable { mutableStateOf(true) }
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var selectedChatId by rememberSaveable { mutableStateOf<Long?>(null) }

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = VioletBright,
            onPrimary = Color(0xFF21143D),
            secondary = Cyan,
            background = Color(0xFF0B0A13),
            surface = Color(0xFF171522),
            surfaceVariant = Color(0xFF252235),
            onBackground = Color(0xFFF6F2FF),
            onSurface = Color(0xFFF6F2FF),
            onSurfaceVariant = Color(0xFFBDB6D0),
            primaryContainer = Color(0xFF342653),
            onPrimaryContainer = Color(0xFFEDE4FF)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6745C5),
            onPrimary = Color.White,
            secondary = Color(0xFF24798B),
            background = Color(0xFFF5F2FC),
            surface = Color(0xFFFFFEFF),
            surfaceVariant = Color(0xFFEAE5F5),
            onBackground = Color(0xFF211B30),
            onSurface = Color(0xFF211B30),
            onSurfaceVariant = Color(0xFF625B70),
            primaryContainer = Color(0xFFE9DFFF),
            onPrimaryContainer = Color(0xFF2C1A55)
        )
    }

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize()) {
            AuroraBackdrop(darkTheme)
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = {
                    AidroidTopBar(
                        onSettings = { selectedScreen = Screen.SETTINGS },
                        onNewChat = {
                            selectedScreen = Screen.CHAT
                            selectedChatId = null
                        }
                    )
                },
                bottomBar = {
                    AidroidBottomBar(selectedScreen = selectedScreen, onSelect = { selectedScreen = it })
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedContent(
                        targetState = selectedScreen,
                        transitionSpec = {
                            (fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220)))
                                .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.99f, animationSpec = tween(150)))
                        },
                        label = "main-screen"
                    ) { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                onOpenChats = { selectedScreen = Screen.CHAT; selectedChatId = null },
                                onOpenModels = { selectedScreen = Screen.MODELS }
                            )
                            Screen.CHAT -> ChatWorkspace(
                                repository = repository,
                                selectedChatId = selectedChatId,
                                onSelectChat = { selectedChatId = it },
                                onBackToChats = { selectedChatId = null }
                            )
                            Screen.AGENT -> PlaceholderScreen("Рабочее пространство агента", "Здесь появятся инструменты, задачи и журнал действий агента.",
                                Icons.Filled.SmartToy
                            )
                            Screen.MODELS -> PlaceholderScreen("Модели и провайдеры", "Локальные модели, облачные API и Hybrid Model Router.",
                                Icons.Filled.Storage
                            )
                            Screen.SETTINGS -> SettingsScreen(darkTheme) { darkTheme = it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraBackdrop(dark: Boolean) {
    val transition = rememberInfiniteTransition(label = "aurora-glass")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aurora-phase"
    )
    val drift by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(24000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aurora-drift"
    )
    val base = if (dark) Color(0xFF090914) else Color(0xFFF4F2FB)
    val violet = if (dark) Color(0xFF7351C7) else Color(0xFFB7A0F0)
    val aqua = if (dark) Color(0xFF176B79) else Color(0xFF9CDCE0)
    val rose = if (dark) Color(0xFF633E7B) else Color(0xFFE3C9EF)

    Canvas(Modifier.fillMaxSize().background(base)) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = if (dark) listOf(Color(0xFF111023), Color(0xFF0A1420), Color(0xFF100D1C))
                else listOf(Color(0xFFF8F5FF), Color(0xFFEAF6F7), Color(0xFFF6F0FC)),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(violet.copy(alpha = if (dark) 0.38f else 0.34f), Color.Transparent)),
            radius = size.minDimension * 0.78f,
            center = Offset(w * (0.12f + phase * 0.34f), h * (0.12f + drift * 0.08f))
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(aqua.copy(alpha = if (dark) 0.28f else 0.38f), Color.Transparent)),
            radius = size.minDimension * 0.72f,
            center = Offset(w * (0.92f - phase * 0.28f), h * (0.67f - drift * 0.12f))
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(rose.copy(alpha = if (dark) 0.18f else 0.30f), Color.Transparent)),
            radius = size.minDimension * 0.56f,
            center = Offset(w * (0.58f - phase * 0.12f), h * (0.94f - drift * 0.18f))
        )
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 26,
    highlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.red < 0.2f
    val shape = RoundedCornerShape(cornerRadius.dp)
    val baseTop = if (dark) Color(0xFF27253A).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.82f)
    val baseBottom = if (dark) Color(0xFF111827).copy(alpha = 0.82f) else Color(0xFFEDEBFA).copy(alpha = 0.72f)
    val edge = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.94f)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .shadow(if (dark) 24.dp else 16.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        if (highlighted) accent.copy(alpha = if (dark) 0.34f else 0.20f) else baseTop,
                        baseBottom,
                        if (dark) Color(0xFF182A35).copy(alpha = 0.62f) else Color(0xFFE8F6F5).copy(alpha = 0.48f)
                    )
                )
            )
            .border(1.dp, Brush.linearGradient(listOf(edge, accent.copy(alpha = if (dark) 0.24f else 0.18f), edge.copy(alpha = 0.42f))), shape)
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = if (dark) 0.17f else 0.10f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 760f
                )
            )
        )
        Box(
            Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, edge, Color.Transparent)))
        )
        content()
    }
}
@Composable
private fun AidroidTopBar(onSettings: () -> Unit, onNewChat: () -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        cornerRadius = 25
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(43.dp).shadow(10.dp, RoundedCornerShape(15.dp), clip = false)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFC2A6FF), Violet, Color(0xFF3D9EAC))))
                        .border(1.dp, Color.White.copy(alpha = 0.52f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Aidroid Remaster", modifier = Modifier.size(29.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("AIDROID", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                    Text("REMASTER  /  AI STUDIO", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.25.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val buttonShape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.size(42.dp).clip(buttonShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), buttonShape)
                        .clickable(onClick = onNewChat),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.AddComment, "Новый чат", tint = MaterialTheme.colorScheme.onBackground) }
                Box(
                    Modifier.size(42.dp).clip(buttonShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), buttonShape)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Settings, "Настройки", tint = MaterialTheme.colorScheme.onBackground) }
            }
        }
    }
}
@Composable
private fun HomeScreen(onOpenChats: () -> Unit, onOpenModels: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < 0.2f
    val shimmerTransition = rememberInfiniteTransition(label = "hero-sheen")
    val shimmer by shimmerTransition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(7200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "hero-sheen-offset"
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("ТВОЁ AI-ПРОСТРАНСТВО", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.1.sp)
                Text("Твой AI.\nТвои правила.", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, lineHeight = 41.sp)
                Text("Модели, диалоги и инструменты — в едином пространстве, под твоим контролем.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            val heroText = if (dark) Color(0xFFF9F6FF) else Color(0xFF241A38)
            val heroMuted = if (dark) Color(0xFFD8D0E8) else Color(0xFF625873)
            val heroAccent = if (dark) Color(0xFF91E5E8) else Color(0xFF287B86)
            GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 31, highlighted = true) {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.size(190.dp).align(Alignment.TopEnd).graphicsLayer { rotationZ = shimmer * 16f }
                            .background(
                                Brush.radialGradient(
                                    listOf(if (dark) Color(0xFF9E7AF4).copy(alpha = 0.30f) else Color(0xFFB6A1F0).copy(alpha = 0.34f), Color.Transparent),
                                    radius = 260f
                                ), CircleShape
                            )
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.075f else 0.30f), Color.Transparent),
                                start = Offset(shimmer * 760f, 0f),
                                end = Offset(shimmer * 760f + 380f, 560f)
                            )
                        )
                    )
                    Column(Modifier.fillMaxWidth().padding(21.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(heroAccent, CircleShape))
                            Text("СИСТЕМА ГОТОВА", color = heroAccent, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                            Spacer(Modifier.weight(1f))
                            Text("●  LOCAL FIRST", color = heroMuted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                        Text("Продолжим\nразговор?", color = heroText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                        Text("Твои диалоги сохраняются локально и остаются под твоим контролем.", color = heroMuted)
                        GlowButton("Открыть чаты", onOpenChats)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(Modifier.weight(1f), Icons.Filled.Chat, "Мои чаты", "История и отдельные контексты", onOpenChats)
                QuickCard(Modifier.weight(1f), Icons.Filled.Storage, "Модели", "Провайдеры и локальный AI", onOpenModels)
            }
        }
        item {
            InfoCard(Icons.Filled.Security, "Приватность прежде всего", "История диалогов хранится на устройстве. Внешняя передача данных пока не включена.")
        }
    }
}
@Composable
private fun GlowButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val glow by animateDpAsState(
        targetValue = when { pressed -> 27.dp; hovered -> 23.dp; else -> 9.dp },
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "glass-button-glow"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else if (hovered) 1.025f else 1f,
        animationSpec = tween(190),
        label = "glass-button-scale"
    )
    val shape = RoundedCornerShape(19.dp)
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(glow, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFC2A7FF), Violet, Color(0xFF4A78B9))))
            .border(1.dp, Color.White.copy(alpha = if (pressed || hovered) 0.82f else 0.45f), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent))))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun QuickCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(180), label = "quick-card-scale")
    GlassPanel(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        cornerRadius = 23
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(39.dp).clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.23f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.13f))))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
private fun AidroidBottomBar(selectedScreen: Screen, onSelect: (Screen) -> Unit) {
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Chat, Icons.Filled.SmartToy, Icons.Filled.Storage, Icons.Filled.Settings)
    GlassPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 7.dp),
        cornerRadius = 28
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Screen.values().forEachIndexed { index, screen ->
                val selected = selectedScreen == screen
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val hovered by interaction.collectIsHoveredAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.91f else if (selected) 1.035f else if (hovered) 1.045f else 1f,
                    animationSpec = tween(190, easing = FastOutSlowInEasing),
                    label = "nav-scale-${screen.name}"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(240),
                    label = "nav-tint-${screen.name}"
                )
                val indicatorAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "nav-indicator-${screen.name}")
                Column(
                    Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(20.dp))
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(screen) }
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 56.dp, height = 34.dp)) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer { alpha = indicatorAlpha }
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.17f), CircleShape)
                        )
                        Icon(icons[index], contentDescription = screen.label, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        screen.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
@Composable
private fun ChatWorkspace(
    repository: ChatRepository,
    selectedChatId: Long?,
    onSelectChat: (Long?) -> Unit,
    onBackToChats: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val chats by (if (showArchived) repository.archivedChats() else repository.activeChats())
        .collectAsState(initial = emptyList())
    val selectedChat = chats.firstOrNull { it.id == selectedChatId }
    val messagesFlow: Flow<List<MessageEntity>>? = selectedChatId?.let { repository.messages(it) }
    val messages by (messagesFlow ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())


    if (selectedChatId == null || selectedChat == null) {
        ChatListScreen(
            chats = chats,
            archived = showArchived,
            onToggleArchived = { showArchived = it },
            onCreate = {
                scope.launch {
                    val id = repository.createChat()
                    onSelectChat(id)
                }
            },
            onOpen = { onSelectChat(it) },
            onRename = { chat, title -> scope.launch { repository.rename(chat.id, title) } },
            onPin = { chat -> scope.launch { repository.setPinned(chat, !chat.isPinned) } },
            onArchive = { chat -> scope.launch { repository.setArchived(chat, !chat.isArchived) } },
            onDelete = { chat -> scope.launch { repository.delete(chat) } }
        )
    } else {
        ConversationScreen(
            chat = selectedChat,
            messages = messages,
            onBack = onBackToChats,
            onSend = { text -> scope.launch { repository.sendUserMessage(selectedChat.id, text) } },
            onRename = { title -> scope.launch { repository.rename(selectedChat.id, title) } }
        )
    }
}

@Composable
private fun ChatListScreen(
    chats: List<ChatEntity>,
    archived: Boolean,
    onToggleArchived: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (ChatEntity, String) -> Unit,
    onPin: (ChatEntity) -> Unit,
    onArchive: (ChatEntity) -> Unit,
    onDelete: (ChatEntity) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ChatEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatEntity?>(null) }
    val filtered = chats.filter { it.title.contains(query, ignoreCase = true) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (archived) "Архив" else "Мои чаты",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (archived) "${filtered.size} сохранённых диалогов" else "${filtered.size} диалогов",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val addShape = CircleShape
            Box(
                Modifier.size(54.dp)
                    .shadow(12.dp, addShape, clip = false)
                    .clip(addShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFB99AFF), Color(0xFF8061E8), Color(0xFF4E9ED0))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.48f), addShape)
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Создать чат",
                    tint = Color.White,
                    modifier = Modifier.size(29.dp)
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 21) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 3.dp),
                placeholder = { Text("Поиск по чатам") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить поиск")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(false to "Все чаты", true to "Архив").forEach { (isArchive, label) ->
                val selected = archived == isArchive
                Row(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
                                )
                            ) else Brush.linearGradient(
                                listOf(Color.Transparent, Color.Transparent)
                            )
                        )
                        .border(
                            if (selected) 1.dp else 0.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            if (archived != isArchive) {
                                onToggleArchived(isArchive)
                                query = ""
                            }
                        }
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isArchive) Icons.Filled.Archive else Icons.Filled.Forum,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        label,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 28, highlighted = true) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(
                            Modifier.size(76.dp)
                                .shadow(18.dp, CircleShape, clip = false)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (query.isBlank()) Icons.Filled.AutoAwesome else Icons.Filled.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Text(
                            if (query.isBlank()) "Твоё пространство диалогов" else "Ничего не найдено",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (query.isBlank()) {
                                "Создай новый чат — история и контекст будут храниться на устройстве."
                            } else {
                                "Попробуй изменить поисковый запрос."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (query.isBlank() && !archived) {
                            Spacer(Modifier.height(2.dp))
                            GlowButton("Создать первый чат", onCreate)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 1.dp, bottom = 18.dp)
            ) {
                items(filtered, key = { it.id }) { chat ->
                    ChatListItem(
                        chat,
                        onOpen = { onOpen(chat.id) },
                        onRename = { renameTarget = chat },
                        onPin = { onPin(chat) },
                        onArchive = { onArchive(chat) },
                        onDelete = { deleteTarget = chat }
                    )
                }
            }
        }
    }

    renameTarget?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать чат") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Название") }) },
            confirmButton = { TextButton(onClick = { onRename(chat, title); renameTarget = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена") } }
        )
    }
    deleteTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить диалог?") },
            text = { Text("«${chat.title}» и все его сообщения будут удалены без возможности восстановления.") },
            confirmButton = { TextButton(onClick = { onDelete(chat); deleteTarget = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun ChatListItem(
    chat: ChatEntity,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(160), label = "chat-row-scale")
    GlassPanel(
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
        cornerRadius = 21
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 13.dp, end = 5.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(45.dp).clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.17f))))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.17f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (chat.isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.PushPin, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    if (chat.isArchived) "В архиве" else "Локальный диалог · ${android.text.format.DateFormat.format("dd.MM · HH:mm", chat.updatedAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Меню чата", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(if (chat.isPinned) "Открепить" else "Закрепить") }, leadingIcon = { Icon(Icons.Filled.PushPin, null) }, onClick = { menu = false; onPin() })
                    DropdownMenuItem(text = { Text(if (chat.isArchived) "Вернуть из архива" else "Архивировать") }, leadingIcon = { Icon(Icons.Filled.Archive, null) }, onClick = { menu = false; onArchive() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
@Composable
private fun ConversationScreen(
    chat: ChatEntity,
    messages: List<MessageEntity>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onRename: (String) -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var localText by rememberSaveable(chat.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp), cornerRadius = 21) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "К списку чатов", tint = MaterialTheme.colorScheme.onSurface) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(chat.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Отдельная история · хранится локально", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { showRename = true }) { Icon(Icons.Filled.Edit, "Переименовать", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        AnimatedVisibility(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            visible = messages.isEmpty(),
            enter = fadeIn(tween(280)) + scaleIn(initialScale = 0.96f, animationSpec = tween(280)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.98f, animationSpec = tween(160))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 27) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)))),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp)) }
                        Text("С чего начнём?", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Сообщения этого чата не смешиваются с другими.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (messages.isNotEmpty()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        GlassPanel(modifier = Modifier.widthIn(max = 320.dp), cornerRadius = 21, highlighted = isUser) {
                            Text(
                                message.content,
                                Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        GlassPanel(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 9.dp), cornerRadius = 25) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedTextField(
                    value = localText,
                    onValueChange = { localText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    shape = RoundedCornerShape(19.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (localText.isNotBlank()) { onSend(localText); localText = "" }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                GlowSendButton(enabled = localText.isNotBlank()) {
                    if (localText.isNotBlank()) { onSend(localText); localText = "" }
                }
            }
        }
    }
    if (showRename) {
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Название чата") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(title); showRename = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Отмена") } }
        )
    }
}
@Composable
private fun GlowSendButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val elevation by animateDpAsState(if (enabled) 16.dp else 2.dp, tween(220), label = "send-glow")
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, tween(150), label = "send-scale")
    Box(
        Modifier.size(51.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                if (enabled) Brush.linearGradient(listOf(Color(0xFFC4A8FF), Violet, Color(0xFF3E94A2)))
                else Brush.linearGradient(listOf(Color(0xFF807A90), Color(0xFF5C5969)))
            )
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.62f else 0.20f), CircleShape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}
@Composable
private fun PlaceholderScreen(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxSize().padding(19.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 28) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)))),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp)) }
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("РАЗДЕЛ В РАЗРАБОТКЕ", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp)
            }
        }
    }
}
@Composable
private fun SettingsScreen(darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("Настройки", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Тёмная тема", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Переключение между Aurora Dark и Aurora Light", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = darkTheme, onCheckedChange = onThemeChange)
            }
        }
        InfoCard(Icons.Filled.Security, "Локальное хранение", "История чатов хранится в базе Room на устройстве. Внешняя передача пока не выполняется.")
        InfoCard(Icons.Filled.Info, "О приложении", "Aidroid Remaster · 0.2.0-alpha.3 · Aurora Glass")
    }
}
@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 23) {
        Row(
            Modifier.fillMaxWidth().padding(17.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(43.dp).clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)))),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}