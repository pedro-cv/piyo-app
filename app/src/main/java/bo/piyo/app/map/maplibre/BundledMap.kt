package bo.piyo.app.map.maplibre

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The city map shipped inside the APK (assets/map/, built by scripts/bundle-map.py): Protomaps vector
 * tiles of the metro area as one PMTiles file, plus the light and dark styles that share one layer set
 * (same streets, places and points in both themes), their fonts and sprites. Works with no network from
 * the first launch.
 *
 * MapLibre reads PMTiles from a file:// URL (pmtiles://file://…), not from inside the APK, so [prepare]
 * copies the archive to app storage once (and again after an app update changes its size). Styles,
 * glyphs and sprites load straight from the APK through asset:// URLs.
 */
class BundledMap(private val context: Context) {
    private val target get() = File(File(context.filesDir, "map"), TILES)
    private val styles = HashMap<String, String>()

    /** Copies the tiles out of the APK when missing or stale. Safe to call on every launch (cheap check). */
    suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val size = context.assets.openFd("map/$TILES").use { it.length }
            val dst = target
            if (dst.length() != size) {
                dst.parentFile?.mkdirs()
                val tmp = File(dst.parentFile, "$TILES.tmp")
                context.assets.open("map/$TILES").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst) }
            }
            dst.length() == size
        }.getOrDefault(false)
    }

    /** Style JSON for the theme with the tiles URL filled in, or null when the tiles are not in place. */
    fun styleJson(dark: Boolean): String? {
        val file = target
        if (!file.exists()) return null
        val theme = if (dark) "dark" else "light"
        val raw = styles.getOrPut(theme) { context.assets.open("map/style-$theme.json").bufferedReader().use { it.readText() } }
        return raw.replace("{PMTILES}", "file://" + file.absolutePath)
    }

    companion object {
        const val TILES = "santa-cruz.pmtiles"
        const val ATTRIBUTION = "© OpenStreetMap contributors · Protomaps"
    }
}
