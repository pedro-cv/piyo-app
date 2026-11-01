package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.R
import bo.piyo.app.ui.theme.LocalPiyo

/**
 * Custom line colour: hue / saturation / lightness sliders over coloured tracks, a live chip preview and
 * a hex field (any `#RRGGBB` typed there updates the sliders). Returns the hex on confirm.
 */
@Composable
fun ColorPickerDialog(initialHex: String, display: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val t = LocalPiyo.current
    val start = remember(initialHex) { parseHex(initialHex) ?: Color(0xFF2F6FB6) }
    var hsl by remember { mutableStateOf(toHsl(start)) }
    val color = Color.hsl(hsl.first, hsl.second, hsl.third)
    var hex by remember { mutableStateOf(toHex(start)) }
    fun setHsl(h: Float = hsl.first, s: Float = hsl.second, l: Float = hsl.third) { hsl = Triple(h, s, l); hex = toHex(Color.hsl(h, s, l)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
                        Text(display.ifBlank { "?" }, color = Color.White, fontSize = if (display.length > 3) 11.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(t.surface2).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            hex, { v -> hex = v.take(7).uppercase(); parseHex(v)?.let { c -> hsl = toHsl(c) } },
                            singleLine = true, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                HslSlider(stringResource(R.string.color_hue), hsl.first, 0f..360f, Brush.horizontalGradient((0..6).map { Color.hsl(it * 60f, 1f, 0.5f) })) { setHsl(h = it) }
                HslSlider(stringResource(R.string.color_saturation), hsl.second, 0f..1f, Brush.horizontalGradient(listOf(Color.hsl(hsl.first, 0f, hsl.third), Color.hsl(hsl.first, 1f, hsl.third)))) { setHsl(s = it) }
                HslSlider(stringResource(R.string.color_lightness), hsl.third, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, Color.hsl(hsl.first, hsl.second, 0.5f), Color.White))) { setHsl(l = it) }
                Text(stringResource(R.string.color_custom_note), color = t.muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(toHex(color)) }) { Text(stringResource(R.string.use_color), color = t.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.muted,
    )
}

@Composable
private fun HslSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    val t = LocalPiyo.current
    Column {
        Text(label, color = t.muted, fontSize = 12.sp)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp).clip(CircleShape).background(track))
            Slider(
                value = value, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent, thumbColor = t.ink),
            )
        }
    }
}

internal fun parseHex(hex: String): Color? = runCatching {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    Color((0xFF000000L or h.toLong(16)).toInt())
}.getOrNull()

internal fun toHex(c: Color): String = String.format("#%06X", 0xFFFFFF and c.toArgb())

/** RGB → HSL (hue 0–360, s/l 0–1). */
internal fun toHsl(c: Color): Triple<Float, Float, Float> {
    val max = maxOf(c.red, c.green, c.blue); val min = minOf(c.red, c.green, c.blue)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        c.red -> ((c.green - c.blue) / d + (if (c.green < c.blue) 6f else 0f))
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    } * 60f
    return Triple(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}
