package cz.healthwidgy.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.data.settings.OverviewCard
import cz.healthwidgy.data.settings.OverviewCardType
import cz.healthwidgy.data.settings.OverviewLayout
import cz.healthwidgy.data.settings.SettingsRepository
import cz.healthwidgy.ui.components.AppIcon
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.history.DetailTopBar
import kotlinx.coroutines.launch

/**
 * "Upravit přehled": which cards the Přehled tab shows, in which order, and whether each is half wide
 * (two side by side) or spans the width. Every change is saved at once; Přehled follows it.
 */
@Composable
fun OverviewEditScreen(settingsRepository: SettingsRepository, onBack: () -> Unit, contentPadding: PaddingValues) {
  val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
  val scope = rememberCoroutineScope()
  // Every change works on the stored layout (not on the list of the last composition): quick taps add up.
  fun update(transform: (List<OverviewCard>) -> List<OverviewCard>) {
    scope.launch { settingsRepository.updateOverviewCards(transform) }
  }
  fun indexOf(cards: List<OverviewCard>, type: OverviewCardType) = cards.indexOfFirst { it.type == type }

  Column(Modifier.fillMaxSize().padding(contentPadding)) {
    DetailTopBar("Upravit přehled", onBack)
    val cards = settings?.overviewCards ?: return@Column
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Text(
          "Karty na Přehledu shora dolů. Poloviční karty se skládají po dvou vedle sebe, celé jdou přes celou šířku. Změny se ukládají hned.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (cards.isEmpty()) {
        item { Text("Přehled je prázdný. Přidej kartu níže.", style = MaterialTheme.typography.bodyMedium) }
      } else {
        item { LayoutPreview(cards) }
      }
      itemsIndexed(cards, key = { _, card -> card.type.name }) { index, card ->
        CardEditor(
          card = card,
          first = index == 0,
          last = index == cards.lastIndex,
          onWide = { wide -> update { cs -> cs.map { if (it.type == card.type) it.copy(wide = wide) else it } } },
          onUp = { update { cs -> OverviewLayout.move(cs, indexOf(cs, card.type), -1) } },
          onDown = { update { cs -> OverviewLayout.move(cs, indexOf(cs, card.type), 1) } },
          onRemove = { update { cs -> cs.filter { it.type != card.type } } },
        )
      }
      val missing = OverviewLayout.available(cards)
      if (missing.isNotEmpty()) {
        item { Text("Přidat kartu", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
        items(missing, key = { "add-${it.name}" }) { type ->
          AddRow(type) { update { cs -> if (cs.any { it.type == type }) cs else cs + OverviewCard(type, type.wideByDefault) } }
        }
      }
      item {
        OutlinedButton(onClick = { update { OverviewLayout.DEFAULT } }, enabled = cards != OverviewLayout.DEFAULT, modifier = Modifier.padding(top = 8.dp)) {
          Text("Obnovit výchozí rozvržení")
        }
      }
    }
  }
}

/** A sketch of the layout: one block per card, half blocks side by side. */
@Composable
private fun LayoutPreview(cards: List<OverviewCard>) {
  val slots = OverviewLayout.slots(cards.map { it.wide })
  val rows = cards.indices.groupBy { slots[it].row }.toSortedMap().values
  SurfaceCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
    Text("Náhled", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      rows.forEach { indices ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          indices.forEach { i -> PreviewBlock(cards[i].type, Modifier.weight(1f)) }
          // A half card alone in its row keeps half the width.
          if (indices.size == 1 && !cards[indices.first()].wide) Spacer(Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun PreviewBlock(type: OverviewCardType, modifier: Modifier) {
  val (icon, color) = type.iconAndColor()
  Surface(modifier.height(30.dp), shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.16f)) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      AppIcon(icon, tint = color, size = 14.dp)
      Spacer(Modifier.width(5.dp))
      Text(type.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun CardEditor(
  card: OverviewCard,
  first: Boolean,
  last: Boolean,
  onWide: (Boolean) -> Unit,
  onUp: () -> Unit,
  onDown: () -> Unit,
  onRemove: () -> Unit,
) {
  val (icon, color) = card.type.iconAndColor()
  SurfaceCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      AppIcon(icon, tint = color, size = 20.dp)
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(card.type.label, style = MaterialTheme.typography.titleSmall)
        Text(card.type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      // The icon takes the button's content color, so a disabled arrow is greyed out.
      IconButton(onClick = onUp, enabled = !first) { AppIcon(R.drawable.ic_arrow_up, contentDescription = "Posunout výš", size = 20.dp, tint = LocalContentColor.current) }
      IconButton(onClick = onDown, enabled = !last) { AppIcon(R.drawable.ic_arrow_down, contentDescription = "Posunout níž", size = 20.dp, tint = LocalContentColor.current) }
      IconButton(onClick = onRemove) { AppIcon(R.drawable.ic_close, contentDescription = "Odebrat z přehledu", size = 20.dp, tint = LocalContentColor.current) }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
      listOf(false to "Poloviční", true to "Celá šířka").forEachIndexed { i, (wide, label) ->
        SegmentedButton(selected = card.wide == wide, onClick = { onWide(wide) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
      }
    }
  }
}

@Composable
private fun AddRow(type: OverviewCardType, onAdd: () -> Unit) {
  val (icon, color) = type.iconAndColor()
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    AppIcon(icon, tint = color, size = 20.dp)
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(type.label, style = MaterialTheme.typography.bodyLarge)
      Text(type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    TextButton(onClick = onAdd) { Text("Přidat") }
  }
}
