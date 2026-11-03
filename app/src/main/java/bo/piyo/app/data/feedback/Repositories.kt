package bo.piyo.app.data.feedback

import bo.piyo.app.domain.model.Manifest
import bo.piyo.app.domain.model.NpsDraft
import bo.piyo.app.domain.model.ReportDraft
import bo.piyo.app.domain.model.ReportQuota
import bo.piyo.app.domain.model.ReportResult

/** Line reports: quota, blocking and submission (backend contract in backend/README.md). */
interface ReportsRepository {
    suspend fun quota(): ReportQuota
    suspend fun send(draft: ReportDraft): ReportResult
    /** Retry anything queued while offline. Returns how many were delivered. */
    suspend fun flushQueue(): Int
}

/** NPS score + reasons + short comment. Never contains identifiers beyond the install id header. */
interface NpsRepository {
    suspend fun send(draft: NpsDraft): Boolean
    suspend fun flushQueue(): Int
}

/** Data-update check (docs/03 §3): reads /v1/manifest and downloads a newer package. */
interface ManifestRepository {
    sealed class Check {
        data class Available(val manifest: Manifest) : Check()
        data object UpToDate : Check()
        data class Failed(val message: String) : Check()
    }
    suspend fun check(currentVersion: Int): Check
    suspend fun download(manifest: Manifest): String? // bundle JSON text or null on failure
}
