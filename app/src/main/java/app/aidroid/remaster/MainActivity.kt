package app.aidroid.remaster

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.aidroid.remaster.data.ChatRepository
import app.aidroid.remaster.data.attachments.AttachmentJson
import app.aidroid.remaster.data.attachments.AttachmentStorage
import app.aidroid.remaster.data.attachments.StoredAttachment
import app.aidroid.remaster.data.local.AidroidDatabase
import app.aidroid.remaster.data.local.ChatEntity
import app.aidroid.remaster.data.local.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.launch

private val Violet = Color(0xFF9270F5)
private val VioletBright = Color(0xFFB69AFF)
private val Cyan = Color(0xFF77DCEB)

private enum class Screen(val label: String) {
    HOME("Главная"), CHAT("Чаты"), AGENT("Агент"), MODELS("Модели"), SETTINGS("Настройки")
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(
            applicationContext,
            AidroidDatabase::class.java,
            "aidroid-remaster.db"
        ).addMigrations(MIGRATION_1_2).build()
        val attachmentStorage = AttachmentStorage(applicationContext)
        val repository = ChatRepository(database.chatDao(), attachmentStorage)
        setContent { AidroidApp(repository) }
    }
}

@Composable
private fun AidroidApp(repository: ChatRepository) {
    var darkTheme by rememberSaveable { mutableStateOf(true) }
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var selectedChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    val chatLists by produceState<Pair<List<ChatEntity>, List<ChatEntity>>?>(
        initialValue = null,
        key1 = repository
    ) {
        combine(repository.activeChats(), repository.archivedChats()) { active, archived ->
            active to archived
        }.collect { value = it }
    }

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
            primary = Color(0xFF6040B8),
            onPrimary = Color.White,
            secondary = Color(0xFF216F7D),
            background = Color(0xFFF1F0F7),
            surface = Color(0xFFFCFBFF),
            surfaceVariant = Color(0xFFE9E7F0),
            onBackground = Color(0xFF211D2D),
            onSurface = Color(0xFF211D2D),
            onSurfaceVariant = Color(0xFF5C566B),
            outline = Color(0xFF817A91),
            outlineVariant = Color(0xFFD8D3E2),
            primaryContainer = Color(0xFFE8E1FA),
            onPrimaryContainer = Color(0xFF2C1A55),
            errorContainer = Color(0xFFFFE8EC),
            onErrorContainer = Color(0xFF5A1728)
        )
    }

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().background(scheme.background)) {
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
                        modifier = Modifier.fillMaxSize(),
                        targetState = selectedScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                                .togetherWith(fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)))
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
                                chatLists = chatLists,
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
            brush = Brush.radialGradient(listOf(violet.copy(alpha = if (dark) 0.38f else 0.085f), Color.Transparent)),
            radius = size.minDimension * 0.78f,
            center = Offset(w * (0.12f + phase * 0.34f), h * (0.12f + drift * 0.08f))
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(aqua.copy(alpha = if (dark) 0.28f else 0.095f), Color.Transparent)),
            radius = size.minDimension * 0.72f,
            center = Offset(w * (0.92f - phase * 0.28f), h * (0.67f - drift * 0.12f))
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(rose.copy(alpha = if (dark) 0.18f else 0.07f), Color.Transparent)),
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
    val accent = MaterialTheme.colorScheme.primary
    val panelColors = if (dark) {
        listOf(
            if (highlighted) Color(0xFF3B3159).copy(alpha = 0.92f) else Color(0xFF27253A).copy(alpha = 0.92f),
            Color(0xFF111827).copy(alpha = 0.95f),
            Color(0xFF182A35).copy(alpha = 0.90f)
        )
    } else {
        listOf(
            if (highlighted) Color(0xFFF4F0FF) else Color(0xFFFCFBFF),
            Color(0xFFF3F1F8),
            Color(0xFFEEF2F5)
        )
    }
    val edge = if (dark) Color.White.copy(alpha = 0.22f) else Color(0xFFD7D2E2)
    Box(
        modifier = modifier
            .shadow(if (dark) 24.dp else 10.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(panelColors))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        edge,
                        accent.copy(alpha = if (dark) 0.24f else 0.12f),
                        edge.copy(alpha = if (dark) 0.72f else 0.82f)
                    )
                ),
                shape
            )
    ) {
        if (dark) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 760f
                    )
                )
            )
        }
        Box(
            Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, edge.copy(alpha = if (dark) 0.88f else 0.58f), Color.Transparent)
                    )
                )
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
                    Image(painter = painterResource(R.mipmap.ic_launcher_foreground), contentDescription = "Aidroid Remaster", modifier = Modifier.size(35.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("AIDROID", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                    Text("REMASTER  /  AI STUDIO", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.25.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val buttonShape = CircleShape
                Box(
                    Modifier.size(36.dp).clip(buttonShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), buttonShape)
                        .pressScaleClickable(onClick = onNewChat),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Add, "Новый чат", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                Box(
                    Modifier.size(36.dp).clip(buttonShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), buttonShape)
                        .pressScaleClickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Tune, "Настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
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
    chatLists: Pair<List<ChatEntity>, List<ChatEntity>>?,
    selectedChatId: Long?,
    onSelectChat: (Long?) -> Unit,
    onBackToChats: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val activeChats = chatLists?.first.orEmpty()
    val archivedChats = chatLists?.second.orEmpty()
    val chats = if (showArchived) archivedChats else activeChats
    val allChats = if (chatLists == null) emptyList() else activeChats + archivedChats
    val loading = chatLists == null

    val messages by produceState<List<MessageEntity>?>(initialValue = null, key1 = selectedChatId) {
        value = null
        val chatId = selectedChatId ?: return@produceState
        repository.messages(chatId).collect { value = it }
    }

    AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        targetState = selectedChatId,
        transitionSpec = {
            fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                .togetherWith(fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)))
        },
        label = "chat-list-conversation-transition"
    ) { destinationChatId ->
        if (destinationChatId == null) {
            ChatListScreen(
                chats = chats,
                loading = loading,
                archived = showArchived,
                onToggleArchived = { showArchived = it },
                onCreate = {
                    showArchived = false
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
            val destinationChat = allChats.firstOrNull { it.id == destinationChatId }
            if (destinationChat == null) {
                ConversationLoadingState()
            } else {
                ConversationScreen(
                    chat = destinationChat,
                    messages = if (destinationChatId == selectedChatId) messages else null,
                    onBack = onBackToChats,
                    onSend = { text, attachments ->
                        repository.sendUserMessage(destinationChat.id, text, attachments)
                    },
                    onDeleteMessage = { message -> repository.deleteMessage(message) },
                    onRename = { title -> scope.launch { repository.rename(destinationChat.id, title) } }
                )
            }
        }
    }
}

@Composable
private fun ConversationLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassPanel(modifier = Modifier.size(92.dp), cornerRadius = 26) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ChatListScreen(
    chats: List<ChatEntity>,
    loading: Boolean,
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
                    .pressScaleClickable(onClick = onCreate),
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
                        PressAnimatedIconButton(
                            onClick = { query = "" },
                            icon = Icons.Filled.Close,
                            contentDescription = "Очистить поиск",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                .background(
                    if (MaterialTheme.colorScheme.background.red < 0.2f) MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
                    else Color(0xFFE9E6F0)
                )
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(false to "Все чаты", true to "Архив").forEach { (isArchive, label) ->
                val selected = archived == isArchive
                val tabProgress by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(230, easing = FastOutSlowInEasing),
                    label = "chat-filter-progress"
                )
                val tabTint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(230, easing = FastOutSlowInEasing),
                    label = "chat-filter-tint"
                )
                Row(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f * tabProgress),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f * tabProgress)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f * tabProgress),
                            RoundedCornerShape(14.dp)
                        )
                        .pressScaleClickable(onClick = {
                            if (archived != isArchive) {
                                onToggleArchived(isArchive)
                                query = ""
                            }
                        })
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isArchive) Icons.Filled.Archive else Icons.Filled.Forum,
                        contentDescription = null,
                        tint = tabTint,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        label,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else tabTint,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp
                )
            }
        } else if (filtered.isEmpty()) {
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
                    Box(Modifier.fillMaxWidth().animateItem()) {
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
    }

    renameTarget?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать чат", color = MaterialTheme.colorScheme.onSurface) },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Название") }, colors = auroraTextFieldColors()) },
            confirmButton = { PressAnimatedTextButton("Сохранить") { onRename(chat, title); renameTarget = null } },
            dismissButton = { PressAnimatedTextButton("Отмена") { renameTarget = null } },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 0.dp
        )
    }
    deleteTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить диалог?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("«${chat.title}» и все его сообщения будут удалены без возможности восстановления.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                PressAnimatedTextButton(
                    text = "Удалить",
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { onDelete(chat); deleteTarget = null }
            },
            dismissButton = { PressAnimatedTextButton("Отмена") { deleteTarget = null } },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 0.dp
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
                PressAnimatedIconButton(
                    onClick = { menu = true },
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "Меню чата",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConversationScreen(
    chat: ChatEntity,
    messages: List<MessageEntity>?,
    onBack: () -> Unit,
    onSend: suspend (String, List<StoredAttachment>) -> Unit,
    onDeleteMessage: suspend (MessageEntity) -> Unit,
    onRename: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val attachmentStorage = remember(context) { AttachmentStorage(context.applicationContext) }
    var showRename by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val attachmentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val messageActionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMessage by remember(chat.id) { mutableStateOf<MessageEntity?>(null) }
    var localText by rememberSaveable(chat.id) { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var uiNotice by remember { mutableStateOf<String?>(null) }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val uriHandler = LocalUriHandler.current

    fun showNotice(message: String) {
        scope.launch {
            uiNotice = message
            delay(2200)
            if (uiNotice == message) uiNotice = null
        }
    }

    val pendingAttachments = remember(chat.id) { mutableStateListOf<StoredAttachment>() }
    var pendingCameraFile by remember(chat.id) { mutableStateOf<File?>(null) }
    val messageListState = rememberLazyListState()
    var hasPositionedInitialMessages by remember(chat.id) { mutableStateOf(false) }
    var lastObservedMessageCount by remember(chat.id) { mutableIntStateOf(-1) }
    LaunchedEffect(chat.id, messages?.size) {
        val current = messages ?: return@LaunchedEffect
        if (!hasPositionedInitialMessages) {
            if (current.isNotEmpty()) messageListState.scrollToItem(current.lastIndex)
            hasPositionedInitialMessages = true
        } else if (current.size > lastObservedMessageCount && current.isNotEmpty()) {
            messageListState.animateScrollToItem(current.lastIndex)
        }
        lastObservedMessageCount = current.size
    }

    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val availableSlots = MAX_MESSAGE_ATTACHMENTS - pendingAttachments.size
        if (availableSlots <= 0) {
            attachmentError = "Можно прикрепить не более $MAX_MESSAGE_ATTACHMENTS файлов к одному сообщению."
            return
        }
        val acceptedUris = uris.take(availableSlots)
        scope.launch {
            var addedCount = 0
            acceptedUris.forEach { uri ->
                try {
                    val imported = attachmentStorage.importUri(uri)
                    val totalBytes = pendingAttachments.sumOf { it.sizeBytes } + imported.sizeBytes
                    if (totalBytes > MAX_MESSAGE_TOTAL_BYTES) {
                        attachmentStorage.delete(imported)
                        attachmentError = "Общий размер вложений не должен превышать 50 МБ."
                    } else {
                        pendingAttachments.add(imported)
                        addedCount++
                        attachmentError = null
                    }
                } catch (error: Exception) {
                    attachmentError = error.message ?: "Не удалось прикрепить файл."
                }
            }
            if (addedCount > 0) {
                showNotice("Добавлено файлов: $addedCount")
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_MESSAGE_ATTACHMENTS)
    ) { uris -> addPickedUris(uris) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> addPickedUris(uris) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedFile = pendingCameraFile
        pendingCameraFile = null
        if (success && capturedFile != null && capturedFile.exists() && capturedFile.length() > 0L) {
            try {
                val attachment = attachmentStorage.fromCameraFile(capturedFile)
                val totalBytes = pendingAttachments.sumOf { it.sizeBytes } + attachment.sizeBytes
                if (attachment.sizeBytes > MAX_SINGLE_ATTACHMENT_BYTES ||
                    pendingAttachments.size >= MAX_MESSAGE_ATTACHMENTS ||
                    totalBytes > MAX_MESSAGE_TOTAL_BYTES
                ) {
                    attachmentStorage.delete(attachment)
                    attachmentError = "Фото превышает лимит 25 МБ или общий лимит вложений."
                } else {
                    pendingAttachments.add(attachment)
                    attachmentError = null
                }
            } catch (error: Exception) {
                capturedFile.delete()
                attachmentError = error.message ?: "Не удалось сохранить снимок."
            }
        } else {
            capturedFile?.delete()
        }
    }

    fun launchCamera() {
        try {
            val file = attachmentStorage.createCameraOutput()
            pendingCameraFile = file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            cameraLauncher.launch(uri)
        } catch (error: Exception) {
            pendingCameraFile?.delete()
            pendingCameraFile = null
            attachmentError = "Не удалось открыть камеру: ${error.message ?: "ошибка приложения камеры"}"
        }
    }

    DisposableEffect(chat.id) {
        onDispose {
            pendingAttachments.forEach(attachmentStorage::delete)
            pendingCameraFile?.delete()
        }
    }

    fun sendCurrentMessage() {
        val text = localText.trim()
        if (isSending || (text.isEmpty() && pendingAttachments.isEmpty())) return
        val attachments = pendingAttachments.toList()
        isSending = true
        scope.launch {
            try {
                onSend(text, attachments)
                localText = ""
                pendingAttachments.clear()
                attachmentError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                attachmentError = "Не удалось сохранить сообщение: ${error.message ?: "попробуй ещё раз"}"
            } finally {
                isSending = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp), cornerRadius = 21) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PressAnimatedIconButton(
                    onClick = onBack,
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "К списку чатов",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(chat.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Отдельная история · хранится локально", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                PressAnimatedIconButton(
                    onClick = { showRename = true },
                    icon = Icons.Filled.Edit,
                    contentDescription = "Переименовать",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val loadedMessagesState = messages
        when {
            loadedMessagesState == null -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(27.dp),
                        strokeWidth = 2.4.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = messageListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(loadedMessagesState, key = { it.id }) { message ->
                            val isUser = message.role == "user"
                            val attachments = AttachmentJson.decode(message.attachmentsJson)
                            val messageContent: @Composable ColumnScope.() -> Unit = {
                                if (message.content.isNotBlank()) {
                                    MessageBodyText(message.id, message.content) { url ->
                                        runCatching { uriHandler.openUri(url) }
                                            .onFailure { showNotice("Не удалось открыть ссылку") }
                                    }
                                }
                                attachments.forEach { attachment ->
                                    StoredAttachmentCard(attachment) {
                                        openStoredAttachment(context, attachment)?.let { attachmentError = it }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().animateItem(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                GlassPanel(
                                    modifier = Modifier.widthIn(max = 320.dp).combinedClickable(
                                        onClick = {},
                                        onLongClickLabel = "Действия с сообщением",
                                        onLongClick = { selectedMessage = message }
                                    ),
                                    cornerRadius = 21,
                                    highlighted = isUser
                                ) {
                                    Column(
                                        Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp),
                                        content = messageContent
                                    )
                                }
                            }
                        }
                    }

                    if (loadedMessagesState.isEmpty()) {
                        androidx.compose.animation.AnimatedVisibility(
                            modifier = Modifier.fillMaxSize(),
                            visible = true,
                            enter = fadeIn(tween(300)) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(320, easing = FastOutSlowInEasing)),
                            label = "new-chat-welcome"
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
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.AutoAwesome,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(27.dp)
                                            )
                                        }
                                        Text(
                                            "С чего начнём?",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Можно написать сообщение, добавить файлы или сделать фото камерой.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 9.dp), cornerRadius = 25) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (pendingAttachments.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        pendingAttachments.forEach { attachment ->
                            PendingAttachmentChip(attachment) {
                                pendingAttachments.remove(attachment)
                                attachmentStorage.delete(attachment)
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = attachmentError != null,
                    enter = fadeIn(tween(180)) + expandVertically(animationSpec = tween(220)) + slideInVertically(tween(220)) { -it / 4 },
                    exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = tween(180)),
                    label = "attachment-error"
                ) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f))
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                            .padding(start = 11.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(attachmentError.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        PressAnimatedIconButton(
                            onClick = { attachmentError = null },
                            icon = Icons.Filled.Close,
                            contentDescription = "Закрыть уведомление",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp),
                            iconModifier = Modifier.size(16.dp)
                        )
                    }
                }
                AnimatedVisibility(
                    visible = uiNotice != null,
                    enter = fadeIn(tween(180)) + expandVertically(animationSpec = tween(220)) + slideInVertically(tween(220)) { -it / 4 },
                    exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = tween(180)),
                    label = "conversation-notice"
                ) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        Text(uiNotice.orEmpty(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        PressAnimatedIconButton(
                            onClick = { showAttachmentMenu = true },
                            icon = Icons.Filled.AttachFile,
                            contentDescription = "Прикрепить",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = localText,
                        onValueChange = { localText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Сообщение...") },
                        shape = RoundedCornerShape(19.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendCurrentMessage() }),
                        colors = auroraTextFieldColors()
                    )
                    GlowSendButton(
                        enabled = !isSending && (localText.isNotBlank() || pendingAttachments.isNotEmpty()),
                        loading = isSending
                    ) {
                        sendCurrentMessage()
                    }
                }
            }
        }
    }
    if (showAttachmentMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentMenu = false },
            sheetState = attachmentSheetState,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)) }
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Добавить вложение", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Выбери источник — можно добавить несколько файлов к одному сообщению.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AttachmentChoiceRow(Icons.Filled.PhotoLibrary, "Фото из галереи", "Выбрать одно или несколько изображений") {
                    scope.launch {
                        attachmentSheetState.hide()
                        showAttachmentMenu = false
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                AttachmentChoiceRow(Icons.Filled.PhotoCamera, "Сделать фото", "Открыть камеру и приложить снимок") {
                    scope.launch {
                        attachmentSheetState.hide()
                        showAttachmentMenu = false
                        launchCamera()
                    }
                }
                AttachmentChoiceRow(Icons.Filled.InsertDriveFile, "Прикрепить файл", "Документы, архивы и другие файлы") {
                    scope.launch {
                        attachmentSheetState.hide()
                        showAttachmentMenu = false
                        documentPicker.launch(arrayOf("*/*"))
                    }
                }
            }
        }
    }
    selectedMessage?.let { messageForActions ->
        ModalBottomSheet(
            onDismissRequest = { selectedMessage = null },
            sheetState = messageActionSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)) }
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 18.dp).padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Действия с сообщением",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    messageForActions.content.ifBlank { "Сообщение с вложением" },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (messageForActions.content.isNotBlank()) {
                    MessageActionOption(
                        icon = Icons.Filled.ContentCopy,
                        title = "Копировать текст",
                        description = "Скопировать содержимое сообщения"
                    ) {
                        scope.launch {
                            messageActionSheetState.hide()
                            selectedMessage = null
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Aidroid message", messageForActions.content)
                            )
                            showNotice("Сообщение скопировано")
                        }
                    }
                }
                MessageActionOption(
                    icon = Icons.Filled.DeleteOutline,
                    title = "Удалить сообщение",
                    description = "Удалить сообщение и его вложения",
                    destructive = true
                ) {
                    scope.launch {
                        messageActionSheetState.hide()
                        selectedMessage = null
                        try {
                            onDeleteMessage(messageForActions)
                            showNotice("Сообщение удалено")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            attachmentError = "Не удалось удалить сообщение: ${error.message ?: "ошибка хранилища"}"
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Название чата", color = MaterialTheme.colorScheme.onSurface) },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, colors = auroraTextFieldColors()) },
            confirmButton = { PressAnimatedTextButton("Сохранить") { onRename(title); showRename = false } },
            dismissButton = { PressAnimatedTextButton("Отмена") { showRename = false } },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 0.dp
        )
    }
}

