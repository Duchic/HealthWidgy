package cz.healthwidgy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cz.healthwidgy.ui.theme.HealthWidgyTheme
import cz.healthwidgy.widget.Destinations

class MainActivity : ComponentActivity() {
  /** Screen requested by a widget tap; consumed by the navigation once handled. */
  private var destination by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // A relaunch from Recents reuses the original intent: do not jump to an old widget destination again.
    val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
    if (savedInstanceState == null && !fromHistory) destination = intent?.getStringExtra(Destinations.EXTRA)
    enableEdgeToEdge()
    setContent {
      HealthWidgyTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          HealthWidgyRoot(destination = destination, onDestinationHandled = { destination = null })
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra(Destinations.EXTRA)?.let { destination = it }
  }
}
