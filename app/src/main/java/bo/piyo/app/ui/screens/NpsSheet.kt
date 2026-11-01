package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.R
import bo.piyo.app.domain.model.Itinerary
import bo.piyo.app.domain.model.NpsDraft
import bo.piyo.app.domain.model.NpsReason
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.theme.LocalPiyo
import kotlinx.coroutines.launch

const val GITHUB_ISSUES_URL = "https://github.com/" // TODO(next-iteration): set the repo issue-form URL

/** 08 Opinión: NPS bottom sheet at trip completion (also reachable from Datos y conexión). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NpsSheet(trip: Itinerary?, onClose: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf("score") }
    var score by remember { mutableStateOf(-1) }
    var reasons by remember { mutableStateOf(setOf<NpsReason>()) }
    var comment by remember { mutableStateOf("") }

    fun send() {
        scope.launch {
            c.nps.send(NpsDraft(score, reasons, comment.take(140)))
            c.settings.recordNpsShown(dismissed = false)
            step = "done"
        }
    }
    fun dismiss() { scope.launch { c.settings.recordNpsShown(dismissed = step != "done"); onClose() } }

    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = state, containerColor = t.surface, dragHandle = null) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(t.line))
            when (step) {
                "score" -> {
                    if (trip != null) Text(stringResource(R.string.arrived), fontSize = 13.sp, color = t.muted)
                    Text(stringResource(R.string.nps_question), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = t.ink, lineHeight = 24.sp)
                    Text(stringResource(R.string.nps_sub), fontSize = 13.sp, color = t.muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..10).forEach { n ->
                            val on = n == score
                            Box(
                                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(if (on) t.primary else t.surface2).clickable { score = n },
                                contentAlignment = Alignment.Center,
                            ) { Text(n.toString(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) t.onPrimary else t.ink) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.nps_low), fontSize = 12.sp, color = t.muted); Text(stringResource(R.string.nps_high), fontSize = 12.sp, color = t.muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PiyoButton(stringResource(R.string.not_now), primary = false, modifier = Modifier.weight(1f), onClick = ::dismiss)
                        PiyoButton(stringResource(R.string.next), enabled = score >= 0, modifier = Modifier.weight(1f), onClick = { step = "comment" })
                    }
                }
                "comment" -> {
                    Text(stringResource(if (score >= 9) R.string.nps_title_high else if (score >= 7) R.string.nps_title_mid else R.string.nps_title_low), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
                    Text(stringResource(R.string.nps_tap_hint), fontSize = 13.sp, color = t.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            NpsReason.MISSING_LINES to R.string.tag_missing_lines, NpsReason.WRONG_ROUTES to R.string.tag_wrong_routes, NpsReason.MAP to R.string.tag_map,
                            NpsReason.SLOW to R.string.tag_slow, NpsReason.USEFUL to R.string.tag_useful, NpsReason.OTHER to R.string.tag_other,
                        ).forEach { (r, label) ->
                            val on = r in reasons
                            Box(
                                Modifier.height(36.dp).clip(CircleShape).background(if (on) t.primary else t.surface).border(1.dp, if (on) t.primary else t.line, CircleShape)
                                    .clickable { reasons = if (on) reasons - r else reasons + r }.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(stringResource(label), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) t.onPrimary else t.ink) }
                        }
                    }
                    Text(stringResource(R.string.few_words), fontSize = 13.sp, color = t.muted)
                    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(t.surface2).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        if (comment.isEmpty()) Text(stringResource(R.string.nps_placeholder), color = t.muted, fontSize = 15.sp)
                        BasicTextField(comment, { comment = it.take(140) }, singleLine = true, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth())
                    }
                    Text(stringResource(R.string.max_140), fontSize = 11.sp, color = t.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PiyoButton(stringResource(R.string.send_without_comment), primary = false, modifier = Modifier.weight(1f), onClick = { comment = ""; send() })
                        PiyoButton(stringResource(R.string.send), modifier = Modifier.weight(1f), onClick = ::send)
                    }
                }
                else -> {
                    Text(stringResource(R.string.nps_thanks), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
                    Text(stringResource(R.string.open_source_note), fontSize = 13.sp, color = t.muted)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.surface2).clickable { uri.openUri(GITHUB_ISSUES_URL) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.propose_improvement), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.ink)
                            Text(stringResource(R.string.opens_github), fontSize = 12.sp, color = t.muted)
                        }
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = t.muted, modifier = Modifier.size(20.dp))
                    }
                    PiyoButton(stringResource(R.string.close), primary = false, onClick = ::dismiss)
                }
            }
        }
    }
}
