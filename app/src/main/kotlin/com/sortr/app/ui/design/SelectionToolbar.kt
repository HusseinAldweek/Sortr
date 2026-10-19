package com.sortr.app.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount

/** One action in a [SelectionToolbar]. [tint] overrides the blue, e.g. red for Delete. */
class ToolbarItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val tint: Color? = null,
)

/** Bottom bar while items are selected, like the Photos app: the count, then labelled icons. */
@Composable
fun SelectionToolbar(count: Int, items: List<ToolbarItem>) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 6.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.count_selected, count, formatCount(count)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                items.forEach { item -> ToolbarButton(item) }
            }
        }
    }
}

@Composable
private fun ToolbarButton(item: ToolbarItem) {
    val color = (item.tint ?: MaterialTheme.colorScheme.primary).copy(alpha = if (item.enabled) 1f else 0.35f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = item.enabled, role = Role.Button, onClick = item.onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
