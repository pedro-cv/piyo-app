package bo.piyo.app.data.providers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bo.piyo.app.data.lines.DatasetWriter
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LocalProvider
import bo.piyo.app.domain.model.ProviderMeta
import bo.piyo.app.domain.model.RouteEdit
import bo.piyo.app.domain.model.SentInfo
import bo.piyo.app.domain.model.TransferEdge
import bo.piyo.app.domain.routing.TransferBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private val Context.providersStore: DataStore<Preferences> by preferencesDataStore(name = "piyo_providers")

/**
 * Local data providers (docs/implementation/CONTRIBUTIONS_DESIGN.md §3.1–3.2): an index in DataStore
 * (active id + metadata) and one JSON file per provider under filesDir/providers. The file is also the
 * export/import format, so sharing a provider is sharing that file. Nothing here leaves the phone.
 */
class ProvidersRepository(private val context: Context) {
    /** On-disk shape, `format` = "piyo-provider/1". Lines/variants reuse the dataset package shapes. */
    @Serializable
    data class ProviderFile(
        val format: String = FORMAT,
        val id: String,
        val name: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("base_dataset_version") val baseDatasetVersion: Int = 0,
        val lines: List<DatasetWriter.PkgLine> = emptyList(),
        val variants: List<DatasetWriter.PkgVariant> = emptyList(),
        val transfers: List<FileTransfer> = emptyList(),
        val edits: Map<String, RouteEdit> = emptyMap(),
    )

    @Serializable
    data class FileTransfer(val from: String, @SerialName("from_idx") val fromIdx: Int, val to: String, @SerialName("to_idx") val toIdx: Int, @SerialName("walk_m") val walkM: Int)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val activeKey = stringPreferencesKey("active")
    private val indexKey = stringPreferencesKey("index")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir get() = File(context.filesDir, "providers").apply { mkdirs() }

    val providers: Flow<List<ProviderMeta>> = context.providersStore.data.map { p -> decodeIndex(p[indexKey]) }
    val activeId: Flow<String?> = context.providersStore.data.map { it[activeKey] }

    private val _active = MutableStateFlow<LocalProvider?>(null)
    /** The active provider fully loaded (null = official data). Updated whenever the index or the file changes. */
    val active: StateFlow<LocalProvider?> = _active

    init {
        scope.launch { activeId.collect { id -> _active.value = id?.let { load(it) } } }
    }

    suspend fun list(): List<ProviderMeta> = providers.first()

