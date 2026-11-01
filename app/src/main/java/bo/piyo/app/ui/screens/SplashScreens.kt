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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import bo.piyo.app.data.feedback.ManifestRepository
import bo.piyo.app.domain.LineColors
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LineBundle
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.LineChip
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.PiyoCard
import bo.piyo.app.ui.theme.BrandStyle
import bo.piyo.app.ui.theme.LocalPiyo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The app logo as the launcher shows it: the rhea head on brand green in a circle (the shape every
 * launcher mask has in common). Use this wherever the brand appears so it looks the same everywhere.
 */
@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val green = androidx.compose.ui.res.colorResource(R.color.brand_green)
    Box(modifier.size(size).clip(CircleShape).background(green), contentAlignment = Alignment.TopCenter) {
        // Same placement as the adaptive icon: the head is 80/72 of the visible circle, top at 24-18 = 6/72.
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.brand_logo), contentDescription = null,
            modifier = Modifier.padding(top = size * 6 / 72).height(size * 80 / 72),
        )
    }
}

/** 00b Primera carga: seed bundle is ready; optional update and optional offline map. "Empezar" is never blocked. */
@Composable
fun FirstLoadScreen(onStart: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var bundle by remember { mutableStateOf<LineBundle?>(null) }
    var update by remember { mutableStateOf<ManifestRepository.Check?>(null) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val b = c.lines.load(); bundle = b
        when (val chk = c.manifest.check(b.dataVersion)) {
            is ManifestRepository.Check.Available -> {
                update = chk
                val text = c.manifest.download(chk.manifest)
                progress = 1f
                if (text != null) { bundle = c.lines.install(text) }
            }
            else -> update = chk
        }
    }

    Column(Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(36.dp))
        Text(stringResource(R.string.first_load_title), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp), color = t.ink)
        Text(stringResource(R.string.first_load_lead), color = t.muted, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(4.dp))
        PiyoCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ok()
                Column { Title(stringResource(R.string.bundle_included)); Sub(stringResource(R.string.bundle_included_sub, bundle?.lines?.size ?: 0, bundle?.dataVersion ?: 0)) }
            }
        }
        val u = update
        if (u is ManifestRepository.Check.Available) PiyoCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.CloudDownload, null, tint = t.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) { Title(stringResource(R.string.updating_to, u.manifest.dataVersion)); Sub("${u.manifest.packageBytes / 1024} KB") }
                Sub("${(progress * 100).toInt()} %")
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = t.primary, trackColor = t.line)
        }
        PiyoCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Map, null, tint = t.muted, modifier = Modifier.size(28.dp))
                Column { Title(stringResource(R.string.offline_map)); Sub(stringResource(R.string.offline_map_sub)) }
            }
        }
        Spacer(Modifier.weight(1f))
        PiyoButton(stringResource(R.string.start_using), onClick = { scope.launch { c.settings.setFirstRunDone(); onStart() } })
        Note(stringResource(R.string.first_load_note), align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
    }
}

@Composable private fun Ok() {
    val t = LocalPiyo.current
    Box(Modifier.size(28.dp).clip(CircleShape).background(t.primarySoft), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = t.primary, modifier = Modifier.size(20.dp)) }
}
@Composable internal fun Title(s: String) { Text(s, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = LocalPiyo.current.ink) }
@Composable internal fun Sub(s: String, color: androidx.compose.ui.graphics.Color = LocalPiyo.current.muted) { Text(s, fontSize = 13.sp, color = color) }
