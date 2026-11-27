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
    HOME("Р“Р»Р°РІРЅР°СЏ"), CHAT("Р§Р°С‚С‹"), AGENT("РђРіРµРЅС‚"), MODELS("РњРѕРґРµР»Рё"), SETTINGS("РќР°СЃС‚СЂРѕР№РєРё")
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
                            Screen.AGENT -> PlaceholderScreen(
                                "Agent Workspace", "РЎСЂРµРґР° РІС‹РїРѕР»РЅРµРЅРёСЏ Р°РіРµРЅС‚Р° Рё РёРЅСЃС‚СЂСѓРјРµРЅС‚С‹ РїРѕСЏРІСЏС‚СЃСЏ Р·РґРµСЃСЊ.",
                                Icons.Filled.SmartToy
                            )
                            Screen.MODELS -> PlaceholderScreen(
                                "РњРѕРґРµР»Рё Рё РїСЂРѕРІР°Р№РґРµСЂС‹", "Р›РѕРєР°Р»СЊРЅС‹Рµ РјРѕРґРµР»Рё, РѕР±Р»Р°С‡РЅС‹Рµ API Рё Hybrid Model Router.",
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
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aurora-phase"
    )
    val bg = if (dark) Color(0xFF0B0A13) else Color(0xFFF5F2FC)
    val a = if (dark) Color(0xFF563C91) else Color(0xFFC9B7F6)
    val b = if (dark) Color(0xFF174B5B) else Color(0xFFB8E5E9)
    Canvas(Modifier.fillMaxSize().background(bg)) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(listOf(a.copy(alpha = if (dark) 0.34f else 0.34f), Color.Transparent)),
            radius = size.minDimension * 0.72f,
            center = Offset(w * (0.18f + phase * 0.24f), h * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(b.copy(alpha = if (dark) 0.25f else 0.40f), Color.Transparent)),
            radius = size.minDimension * 0.68f,
            center = Offset(w * (0.88f - phase * 0.22f), h * 0.64f)
        )
    }
}

@Composable
private fun AidroidTopBar(onSettings: () -> Unit, onNewChat: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Violet, Color(0xFF4F3A91)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White) }
            Spacer(Modifier.width(11.dp))
            Column {
                Text("AIDROID", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("REMASTER", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNewChat) { Icon(Icons.Filled.AddComment, "РќРѕРІС‹Р№ С‡Р°С‚", tint = MaterialTheme.colorScheme.onBackground) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "РќР°СЃС‚СЂРѕР№РєРё", tint = MaterialTheme.colorScheme.onBackground) }
        }
    }
}

