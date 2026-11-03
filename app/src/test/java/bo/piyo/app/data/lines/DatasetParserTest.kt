package bo.piyo.app.data.lines

import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.domain.routing.RoutePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parses the real seed shipped in assets (dataset v15, package format 2, Microcruz source) and
 * checks the new attributes reach the domain model; then runs the planner on a trip whose
 * answer was cross-checked with backend/tools/check_trips.py (23 min on line 23, 0 transfers).
 */
class DatasetParserTest {
    private val seed by lazy {
        val f = listOf("src/main/assets/dataset-seed.json", "app/src/main/assets/dataset-seed.json").map(::File).first { it.exists() }
        DatasetParser.parse(f.readText())
    }

    @Test
    fun seedHeader() {
        assertEquals(15, seed.dataVersion)
        assertEquals(2, seed.packageFormat)
        assertEquals("Recorridos © Microcruz", seed.attribution)
        assertTrue(seed.license.contains("Microcruz"))
        assertEquals(133, seed.lines.size)
        assertEquals(294, seed.lines.sumOf { it.variants.size })
        assertEquals(0, seed.stops.size)
    }

    @Test
    fun everyVariantHasGeometryAndDirection() {
        for (l in seed.lines) for (v in l.variants) {
            assertTrue("${v.variantId} has ${v.points.size} points", v.points.size >= 2)
            assertTrue("${v.variantId} direction ${v.direction}", v.direction == "ida" || v.direction == "vuelta")
            assertTrue("${v.variantId} headsign", v.headsign.isNotBlank())
            assertTrue("${v.variantId} streets", v.streets.isNotEmpty())
            assertTrue(v.lengthM > 1000)
        }
    }

    @Test
    fun coloursComeFromTheSource() {
        val l41 = seed.lines.first { it.lineId == "41" }
        assertEquals("#E32C2C", l41.color)
        assertEquals("color-file", l41.colorSource)
        // 104B had only a grey placeholder in the source → derived colour, display keeps the suffix.
        val l104b = seed.lines.first { it.lineId == "104B" }
        assertEquals("104B", l104b.display)
        assertEquals("derived", l104b.colorSource)
        assertNotNull(l104b.color)
        assertTrue(l104b.color != "#808080")
        assertTrue(seed.lines.all { it.lineMode == bo.piyo.app.domain.model.LineMode.MICRO })
    }

    @Test
    fun branchesSplitIntoIdaAndVuelta() {
        val l41 = seed.lines.first { it.lineId == "41" }
        assertEquals(listOf("Rojo", "Verde"), l41.branches)
        val rojo = l41.variantsOf("Rojo")
        assertEquals(listOf("ida", "vuelta"), rojo.map { it.direction })
        assertEquals("linea41 Rojo", rojo[0].name)
        // v15: the ida half of 41 Rojo starts at Mercado Plan 3000 (geocoded), so the names are oriented that way.
        assertEquals("Mercado Plan 3000", rojo[0].origin)
        assertEquals("Urb. La Pascana", rojo[0].headsign)
        assertEquals("Urb. La Pascana", rojo[1].origin)
        assertEquals("Mercado Plan 3000", rojo[1].headsign)
        // Line 62: an unnamed main route plus "Naranja".
        assertEquals(listOf("", "Naranja"), seed.lines.first { it.lineId == "62" }.branches)
        // Lines without named routes expose a single "" branch.
        assertEquals(listOf(""), seed.lines.first { it.lineId == "1" }.branches)
    }

    @Test
    fun compactTransfersResolveToVariantIds() {
        assertEquals(103889, seed.transfers.size)
        val ids = seed.lines.flatMap { l -> l.variants.map { it.variantId } }.toSet()
        val lineOf = seed.lines.flatMap { l -> l.variants.map { it.variantId to l.lineId } }.toMap()
        for (t in seed.transfers) {
            assertTrue(t.fromVariant in ids)
            assertTrue(t.toVariant in ids)
            assertTrue("same line ${t.fromVariant}", lineOf[t.fromVariant] != lineOf[t.toVariant])
            assertTrue(t.walkM in 0..150)
        }
    }