    suspend fun create(name: String, baseVersion: Int): ProviderMeta = withContext(Dispatchers.IO) {
        val now = nowIso()
        val meta = ProviderMeta("p_" + UUID.randomUUID().toString().take(8), name.trim().ifBlank { "Mis rutas" }, now, now, baseVersion, 0)
        write(ProviderFile(id = meta.id, name = meta.name, createdAt = now, updatedAt = now, baseDatasetVersion = baseVersion))
        updateIndex { it + meta }
        meta
    }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        val f = read(id) ?: return@withContext
        write(f.copy(name = name.trim().ifBlank { f.name }, updatedAt = nowIso()))
        refresh(id)
    }

    suspend fun activate(id: String?) {
        context.providersStore.edit { if (id == null) it.remove(activeKey) else it[activeKey] = id }
    }

    /** Deletes the provider file and index entry; the official data becomes active when it was the one in use. */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        if (context.providersStore.data.first()[activeKey] == id) activate(null)
        file(id).delete()
        updateIndex { list -> list.filter { it.id != id } }
    }

    suspend fun load(id: String): LocalProvider? = withContext(Dispatchers.IO) { read(id)?.let(::toProvider) }

    /**
     * Adds or replaces a line in the provider. [before] holds the original polylines when the line
     * updates an official one. Transfer links are recomputed against [others] (the lines of the
     * official data) so the planner can use the route immediately.
     */
    suspend fun saveLine(providerId: String, line: Line, op: String, before: Map<String, String>, baseVersion: Int, others: List<Line>) = withContext(Dispatchers.Default) {
        val f = read(providerId) ?: return@withContext
        val now = nowIso()
        val lines = f.lines.filter { it.lineId != line.lineId } + DatasetWriter.line(line)
        val variants = f.variants.filter { it.lineId != line.lineId } + DatasetWriter.variants(line)
        val prev = f.edits[line.lineId]
        val edit = RouteEdit(op = prev?.op ?: op, basedOnVersion = prev?.basedOnVersion ?: baseVersion, before = prev?.before?.ifEmpty { before } ?: before, editedAt = now, sent = null)
        val domainLines = lines.map { DatasetWriter.toLine(it, variants) }
        val transfers = TransferBuilder.build(domainLines, others.filter { o -> domainLines.none { it.lineId == o.lineId } })
        write(f.copy(updatedAt = now, lines = lines, variants = variants, transfers = transfers.map { FileTransfer(it.fromVariant, it.fromIdx, it.toVariant, it.toIdx, it.walkM) }, edits = f.edits + (line.lineId to edit)))
        refresh(providerId)
    }

    suspend fun deleteLine(providerId: String, lineId: String, others: List<Line>) = withContext(Dispatchers.Default) {
        val f = read(providerId) ?: return@withContext
        val lines = f.lines.filter { it.lineId != lineId }
        val variants = f.variants.filter { it.lineId != lineId }
        val domainLines = lines.map { DatasetWriter.toLine(it, variants) }
        val transfers = TransferBuilder.build(domainLines, others.filter { o -> domainLines.none { it.lineId == o.lineId } })
        write(f.copy(updatedAt = nowIso(), lines = lines, variants = variants, transfers = transfers.map { FileTransfer(it.fromVariant, it.fromIdx, it.toVariant, it.toIdx, it.walkM) }, edits = f.edits - lineId))
        refresh(providerId)
    }

    /** Records that each line of [ids] (line id → server id of that route) was sent. */
    suspend fun markSent(providerId: String, ids: Map<String, String>) = withContext(Dispatchers.IO) {
        val f = read(providerId) ?: return@withContext
        val now = nowIso()
        val edits = f.edits.mapValues { (id, e) -> ids[id]?.let { e.copy(sent = SentInfo(it, now)) } ?: e }
        write(f.copy(edits = edits))
        refresh(providerId)
    }

    /** Forgets that [lineId] was sent (after the server withdrew it): the route is back to "Sin enviar". */
    suspend fun clearSent(providerId: String, lineId: String) = withContext(Dispatchers.IO) {
        val f = read(providerId) ?: return@withContext
        val e = f.edits[lineId] ?: return@withContext
        write(f.copy(edits = f.edits + (lineId to e.copy(sent = null))))
        refresh(providerId)
    }

    /**
     * True when another route of any provider still points at server id [contributionId]. Only possible for
     * routes sent before the server stored one row per route; withdrawing would also withdraw that route.
     */
    suspend fun sentIdShared(contributionId: String, exceptProvider: String, exceptLine: String): Boolean = withContext(Dispatchers.IO) {
        list().any { m ->
            val f = read(m.id) ?: return@any false
            f.edits.any { (line, e) -> e.sent?.id == contributionId && !(m.id == exceptProvider && line == exceptLine) }
        }
    }

    /** Applies review results (contribution id → status) to every provider that sent them. */
    suspend fun applyStatuses(statuses: Map<String, ReviewState>) = withContext(Dispatchers.IO) {
        if (statuses.isEmpty()) return@withContext
        for (m in list()) {
            val f = read(m.id) ?: continue
            var changed = false
            val edits = f.edits.mapValues { (_, e) ->
                val s = e.sent ?: return@mapValues e
                val st = statuses[s.id] ?: return@mapValues e
                if (st.status == s.status && st.note == s.note && st.publishedVersion == s.publishedVersion) e
                else { changed = true; e.copy(sent = s.copy(status = st.status, note = st.note, publishedVersion = st.publishedVersion)) }
            }
            if (changed) { write(f.copy(edits = edits)); refresh(m.id) }
        }
    }

    /** What the server says about one sent route. */
    data class ReviewState(val status: String, val note: String, val publishedVersion: Int = 0)

    /** The provider file copied to the shareable cache folder (FileProvider path "exports/"). */
    suspend fun exportFile(id: String): File? = withContext(Dispatchers.IO) {
        val f = read(id) ?: return@withContext null
        val out = File(File(context.cacheDir, "exports").apply { mkdirs() }, "piyo-" + f.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "rutas" } + ".json")
        out.writeText(json.encodeToString(ProviderFile.serializer(), f))
        out
    }

    /** Imports a provider file; it gets a fresh id so it never collides with an existing one. Throws on a bad file. */
    suspend fun import(text: String): ProviderMeta = withContext(Dispatchers.IO) {
        val f = json.decodeFromString(ProviderFile.serializer(), text)
        require(f.format.startsWith("piyo-provider/")) { "not a Piyo provider file" }
        require(f.lines.isNotEmpty()) { "the file has no routes" }
        require(f.lines.all { l -> f.variants.any { it.lineId == l.lineId } }) { "a route has no geometry" }
        val now = nowIso()
        val id = "p_" + UUID.randomUUID().toString().take(8)
        val copy = f.copy(id = id, updatedAt = now, edits = f.edits.mapValues { it.value.copy(sent = null) })
        write(copy)
        val meta = ProviderMeta(id, copy.name, copy.createdAt, now, copy.baseDatasetVersion, copy.lines.size)
        updateIndex { it + meta }
        meta
    }

    // --- internals -----------------------------------------------------------------------------

    private fun file(id: String) = File(dir, "$id.json")

    private fun read(id: String): ProviderFile? = file(id).takeIf { it.exists() }?.let { runCatching { json.decodeFromString(ProviderFile.serializer(), it.readText()) }.getOrNull() }

    private fun write(f: ProviderFile) {
        val tmp = File(dir, "${f.id}.json.tmp")
        tmp.writeText(json.encodeToString(ProviderFile.serializer(), f))
        val dst = file(f.id)
        if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst) }
    }

    private fun toProvider(f: ProviderFile): LocalProvider = LocalProvider(
        meta = ProviderMeta(f.id, f.name, f.createdAt, f.updatedAt, f.baseDatasetVersion, f.lines.size),
        lines = f.lines.map { DatasetWriter.toLine(it, f.variants) },
        transfers = f.transfers.map { TransferEdge(it.from, it.fromIdx, it.to, it.toIdx, it.walkM) },
        edits = f.edits,
    )

    /** Re-reads the file into the index entry and, when active, into [active]. */
    private suspend fun refresh(id: String) {
        val f = read(id) ?: return
        updateIndex { list -> list.map { if (it.id == id) it.copy(name = f.name, updatedAt = f.updatedAt, routeCount = f.lines.size) else it } }
        if (context.providersStore.data.first()[activeKey] == id) _active.value = toProvider(f)
    }

    private suspend fun updateIndex(fn: (List<ProviderMeta>) -> List<ProviderMeta>) {
        context.providersStore.edit { p -> p[indexKey] = json.encodeToString(ListSerializer(ProviderMeta.serializer()), fn(decodeIndex(p[indexKey]))) }
    }

    private fun decodeIndex(s: String?): List<ProviderMeta> =
        s?.let { runCatching { json.decodeFromString(ListSerializer(ProviderMeta.serializer()), it) }.getOrNull() } ?: emptyList()

    companion object {
        const val FORMAT = "piyo-provider/1"
        /** ISO-8601 UTC without java.time (minSdk 24, no desugaring). */
        fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    }
}
