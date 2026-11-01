package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bo.piyo.app.R
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.PiyoBottomBar
import bo.piyo.app.ui.components.Tab
import bo.piyo.app.ui.theme.LocalPiyo

/** Page frame: app bar, scrollable body with 18 dp gutters, optional tab bar. */
@Composable
fun Page(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    tab: Tab? = null,
    onTab: ((Tab) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    scroll: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalPiyo.current
    Column(Modifier.fillMaxSize().background(t.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = if (onBack != null) 10.dp else 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onBack != null) IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
            if (titleContent != null) titleContent()
            else if (title != null) Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = t.ink, modifier = Modifier.weight(1f).padding(start = 4.dp))
            else Box(Modifier.weight(1f))
            actions()
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            body()
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp))
        }
        if (tab != null && onTab != null) PiyoBottomBar(tab, onTab)
    }
}
