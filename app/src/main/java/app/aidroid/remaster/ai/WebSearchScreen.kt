package app.aidroid.remaster.ai

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun WebSearchScreen(agentRunner: AgentRunner) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) {
        context.getSharedPreferences("aidroid_web_search_history", Context.MODE_PRIVATE)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var freshness by rememberSaveable { mutableStateOf("any") }
    var results by remember { mutableStateOf<List<WebSearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<String>() }

    LaunchedEffect(preferences) {
        history.clear()
        history.addAll(preferences.getStringSet("queries", emptySet()).orEmpty().toList())
    }

    fun runSearch(value: String = query) {
        val clean = value.trim()
        if (clean.isBlank()) return
        query = clean
        scope.launch {
            loading = true
            error = null
            results = emptyList()
            try {
                results = agentRunner.searchWeb(clean, freshness)
                history.remove(clean)
                history.add(0, clean)
                while (history.size > 20) history.removeAt(history.lastIndex)
                preferences.edit().putStringSet("queries", history.toSet()).apply()
            } catch (failure: Exception) {
                error = failure.message ?: "Не удалось выполнить поиск."
            } finally {
                loading = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 3.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
                placeholder = { Text("Что найти в интернете?") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            Spacer(Modifier.width(7.dp))
            Button(
                onClick = { runSearch() },
                enabled = query.isNotBlank() && !loading,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(19.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Search, contentDescription = "Искать")
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf("any" to "Всё", "day" to "День", "week" to "Неделя", "month" to "Месяц").forEach { (key, label) ->
                FilterChip(
                    selected = freshness == key,
                    onClick = { freshness = key },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }

        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text(
                    error.orEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (results.isNotEmpty()) {
            Text(
                "${results.size} результатов · ${results.first().source}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        } else if (!loading && error == null && history.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Недавние запросы",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = {
                    history.clear()
                    preferences.edit().remove("queries").apply()
                }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Text("Очистить")
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (results.isNotEmpty()) {
                items(results, key = { it.url }) { hit ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            runCatching { uriHandler.openUri(hit.url) }
                        },
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    hit.source,
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    Icons.Filled.OpenInNew,
                                    contentDescription = "Открыть сайт",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                hit.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hit.snippet.isNotBlank()) {
                                Text(
                                    hit.snippet,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                hit.url,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (!loading && error == null) {
                items(history, key = { it }) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { runSearch(item) },
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                item,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
