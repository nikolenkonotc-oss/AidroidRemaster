package app.aidroid.remaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AidroidTheme {
                AidroidHome()
            }
        }
    }
}

@Composable
private fun AidroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB8A2FF),
            secondary = Color(0xFF8DD9E8),
            background = Color(0xFF0B0B12),
            surface = Color(0xFF171722)
        ),
        content = content
    )
}

@Composable
private fun AidroidHome() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AIDROID",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Remaster",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Text("0.1.0", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Твой AI. Твои правила.",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "Локальные модели, разные AI-провайдеры и управляемые действия — в одном пространстве.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Система готова к настройке", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Следующий шаг — подключить Provider System и первый чат. Пока приложение работает в демонстрационном режиме.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { /* Chat flow will be added in the next milestone. */ }) {
                        Text("Скоро: открыть чат")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "LOCAL-FIRST  •  PROVIDER-AGNOSTIC  •  USER-CONTROLLED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
