package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.R
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.PiyoCard
import bo.piyo.app.ui.theme.LocalPiyo

/** 09 Gracias: opened from the per-release notification (piyo://gracias). Reads the version from the local bundle. */
@Composable
fun ThanksScreen(onLines: () -> Unit, onHome: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { version = c.lines.load().dataVersion }
    Column(Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(t.primarySoft), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = t.primary, modifier = Modifier.size(40.dp)) }
        Text(stringResource(R.string.thanks_title), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp), color = t.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.thanks_lead), color = t.muted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        PiyoCard {
            Text(stringResource(R.string.data_lines), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink)
            Text(stringResource(R.string.updated_to_version, version), color = t.muted, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.includes), color = t.muted, fontSize = 14.sp); Text(stringResource(R.string.includes_value), color = t.ink, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        PiyoButton(stringResource(R.string.see_lines), onClick = onLines)
        PiyoButton(stringResource(R.string.back_home), primary = false, onClick = onHome)
        Spacer(Modifier.height(16.dp))
    }
}
