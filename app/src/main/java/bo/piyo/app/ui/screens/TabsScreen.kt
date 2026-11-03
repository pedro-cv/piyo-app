package bo.piyo.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import bo.piyo.app.ui.LocalSession
import bo.piyo.app.ui.components.PiyoBottomBar
import bo.piyo.app.ui.components.Tab
import bo.piyo.app.ui.theme.LocalPiyo
import kotlinx.coroutines.delay

/**
 * The four tabs (Buscar, Líneas, Favoritos, Datos) as pages of one [HorizontalPager] under a shared
 * bottom bar. Pages stay composed once visited (and the neighbours are composed shortly after the first
 * frame), so switching tabs is a scroll, not a screen rebuild: that is what makes it feel native.
 * The selected tab lives in the session so other screens can jump to a tab.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabsScreen(
    onSearch: () -> Unit, onResults: () -> Unit, onRoute: (String) -> Unit, onLine: (String) -> Unit,
    onReportMissing: () -> Unit, onSettings: () -> Unit, onPickPlace: (String) -> Unit,
    onEditRoute: (lineId: String?, variantId: String?) -> Unit,
) {
    val onPickOrigin = { onPickPlace("origin") }
    val t = LocalPiyo.current
    val session = LocalSession.current
    val tabs = Tab.entries
    val pager = rememberPagerState(initialPage = tabs.indexOf(session.tab)) { tabs.size }
    // Compose the other pages once the first frame of the current one is on screen.
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(500); warm = true }
    LaunchedEffect(session.tab) {
        val idx = tabs.indexOf(session.tab)
        if (pager.currentPage != idx) pager.animateScrollToPage(idx)
    }
    BackHandler(enabled = session.tab != Tab.SEARCH) { session.tab = Tab.SEARCH }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        HorizontalPager(
            state = pager, modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = false, beyondViewportPageCount = if (warm) 3 else 0,
        ) { page ->
            when (tabs[page]) {
                Tab.SEARCH -> HomeScreen(onSearch, onResults, onRoute, onLine, onReportMissing, onSettings, onTab = { session.tab = it }, onPickOrigin = onPickOrigin)
                Tab.LINES -> LinesScreen(onLine = onLine, onTab = { session.tab = it }, onSettings = onSettings)
                Tab.FAVORITES -> FavoritesScreen(onResults = onResults, onPickPlace = onPickPlace, onLine = onLine, onTab = { session.tab = it }, onSettings = onSettings)
                Tab.CONTRIBUTE -> ContributeScreen(onEditRoute = onEditRoute, onSettings = onSettings)
            }
        }
        PiyoBottomBar(session.tab, onSelect = { session.tab = it })
    }
}