    @Test
    fun plannerMatchesPythonCrossCheck() {
        // Plaza 24 de Septiembre -> Mall Ventura (check_trips.py: #1 line 23, 23 min, 0 transfers, Bs 2.5)
        val req = TripRequest(LatLng(-17.7834, -63.1821), "Plaza 24 de Septiembre", LatLng(-17.7572, -63.1963), "Mall Ventura")
        val its = RoutePlanner(seed).plan(req)
        // Up to 10 distinct line combinations now; direct rides first, then one-transfer ones.
        assertTrue(its.size in 4..10)
        assertEquals("23", its[0].lines.single().display)
        assertEquals(23, its[0].totalMinutes)
        assertEquals(0, its[0].transfers)
        // Snapping to the line (not the nearest vertex) since 2026-10-31 shortens some walks: 23 and 110 stay
        // first, 103 is still a direct option (the Python tool snaps to vertices, so only the leaders match).
        assertEquals(listOf("23", "110"), its.take(2).map { it.lines.joinToString("+") { l -> l.display } })
        assertTrue(its.any { it.transfers == 0 && it.lines.single().display == "103" })
        assertTrue(its.zipWithNext().all { (x, y) -> x.transfers <= y.transfers })
        // UAGRM -> Mercado Mutualista needs one transfer (check_trips.py: 72+29, 32 min)
        val its2 = RoutePlanner(seed).plan(TripRequest(LatLng(-17.7757, -63.1933), "UAGRM", LatLng(-17.766, -63.154), "Mutualista"))
        // 72+29 and 73+29 tie at 32 min; either may come first.
        assertTrue(its2[0].lines.joinToString("+") { it.display } in setOf("72+29", "73+29"))
        assertTrue(its2[0].totalMinutes in 30..33) // 32 with vertex snapping, 31 snapping to the line
        assertEquals(1, its2[0].transfers)
    }

    @Test
    fun formatOneTransfersAndLegacyBundleStillParse() {
        val fmt1 = """{"dataset_version":13,"fare_bs":"2.50","license":"ODbL",
          "lines":[{"line_id":"10","display":"10","name":"Línea 10","mode":"micro","color":"#112233","variant_ids":["a","b"]},
                   {"line_id":"7","display":"7","name":"Línea 7","mode":"micro","variant_ids":["c"]}],
          "variants":[{"variant_id":"a","line_id":"10","direction":"ida","headsign":"B","polyline":"${DatasetParserTestData.ENC}","stop_ids":[],"streets":["X"]},
                      {"variant_id":"b","line_id":"10","direction":"vuelta","headsign":"A","polyline":"${DatasetParserTestData.ENC}"},
                      {"variant_id":"c","line_id":"7","direction":"ida","headsign":"C","polyline":"${DatasetParserTestData.ENC}"}],
          "transfers":[{"from":"a","from_idx":1,"to":"c","to_idx":0,"walk_m":40}]}"""
        val b = DatasetParser.parse(fmt1)
        assertEquals(1, b.packageFormat)
        assertEquals("", b.attribution)
        assertEquals(1, b.transfers.size)
        assertEquals("a", b.transfers[0].fromVariant)
        assertEquals("c", b.transfers[0].toVariant)
        assertEquals(40, b.transfers[0].walkM)
        val v = b.lines.first { it.lineId == "10" }.variants[0]
        assertEquals("", v.branch)
        assertEquals(null, v.color)
        assertEquals(2, v.points.size)

        val legacy = """{"data_version":12,"fare_bs":"2.50","lines":[{"line_id":"10","display":"10","name":"Línea 10",
          "variants":[{"variant_id":"a","direction":"ida","headsign":"B","polyline":[[-17.78,-63.18],[-17.79,-63.19]]}]}]}"""
        val lb = DatasetParser.parse(legacy)
        assertEquals(12, lb.dataVersion)
        assertEquals(-17.79, lb.lines[0].variants[0].points[1].lat, 1e-9)
    }
}

object DatasetParserTestData {
    /** Two points (-17.78,-63.18) → (-17.79,-63.19), Google polyline 1e-5. */
    val ENC: String = encode(listOf(-17.78 to -63.18, -17.79 to -63.19))

    private fun encode(pts: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var pLat = 0L
        var pLng = 0L
        for ((la, ln) in pts) {
            val lat = Math.round(la * 1e5)
            val lng = Math.round(ln * 1e5)
            enc(lat - pLat, sb); enc(lng - pLng, sb)
            pLat = lat; pLng = lng
        }
        return sb.toString().replace("\\", "\\\\")
    }

    private fun enc(v0: Long, sb: StringBuilder) {
        var v = v0 shl 1
        if (v0 < 0) v = v.inv()
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }
}