@Composable
private fun AttachmentChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(if (MaterialTheme.colorScheme.background.red < 0.2f) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(19.dp))
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageActionOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = if (destructive) tint else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageBodyText(
    messageId: Long,
    content: String,
    onOpenLink: (String) -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(messageId, content, linkColor) {
        val pattern = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
        buildAnnotatedString {
            var cursor = 0
            pattern.findAll(content).forEach { match ->
                val clean = match.value.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}')
                if (clean.isNotEmpty()) {
                    val start = match.range.first
                    val suffixStart = start + clean.length
                    append(content.substring(cursor, start))
                    val link = LinkAnnotation.Url(
                        clean,
                        TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        )
                    ) { clicked ->
                        onOpenLink((clicked as LinkAnnotation.Url).url)
                    }
                    withLink(link) { append(clean) }
                    if (suffixStart <= match.range.last) append(content.substring(suffixStart, match.range.last + 1))
                    cursor = match.range.last + 1
                }
            }
            append(content.substring(cursor))
        }
    }
    var expanded by remember(messageId) { mutableStateOf(false) }
    var canExpand by remember(messageId) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(240, easing = FastOutSlowInEasing))) {
        BasicText(
            text = annotated,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            maxLines = if (expanded) Int.MAX_VALUE else 8,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { canExpand = it.didOverflowHeight }
        )
        AnimatedVisibility(visible = canExpand || expanded, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp)
            ) {
                Text(if (expanded) "Свернуть" else "Показать ещё", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun auroraTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary
)

private const val MAX_MESSAGE_ATTACHMENTS = 10
private const val MAX_MESSAGE_TOTAL_BYTES = 50L * 1024L * 1024L
private const val MAX_SINGLE_ATTACHMENT_BYTES = 25L * 1024L * 1024L

@Composable
private fun PendingAttachmentChip(attachment: StoredAttachment, onRemove: () -> Unit) {
    Row(
        Modifier.widthIn(max = 210.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (MaterialTheme.colorScheme.background.red < 0.2f) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(start = 9.dp, end = 3.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (attachment.mimeType.startsWith("image/")) Icons.Filled.Image else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column(Modifier.weight(1f, fill = false)) {
            Text(attachment.displayName, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(formatAttachmentSize(attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PressAnimatedIconButton(
            onClick = onRemove,
            icon = Icons.Filled.Close,
            contentDescription = "Убрать вложение",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
            iconModifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun StoredAttachmentCard(attachment: StoredAttachment, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = attachment.relativePath) {
        if (attachment.mimeType.startsWith("image/")) {
            value = withContext(Dispatchers.IO) {
                val file = File(context.filesDir, attachment.relativePath)
                if (!file.isFile) null else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val sample = maxOf(1, minOf(bounds.outWidth / 480, bounds.outHeight / 360))
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(if (MaterialTheme.colorScheme.background.red < 0.2f) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(15.dp))
            .pressScaleClickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = attachment.displayName,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (attachment.mimeType.startsWith("image/")) Icons.Filled.Image else Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(attachment.displayName, maxLines = 1, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("${attachment.mimeType} · ${formatAttachmentSize(attachment.sizeBytes)}", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.OpenInNew, contentDescription = "Открыть файл", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
    }
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes Б"
    bytes < 1024L * 1024L -> "${bytes / 1024L} КБ"
    else -> String.format(java.util.Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0))
}

private fun openStoredAttachment(context: android.content.Context, attachment: StoredAttachment): String? {
    return try {
        val file = File(context.filesDir, attachment.relativePath)
        if (!file.isFile) {
            "Файл не найден в хранилище приложения."
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(viewIntent, "Открыть вложение"))
            null
        }
    } catch (error: ActivityNotFoundException) {
        "На устройстве нет приложения для этого типа файла."
    } catch (error: Exception) {
        "Не удалось открыть файл: ${error.message ?: "неизвестная ошибка"}"
    }
}

@Composable
private fun PressAnimatedIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(165, easing = FastOutSlowInEasing),
        label = "icon-button-press-scale"
    )
    IconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interaction
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = iconModifier)
    }
}

@Composable
private fun PressAnimatedTextButton(
    text: String,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(165, easing = FastOutSlowInEasing),
        label = "text-button-press-scale"
    )
    TextButton(
        onClick = onClick,
        colors = colors,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interaction
    ) {
        Text(text)
    }
}

@Composable
private fun Modifier.pressScaleClickable(
    onClick: () -> Unit,
    pressedScale: Float = 0.965f
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "shared-press-scale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
private fun GlowSendButton(enabled: Boolean, loading: Boolean = false, onClick: () -> Unit) {
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
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
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
        InfoCard(Icons.Filled.Info, "О приложении", "Aidroid Remaster · 0.2.0-alpha.7 · Aurora Glass")
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