@Composable
private fun HomeScreen(onOpenChats: () -> Unit, onOpenModels: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("РўР’РћРЃ AI-РџР РћРЎРўР РђРќРЎРўР’Рћ", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                Text("РўРІРѕР№ AI.\nРўРІРѕРё РїСЂР°РІРёР»Р°.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
                Text("Р›РѕРєР°Р»СЊРЅС‹Рµ РјРѕРґРµР»Рё, СЂР°Р·РЅС‹Рµ РїСЂРѕРІР°Р№РґРµСЂС‹ Рё СѓРїСЂР°РІР»СЏРµРјС‹Рµ РґРµР№СЃС‚РІРёСЏ вЂ” РІ РѕРґРЅРѕРј РїСЂРѕСЃС‚СЂР°РЅСЃС‚РІРµ.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            val dark = MaterialTheme.colorScheme.background.red < 0.2f
            val heroText = if (dark) Color(0xFFF9F6FF) else Color(0xFF241A38)
            val heroMuted = if (dark) Color(0xFFD8D0E8) else Color(0xFF625873)
            val heroAccent = if (dark) Cyan else Color(0xFF3B8290)
            val heroBrush = if (dark) {
                Brush.linearGradient(listOf(Color(0xFF33234F), Color(0xFF171D31), Color(0xFF12313A)))
            } else {
                Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF0E9FF), Color(0xFFE6F5F6)))
            }
            val heroBorder = if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFF8F73D6).copy(alpha = 0.22f)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().border(1.dp, heroBorder, RoundedCornerShape(28.dp))
            ) {
                Box(Modifier.background(heroBrush).padding(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(heroAccent, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("РЎРРЎРўР•РњРђ Р“РћРўРћР’Рђ", color = heroAccent, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                        }
                        Text("РџСЂРѕРґРѕР»Р¶РёРј\\nСЂР°Р·РіРѕРІРѕСЂ?", color = heroText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("РўРІРѕРё РґРёР°Р»РѕРіРё С‚РµРїРµСЂСЊ СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ Р»РѕРєР°Р»СЊРЅРѕ.", color = heroMuted)
                        GlowButton("РћС‚РєСЂС‹С‚СЊ С‡Р°С‚С‹", onOpenChats)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(Modifier.weight(1f), Icons.Filled.Chat, "РњРѕРё С‡Р°С‚С‹", "РСЃС‚РѕСЂРёСЏ Рё РѕС‚РґРµР»СЊРЅС‹Рµ РєРѕРЅС‚РµРєСЃС‚С‹", onOpenChats)
                QuickCard(Modifier.weight(1f), Icons.Filled.Storage, "РњРѕРґРµР»Рё", "РџСЂРѕРІР°Р№РґРµСЂС‹ Рё Р»РѕРєР°Р»СЊРЅС‹Р№ AI", onOpenModels)
            }
        }
        item {
            InfoCard(Icons.Filled.Security, "РџСЂРёРІР°С‚РЅРѕСЃС‚СЊ РїСЂРµР¶РґРµ РІСЃРµРіРѕ", "РСЃС‚РѕСЂРёСЏ РґРёР°Р»РѕРіРѕРІ С…СЂР°РЅРёС‚СЃСЏ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ. РџРµСЂРµРґР°С‡Р° РґР°РЅРЅС‹С… AI-РїСЂРѕРІР°Р№РґРµСЂР°Рј РїРѕРєР° РЅРµ РІРєР»СЋС‡РµРЅР°.")
        }
    }
}

@Composable
private fun GlowButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val glow by animateFloatAsState(
        targetValue = when { pressed -> 22f; hovered -> 20f; else -> 7f },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "button-glow"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else if (hovered) 1.025f else 1f,
        animationSpec = tween(180),
        label = "button-scale"
    )
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(glow.dp, RoundedCornerShape(18.dp), clip = false)
            .border(1.dp, VioletBright.copy(alpha = if (pressed || hovered) 0.9f else 0.42f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun QuickCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AidroidBottomBar(selectedScreen: Screen, onSelect: (Screen) -> Unit) {
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Chat, Icons.Filled.SmartToy, Icons.Filled.Storage, Icons.Filled.Settings)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding()
            .padding(bottom = 8.dp).shadow(18.dp, RoundedCornerShape(28.dp), clip = false)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Screen.values().forEachIndexed { index, screen ->
                val selected = selectedScreen == screen
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val hovered by interaction.collectIsHoveredAsState()
                val active = selected || pressed || hovered
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.91f else if (hovered) 1.06f else 1f,
                    animationSpec = tween(180), label = "nav-scale-${screen.name}"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180), label = "nav-icon-${screen.name}"
                )
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(19.dp)).hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(screen) }
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(19.dp))
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (active) {
                            Box(Modifier.size(38.dp).background(
                                Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.25f else 0.18f), Color.Transparent)),
                                CircleShape
                            ))
                        }
                        Icon(icons[index], contentDescription = screen.label, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        screen.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
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

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (archived) "РђСЂС…РёРІ" else "РњРѕРё С‡Р°С‚С‹", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${filtered.size} РґРёР°Р»РѕРіРѕРІ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            GlowButton("РќРѕРІС‹Р№ С‡Р°С‚", onCreate)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("РџРѕРёСЃРє РїРѕ С‡Р°С‚Р°Рј") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onToggleArchived(!archived); query = "" }) {
                Icon(if (archived) Icons.Filled.Chat else Icons.Filled.Archive, null)
                Spacer(Modifier.width(6.dp))
                Text(if (archived) "РђРєС‚РёРІРЅС‹Рµ" else "РђСЂС…РёРІ")
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Forum, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Text(if (query.isBlank()) "Р—РґРµСЃСЊ РїРѕРєР° РїСѓСЃС‚Рѕ" else "РќРёС‡РµРіРѕ РЅРµ РЅР°Р№РґРµРЅРѕ", fontWeight = FontWeight.SemiBold)
                    Text(if (query.isBlank()) "РЎРѕР·РґР°Р№ РїРµСЂРІС‹Р№ РґРёР°Р»РѕРі вЂ” РѕРЅ СЃРѕС…СЂР°РЅРёС‚СЃСЏ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ." else "РџРѕРїСЂРѕР±СѓР№ РёР·РјРµРЅРёС‚СЊ Р·Р°РїСЂРѕСЃ.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(filtered, key = { it.id }) { chat ->
                    ChatListItem(chat, onOpen = { onOpen(chat.id) }, onRename = { renameTarget = chat }, onPin = { onPin(chat) }, onArchive = { onArchive(chat) }, onDelete = { deleteTarget = chat })
                }
            }
        }
    }

    renameTarget?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("РџРµСЂРµРёРјРµРЅРѕРІР°С‚СЊ С‡Р°С‚") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("РќР°Р·РІР°РЅРёРµ") }) },
            confirmButton = { TextButton(onClick = { onRename(chat, title); renameTarget = null }) { Text("РЎРѕС…СЂР°РЅРёС‚СЊ") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("РћС‚РјРµРЅР°") } }
        )
    }
    deleteTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("РЈРґР°Р»РёС‚СЊ РґРёР°Р»РѕРі?") },
            text = { Text("В«${chat.title}В» Рё РІСЃРµ РµРіРѕ СЃРѕРѕР±С‰РµРЅРёСЏ Р±СѓРґСѓС‚ СѓРґР°Р»РµРЅС‹ Р±РµР· РІРѕР·РјРѕР¶РЅРѕСЃС‚Рё РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ.") },
            confirmButton = { TextButton(onClick = { onDelete(chat); deleteTarget = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("РЈРґР°Р»РёС‚СЊ") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("РћС‚РјРµРЅР°") } }
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 13.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (chat.isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.PushPin, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(if (chat.isArchived) "Р’ Р°СЂС…РёРІРµ" else "Р›РѕРєР°Р»СЊРЅС‹Р№ РґРёР°Р»РѕРі В· ${android.text.format.DateFormat.format("dd.MM В· HH:mm", chat.updatedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "РњРµРЅСЋ С‡Р°С‚Р°") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("РџРµСЂРµРёРјРµРЅРѕРІР°С‚СЊ") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(if (chat.isPinned) "РћС‚РєСЂРµРїРёС‚СЊ" else "Р—Р°РєСЂРµРїРёС‚СЊ") }, leadingIcon = { Icon(Icons.Filled.PushPin, null) }, onClick = { menu = false; onPin() })
                    DropdownMenuItem(text = { Text(if (chat.isArchived) "Р’РµСЂРЅСѓС‚СЊ РёР· Р°СЂС…РёРІР°" else "РђСЂС…РёРІРёСЂРѕРІР°С‚СЊ") }, leadingIcon = { Icon(Icons.Filled.Archive, null) }, onClick = { menu = false; onArchive() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("РЈРґР°Р»РёС‚СЊ", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Рљ СЃРїРёСЃРєСѓ С‡Р°С‚РѕРІ") }
            Column(Modifier.weight(1f)) {
                Text(chat.title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("РћС‚РґРµР»СЊРЅР°СЏ РёСЃС‚РѕСЂРёСЏ В· С…СЂР°РЅРёС‚СЃСЏ Р»РѕРєР°Р»СЊРЅРѕ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { showRename = true }) { Icon(Icons.Filled.Edit, "РџРµСЂРµРёРјРµРЅРѕРІР°С‚СЊ") }
        }
        AnimatedVisibility(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            visible = messages.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                    Text("РЎ С‡РµРіРѕ РЅР°С‡РЅС‘Рј?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("РЎРѕРѕР±С‰РµРЅРёСЏ СЌС‚РѕРіРѕ С‡Р°С‚Р° РЅРµ СЃРјРµС€РёРІР°СЋС‚СЃСЏ СЃ РґСЂСѓРіРёРјРё.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (messages.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Text(message.content, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var localText by rememberSaveable(chat.id) { mutableStateOf("") }
            OutlinedTextField(
                value = localText, onValueChange = { localText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("РЎРѕРѕР±С‰РµРЅРёРµ...") },
                shape = RoundedCornerShape(22.dp), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (localText.isNotBlank()) { onSend(localText); localText = "" } })
            )
            GlowSendButton(enabled = localText.isNotBlank()) {
                onSend(localText); localText = ""
            }
        }
    }
    if (showRename) {
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("РќР°Р·РІР°РЅРёРµ С‡Р°С‚Р°") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(title); showRename = false }) { Text("РЎРѕС…СЂР°РЅРёС‚СЊ") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("РћС‚РјРµРЅР°") } }
        )
    }
}

@Composable
private fun GlowSendButton(enabled: Boolean, onClick: () -> Unit) {
    val elevation by animateDpAsState(if (enabled) 12.dp else 2.dp, label = "send-glow")
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp).shadow(elevation, CircleShape).background(MaterialTheme.colorScheme.primary, CircleShape)
    ) { Icon(Icons.Filled.Send, null, tint = Color.White) }
}

@Composable
private fun PlaceholderScreen(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        InfoCard(icon, title, description)
    }
}

