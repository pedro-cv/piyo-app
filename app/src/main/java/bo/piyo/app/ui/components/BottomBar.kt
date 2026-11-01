package bo.piyo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.EditRoad
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.R
import bo.piyo.app.ui.theme.LocalPiyo

enum class Tab { SEARCH, LINES, FAVORITES, CONTRIBUTE }

/** Four equal-width tabs with Material pill indicators (docs/02-ux.md + CONTRIBUTIONS_DESIGN.md §4.1). */
@Composable
fun PiyoBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val t = LocalPiyo.current
    Column(Modifier.fillMaxWidth().background(t.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TabItem(Tab.SEARCH, current, Icons.Rounded.Search, stringResource(R.string.tab_search), onSelect)
            TabItem(Tab.LINES, current, Icons.Rounded.DirectionsBus, stringResource(R.string.tab_lines), onSelect)
            TabItem(Tab.FAVORITES, current, Icons.Rounded.Star, stringResource(R.string.tab_favorites), onSelect)
            TabItem(Tab.CONTRIBUTE, current, Icons.Rounded.EditRoad, stringResource(R.string.tab_contribute), onSelect)
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, current: Tab, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onSelect: (Tab) -> Unit) {
    val t = LocalPiyo.current
    val on = tab == current
    Column(
        Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.width(56.dp).height(32.dp).clip(CircleShape).background(if (on) t.primarySoft else t.surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (on) t.primary else t.muted, modifier = Modifier.size(24.dp))
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (on) t.ink else t.muted, maxLines = 1)
    }
}
