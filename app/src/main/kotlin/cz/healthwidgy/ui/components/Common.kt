package cz.healthwidgy.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppIcon(@DrawableRes id: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, size: Dp = 18.dp, contentDescription: String? = null) {
  Icon(painterResource(id), contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}

/** Rounded surface used for dashboard cards and tiles. */
@Composable
fun SurfaceCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  contentPadding: Dp = 14.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(22.dp)
  val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
  if (onClick != null) {
    Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
      Column(Modifier.padding(contentPadding), content = content)
    }
  } else {
    Card(modifier = modifier, shape = shape, colors = colors) { Column(Modifier.padding(contentPadding), content = content) }
  }
}

/** Small label with a colored icon, used as a tile header. */
@Composable
fun TileHeader(@DrawableRes icon: Int, label: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    AppIcon(icon, tint = color, size = 16.dp)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
