package bo.piyo.app.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import bo.piyo.app.R
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.PiyoCard
import bo.piyo.app.ui.theme.LocalPiyo

/** Acerca de · licencias. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    Page(title = stringResource(R.string.about), onBack = onBack) {
        PiyoCard {
            Text("Piyo ${c.appVersion}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink)
            Text("Micros y trufis de Santa Cruz. Gratis, sin anuncios, sin internet. Código abierto.", color = t.muted, fontSize = 14.sp)
        }
        PiyoCard {
            Text("Datos", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink)
            val bundle by c.lines.bundle.collectAsStateWithLifecycle()
            Text(bundle?.license?.takeIf { it.isNotBlank() } ?: "Recorridos © OpenStreetMap contributors, licencia ODbL.", color = t.muted, fontSize = 14.sp)
            Text("Relevamientos propios se suben a OpenStreetMap bajo licencia ODbL.", color = t.muted, fontSize = 14.sp)
        }
        PiyoCard {
            Text("Mapa", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink)
            Text(c.mapProvider.attribution, color = t.muted, fontSize = 14.sp)
        }
        PiyoCard {
            Text("Bibliotecas", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink)
            Text("Jetpack Compose, Material 3, Navigation, DataStore (Apache 2.0) · kotlinx.serialization y coroutines (Apache 2.0) · OkHttp (Apache 2.0) · MapLibre Native (BSD-2) · Íconos Material Rounded (Apache 2.0) · Tipografía Sora (OFL).", color = t.muted, fontSize = 14.sp)
        }
        Note("Sin cuenta, sin analítica, sin anuncios. Los reportes y la opinión se envían de forma anónima; ver docs/implementation/PRIVACY.md.")
    }
}