@Composable
private fun SettingsScreen(darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("РќР°СЃС‚СЂРѕР№РєРё", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), contentColor = MaterialTheme.colorScheme.onSurface)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("РўС‘РјРЅР°СЏ С‚РµРјР°", fontWeight = FontWeight.SemiBold)
                    Text("РџРµСЂРµРєР»СЋС‡РµРЅРёРµ РјРµР¶РґСѓ Aurora Dark Рё Aurora Light", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = darkTheme, onCheckedChange = onThemeChange)
            }
        }
        InfoCard(Icons.Filled.Security, "Р›РѕРєР°Р»СЊРЅРѕРµ С…СЂР°РЅРµРЅРёРµ", "РСЃС‚РѕСЂРёСЏ С‡Р°С‚РѕРІ С…СЂР°РЅРёС‚СЃСЏ РІ Р±Р°Р·Рµ Room РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ. Р’РЅРµС€РЅСЏСЏ РїРµСЂРµРґР°С‡Р° РїРѕРєР° РЅРµ РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ.")
        InfoCard(Icons.Filled.Info, "Рћ РїСЂРёР»РѕР¶РµРЅРёРё", "Aidroid Remaster В· 0.2.0-alpha.3")
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), contentColor = MaterialTheme.colorScheme.onSurface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

