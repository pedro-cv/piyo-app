package bo.piyo.app.data.location

import bo.piyo.app.domain.model.LatLng

/**
 * One location fix, reduced to what [FixSelector] compares. [elapsedMs] is the fix's time on the monotonic
 * clock (Location.elapsedRealtimeNanos / 1e6), not wall time: GPS and network fixes carry wall times from
 * different sources, so only the monotonic clock orders them reliably. [accuracyM] is the radius of 68 %
 * confidence reported by Android; null when the provider gave none.
 */
data class Fix(val position: LatLng, val accuracyM: Float?, val elapsedMs: Long, val provider: String)

/**
 * Decides whether a new fix should replace the one on screen. Before this rule every fix replaced the last
 * one, so during a trip a coarse network fix (30–1000 m) arriving right after a GPS fix (≈5 m) moved the dot
 * away from the user, and the last known fix (possibly a network one, or hours old) was shown as if exact.
 * The rule follows the Android location guide: newer wins unless it is clearly less accurate, a much newer
 * fix always wins (the user may have moved), a much older one never does.
 */
object FixSelector {
    /** After this long a newer fix wins even if it is less accurate. */
    const val SIGNIFICANT_AGE_MS = 2 * 60_000L
    /** A newer fix from the same provider may be this much less accurate and still win. */
    const val SIGNIFICANT_ACCURACY_LOSS_M = 200f
    /** A fix without accuracy is treated as this coarse. */
    const val UNKNOWN_ACCURACY_M = 1_000f
    /** Last known fixes older than this are not shown at all: they may be from another part of the city. */
    const val MAX_LAST_KNOWN_AGE_MS = 30 * 60_000L

    fun isBetter(new: Fix, current: Fix?): Boolean {
        if (current == null) return true
        val dt = new.elapsedMs - current.elapsedMs
        if (dt > SIGNIFICANT_AGE_MS) return true
        if (dt < -SIGNIFICANT_AGE_MS) return false
        val delta = (new.accuracyM ?: UNKNOWN_ACCURACY_M) - (current.accuracyM ?: UNKNOWN_ACCURACY_M)
        val newer = dt > 0
        return when {
            delta < 0 -> true
            newer && delta == 0f -> true
            newer && delta <= SIGNIFICANT_ACCURACY_LOSS_M && new.provider == current.provider -> true
            else -> false
        }
    }

    /** The best of [fixes] that is not older than [MAX_LAST_KNOWN_AGE_MS] at [nowElapsedMs]. */
    fun best(fixes: List<Fix>, nowElapsedMs: Long): Fix? =
        fixes.filter { nowElapsedMs - it.elapsedMs <= MAX_LAST_KNOWN_AGE_MS }
            .fold(null as Fix?) { acc, f -> if (isBetter(f, acc)) f else acc }
}
