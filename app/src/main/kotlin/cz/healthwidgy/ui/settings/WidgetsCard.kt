package cz.healthwidgy.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.widget.HeartWidgetReceiver
import cz.healthwidgy.widget.LastWorkoutWidgetReceiver
import cz.healthwidgy.widget.SingleValueWidgetReceiver
import cz.healthwidgy.widget.SleepWidgetReceiver
import cz.healthwidgy.widget.StepsTrendWidgetReceiver
import cz.healthwidgy.widget.StepsWidgetReceiver
import cz.healthwidgy.widget.TodayWidgetReceiver

private data class WidgetEntry(val name: String, val size: String, val description: String, val receiver: Class<*>)

private val entries =
  listOf(
    WidgetEntry("Dnešní přehled", "4×2", "Kroky, kalorie, vzdálenost, patra, klidový tep", TodayWidgetReceiver::class.java),
    WidgetEntry("Kroky", "2×2", "Kroužek s procentem cíle", StepsWidgetReceiver::class.java),
    WidgetEntry("Spánek", "4×2", "Délka a fáze poslední noci", SleepWidgetReceiver::class.java),
    WidgetEntry("Srdce", "2×2", "Klidový tep a trend 7 dní", HeartWidgetReceiver::class.java),
    WidgetEntry("Kroky 7 dní", "4×2", "Sloupce za týden s cílem", StepsTrendWidgetReceiver::class.java),
    WidgetEntry("Jedna hodnota", "2×1", "Vybraná hodnota velkým písmem", SingleValueWidgetReceiver::class.java),
    WidgetEntry("Poslední aktivita", "4×1", "Poslední trénink", LastWorkoutWidgetReceiver::class.java),
  )

/** Lists the widgets with a button that asks the launcher to place one on the home screen. */
@Composable
fun WidgetsCard() {
  val context = LocalContext.current
  SurfaceCard(Modifier.fillMaxWidth()) {
    Text("Widgety na plochu", style = MaterialTheme.typography.titleMedium)
    Text(
      "Ťukni na Přidat, nebo dlouze podrž prázdné místo na ploše → Widgety → HealthWidgy. Widgety se obnovují zhruba jednou za hodinu, při otevření aplikace a ťuknutím na ↻.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 4.dp),
    )
    entries.forEach { e ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
          Text("${e.name} · ${e.size}", style = MaterialTheme.typography.bodyLarge)
          Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { pin(context, e.receiver) }) { Text("Přidat") }
      }
    }
  }
}

private fun pin(context: Context, receiver: Class<*>) {
  val manager = context.getSystemService(AppWidgetManager::class.java)
  if (manager?.isRequestPinAppWidgetSupported == true) {
    manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
  } else {
    Toast.makeText(context, "Tento launcher přidání z aplikace nepodporuje. Přidej widget z plochy.", Toast.LENGTH_LONG).show()
  }
}
