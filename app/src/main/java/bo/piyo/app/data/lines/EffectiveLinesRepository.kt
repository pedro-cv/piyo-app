package bo.piyo.app.data.lines

import bo.piyo.app.data.providers.ProvidersRepository
import bo.piyo.app.domain.model.DataStatus
import bo.piyo.app.domain.model.LineBundle
import bo.piyo.app.domain.model.LocalEdit
import bo.piyo.app.domain.model.LocalProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What every screen and the planner see: the official bundle with the active local provider applied
 * (CONTRIBUTIONS_DESIGN.md §2 "effective bundle"). Overridden lines replace the official line with the
 * same id, new lines are appended, and the official transfer links that touched a replaced variant are
 * dropped (their indices no longer match) in favour of the provider's own links.
 */
class EffectiveLinesRepository(
    private val main: LinesRepository,
    private val providers: ProvidersRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : LinesRepository {
    override val bundle: StateFlow<LineBundle?> =
        combine(main.bundle, providers.active) { b, p -> b?.let { merge(it, p) } }.stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun load(): LineBundle = merge(main.load(), providers.active.value)

    override suspend fun install(bundleJson: String): LineBundle = merge(main.install(bundleJson), providers.active.value)

    override suspend fun status(): DataStatus {
        val s = main.status()
        val p = providers.active.value ?: return s
        return s.copy(lineCount = merge(main.load(), p).lines.size, providerName = p.meta.name)
    }

    /** The official bundle alone (what contributions are based on and compared with). */
    suspend fun official(): LineBundle = main.load()

    companion object {
        fun merge(main: LineBundle, p: LocalProvider?): LineBundle {
            if (p == null || p.lines.isEmpty()) return main
            val overlay = p.lines.associateBy { it.lineId }
            val replacedVariantIds = main.lines.filter { it.lineId in overlay }.flatMap { l -> l.variants.map { it.variantId } }.toSet()
            val lines = main.lines.map { l -> overlay[l.lineId]?.copy(local = LocalEdit.UPDATED) ?: l } +
                p.lines.filter { l -> main.lines.none { it.lineId == l.lineId } }.map { it.copy(local = LocalEdit.ADDED) }
            val transfers = main.transfers.filter { it.fromVariant !in replacedVariantIds && it.toVariant !in replacedVariantIds } + p.transfers
            return main.copy(lines = lines, transfers = transfers, providerName = p.meta.name)
        }
    }
}
