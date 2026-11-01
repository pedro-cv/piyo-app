package bo.piyo.app.domain

import androidx.compose.ui.graphics.Color
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LineMode
import bo.piyo.app.domain.model.Variant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Colour per line, identical on chips, polylines and list bullets.
 *
 * Source colours (the official list) are kept by hue but toned to the app's palette: the list has
 * pure #FF0000 / #0000FF / #FFFF00 values that glare on screen and leave white text unreadable, so
 * saturation is clamped to 45–70 % and lightness to 34–48 % (the canvas uses hsl(h 62% 38%)).
 * Lines without a usable colour get a stable hue from their number.
 */
object LineColors {
    private val knownHues = mapOf(
        "76" to 4f, "98" to 150f, "103" to 210f, "21" to 30f, "92" to 340f,
        "9" to 120f, "111" to 200f, "8" to 265f, "81" to 45f,
    )
    private const val S_MIN = 0.45f
    private const val S_MAX = 0.70f
    private const val L_MIN = 0.34f
    private const val L_MAX = 0.48f

    private val cache = HashMap<String, Color>()

    /** Colour of one variant: its own override when the dataset gives one, else the line colour. */
    fun colorOf(line: Line, variant: Variant?): Color {
        variant?.color?.let { hex -> toned(hex)?.let { return it } }
        return colorOf(line)
    }

    fun colorOf(line: Line): Color {
        line.color?.let { hex -> toned(hex)?.let { return it } }
        if (line.lineMode == LineMode.TRUFI) return Color.hsl(265f, 0.45f, 0.40f)
        return fromDisplay(line.display)
    }

    fun fromDisplay(display: String): Color {
        val hue = knownHues[display] ?: run {
            val n = display.filter { it.isDigit() }.toIntOrNull() ?: display.hashCode().let { if (it < 0) -it else it }
            ((n * 47) % 360).toFloat()
        }
        return Color.hsl(hue, 0.62f, 0.38f)
    }

    /** Parses a hex colour and tones it into the palette range (memoised: it runs for every chip). */
    fun toned(hex: String): Color? {
        cache[hex]?.let { return it }
        val c = parseHex(hex) ?: return null
        val (h, s, l) = hsl(c)
        val out = when {
            s < 0.12f -> Color.hsl(h, 0f, l.coerceIn(0.30f, 0.45f)) // greys stay grey, just readable under white text
            else -> Color.hsl(h, s.coerceIn(S_MIN, S_MAX), l.coerceIn(L_MIN, L_MAX))
        }
        cache[hex] = out
        return out
    }

    private fun parseHex(hex: String): Color? = runCatching {
        val h = hex.removePrefix("#")
        val v = h.toLong(16)
        if (h.length == 6) Color((0xFF000000L or v).toInt()) else Color(v.toInt())
    }.getOrNull()

    /** RGB → HSL (hue 0–360, s/l 0–1). */
    private fun hsl(c: Color): Triple<Float, Float, Float> {
        val r = c.red; val g = c.green; val b = c.blue
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d < 1e-5f) return Triple(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        var h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        if (h < 0f) h += 360f
        return Triple(h, s.coerceIn(0f, 1f), l)
    }
}
