package bo.piyo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.data.feedback.ManifestRepository
import bo.piyo.app.domain.model.DataStatus
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.PiyoCard
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.Notifier
import bo.piyo.app.ui.util.formatDateWithAge
import bo.piyo.app.ui.util.isOnline
import kotlinx.coroutines.launch

/**
 * The official line data card with "Buscar actualización" (Datos tab): version, line count and age, nothing
 * more. The data in use is the section below it. [reload] re-reads the status after an install; [onStatus]
 * publishes it.
 */
@Composable
fun OfficialDataCard(status: DataStatus?, reload: suspend () -> DataStatus, onStatus: (DataStatus) -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val online = isOnline()
    val notifications by c.settings.notifications.collectAsStateWithLifecycle(initialValue = true)
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val updateAvailable = stringResource(R.string.update_available, 0).substringBefore("0")
    val upToDate = stringResource(R.string.up_to_date)
    val failed = stringResource(R.string.update_failed)

    PiyoCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(stringResource(R.string.data_lines), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink, modifier = Modifier.weight(1f))
            Text("v${status?.dataVersion ?: "—"}", fontSize = 13.sp, color = t.muted)
        }
        Kv(stringResource(R.string.data_lines_count), status?.lineCount?.toString() ?: "—")
        Kv(stringResource(R.string.data_updated), formatDateWithAge(status?.generatedAt))
    }
    PiyoButton(stringResource(R.string.check_update), primary = false, enabled = online && !checking, onClick = {
        scope.launch {
            checking = true
            val v = status?.dataVersion ?: 0
            message = when (val r = c.manifest.check(v)) {
                is ManifestRepository.Check.Available -> {
                    val text = c.manifest.download(r.manifest)
                    if (text != null) {
                        c.lines.install(text); onStatus(reload())
                        if (notifications) Notifier.dataUpdated(ctx)
                        "$updateAvailable${r.manifest.dataVersion} · instalada"
                    } else failed
                }
                ManifestRepository.Check.UpToDate -> upToDate
                is ManifestRepository.Check.Failed -> failed
            }
            checking = false
        }
    })
    Note(message ?: stringResource(if (online) R.string.check_update_note else R.string.check_update_offline))
}

@Composable
internal fun Kv(k: String, v: String) {
    val t = LocalPiyo.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = t.muted, fontSize = 14.sp); Text(v, color = t.ink, fontSize = 14.sp)
    }
}
