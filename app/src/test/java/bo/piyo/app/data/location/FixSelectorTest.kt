package bo.piyo.app.data.location

import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.map.maplibre.PhotonGeocoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixSelectorTest {
    private val here = LatLng(-17.7834, -63.1821)
    private val tower = LatLng(-17.7860, -63.1790) // where a cell-based network fix lands, ~420 m away

    private fun gps(t: Long, acc: Float = 5f, p: LatLng = here) = Fix(p, acc, t, "gps")
    private fun net(t: Long, acc: Float = 40f, p: LatLng = tower) = Fix(p, acc, t, "network")

    @Test fun firstFixAlwaysWins() = assertTrue(FixSelector.isBetter(net(0), null))

    // The bug: during a trip a network fix right after a GPS fix moved the dot ~400 m away.
    @Test fun coarseNetworkFixDoesNotReplaceRecentGps() {
        assertFalse(FixSelector.isBetter(net(1_000), gps(0)))
        assertFalse(FixSelector.isBetter(net(60_000, acc = 20f), gps(0)))
    }

    @Test fun newerGpsFixFollowsTheUserEvenIfSlightlyLessAccurate() =
        assertTrue(FixSelector.isBetter(gps(2_000, acc = 12f, p = LatLng(-17.7840, -63.1821)), gps(0, acc = 5f)))

    @Test fun moreAccurateFixWins() = assertTrue(FixSelector.isBetter(gps(1_000, acc = 4f), net(0)))

    @Test fun muchNewerFixWinsEvenIfCoarse() =
        assertTrue(FixSelector.isBetter(net(FixSelector.SIGNIFICANT_AGE_MS + 1), gps(0)))

    @Test fun muchOlderFixNeverWins() =
        assertFalse(FixSelector.isBetter(gps(0, acc = 3f), net(FixSelector.SIGNIFICANT_AGE_MS + 1)))

    @Test fun hugeAccuracyLossFromSameProviderIsRejected() =
        assertFalse(FixSelector.isBetter(gps(1_000, acc = 400f), gps(0, acc = 5f)))

    @Test fun fixWithoutAccuracyCountsAsCoarse() =
        assertFalse(FixSelector.isBetter(Fix(tower, null, 1_000, "network"), gps(0)))

    @Test fun bestKnownSkipsStaleFixesAndPrefersAccuracy() {
        val now = 60 * 60_000L
        val stale = gps(now - FixSelector.MAX_LAST_KNOWN_AGE_MS - 1, acc = 3f)
        val recentNet = net(now - 30_000)
        val recentGps = gps(now - 60_000, acc = 6f)
        assertEquals(recentGps, FixSelector.best(listOf(stale, recentNet, recentGps), now))
        assertNull(FixSelector.best(listOf(stale), now))
    }

    @Test fun searchBiasIsRoundedToAboutOneKilometre() {
        val c = PhotonGeocoder.coarse(LatLng(-17.783412, -63.182149))
        assertEquals(-17.78, c.lat, 1e-9)
        assertEquals(-63.18, c.lng, 1e-9)
    }
}
