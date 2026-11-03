package bo.piyo.app.data.lines

import android.content.Context
import bo.piyo.app.domain.model.DataStatus
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LineBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Source of the line bundle. Both flavors use the asset-backed implementation: line data is local by design. */
interface LinesRepository {
    val bundle: StateFlow<LineBundle?>
    suspend fun load(): LineBundle
    suspend fun install(bundleJson: String): LineBundle
    suspend fun status(): DataStatus
    fun find(lineId: String): Line? = bundle.value?.lines?.firstOrNull { it.lineId == lineId }
}

/**
 * Reads the seed package from assets/dataset-seed.json on first run; a downloaded package is written to
 * filesDir/dataset-current.json and wins over the seed when its version is newer.
 */
class AssetLinesRepository(private val context: Context) : LinesRepository {
    private val _bundle = MutableStateFlow<LineBundle?>(null)
    override val bundle: StateFlow<LineBundle?> = _bundle
    private var bytes: Long = 0

    private val current get() = File(context.filesDir, "dataset-current.json")

    override suspend fun load(): LineBundle = withContext(Dispatchers.IO) {
        _bundle.value?.let { return@withContext it }
        val seedText = context.assets.open(SEED).bufferedReader().use { it.readText() }
        val seed = DatasetParser.parse(seedText)
        var chosen = seed
        bytes = seedText.length.toLong()
        if (current.exists()) {
            runCatching {
                val t = current.readText()
                val b = DatasetParser.parse(t)
                if (b.dataVersion > seed.dataVersion) { chosen = b; bytes = t.length.toLong() }
            }
        }
        _bundle.value = chosen
        chosen
    }

    override suspend fun install(bundleJson: String): LineBundle = withContext(Dispatchers.IO) {
        val b = DatasetParser.parse(bundleJson) // throws on a bad package: nothing is replaced
        val tmp = File(context.filesDir, "dataset-current.json.tmp")
        tmp.writeText(bundleJson)
        if (!tmp.renameTo(current)) { current.delete(); tmp.renameTo(current) }
        bytes = bundleJson.length.toLong()
        _bundle.value = b
        b
    }

    override suspend fun status(): DataStatus {
        val b = load()
        return DataStatus(dataVersion = b.dataVersion, lineCount = b.lines.size, bytes = bytes, generatedAt = b.generatedAt, license = b.license, attribution = b.attribution)
    }

    companion object { const val SEED = "dataset-seed.json" }
}
