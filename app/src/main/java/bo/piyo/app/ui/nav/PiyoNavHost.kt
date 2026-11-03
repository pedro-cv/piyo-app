package bo.piyo.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import bo.piyo.app.ui.components.Tab
import bo.piyo.app.ui.screens.AboutScreen
import bo.piyo.app.ui.screens.SettingsScreen
import bo.piyo.app.ui.screens.FirstLoadScreen
import bo.piyo.app.ui.screens.LineDetailScreen
import bo.piyo.app.ui.screens.ReportScreen
import bo.piyo.app.ui.screens.ResultsScreen
import bo.piyo.app.ui.screens.RouteEditorScreen
import bo.piyo.app.ui.screens.RouteDetailScreen
import bo.piyo.app.ui.screens.SearchScreen
import bo.piyo.app.ui.screens.TabsScreen
import bo.piyo.app.ui.LocalSession
import bo.piyo.app.ui.TripSession
import bo.piyo.app.ui.screens.ThanksScreen
import bo.piyo.app.ui.theme.LocalPiyo

private const val FADE_MS = 120

/** Navigation map from docs/02-ux.md: three tabs, max depth three taps. */
@Composable
fun PiyoNavHost(startRoute: Any) {
    val nav = rememberNavController()
    val t = LocalPiyo.current
    val session = LocalSession.current
    // Short cross-fades instead of the default 700 ms ones: screens with a MapView must appear at once.
    NavHost(
        navController = nav, startDestination = startRoute, modifier = Modifier.fillMaxSize().background(t.bg),
        enterTransition = { fadeIn(tween(FADE_MS)) }, exitTransition = { fadeOut(tween(FADE_MS)) },
        popEnterTransition = { fadeIn(tween(FADE_MS)) }, popExitTransition = { fadeOut(tween(FADE_MS)) },
    ) {
        composable<FirstLoadRoute> { FirstLoadScreen(onStart = { nav.navigate(HomeRoute) { popUpTo<FirstLoadRoute> { inclusive = true } } }) }
        composable<HomeRoute> {
            TabsScreen(
                onSearch = { nav.navigate(SearchRoute()) },
                onResults = { nav.navigate(ResultsRoute) },
                onRoute = { id -> nav.navigate(RouteDetailRoute(id)) },
                onLine = { id -> nav.navigate(LineDetailRoute(id)) },
                onReportMissing = { nav.navigate(ReportRoute(type = "missing")) },
                onSettings = { nav.navigate(SettingsRoute) },
                onPickPlace = { kind -> nav.navigate(SearchRoute(purpose = kind)) },
                onEditRoute = { lineId, variantId -> nav.navigate(RouteEditorRoute(lineId, variantId)) },
            )
        }
        composable<SearchRoute> { entry ->
            val r = entry.toRoute<SearchRoute>()
            SearchScreen(purpose = r.purpose, onBack = { nav.popBackStack() }, onResults = { nav.navigate(ResultsRoute) { popUpTo<HomeRoute>() } })
        }
        composable<ResultsRoute> {
            ResultsScreen(
                onBack = { nav.popBackStack() }, onRoute = { id -> nav.navigate(RouteDetailRoute(id)) }, onTab = { nav.tab(session, it) },
                onPickOrigin = { nav.navigate(SearchRoute(purpose = "origin")) }, onPickDestination = { nav.navigate(SearchRoute(purpose = "destination")) },
            )
        }
        composable<RouteDetailRoute> { entry ->
            val r = entry.toRoute<RouteDetailRoute>()
            // "Terminar viaje" goes back to Inicio by popping (tab() would save and restore this very screen).
            RouteDetailScreen(itineraryId = r.itineraryId, onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<LineDetailRoute> { entry ->
            val r = entry.toRoute<LineDetailRoute>()
            LineDetailScreen(
                lineId = r.lineId, onBack = { nav.popBackStack() },
                onReport = { lat, lng -> nav.navigate(ReportRoute(lineId = r.lineId, lat = lat, lng = lng)) },
                onTab = { nav.tab(session, it) },
            )
        }
        composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }, onAbout = { nav.navigate(AboutRoute) }) }
        composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
        composable<ReportRoute> { entry ->
            val r = entry.toRoute<ReportRoute>()
            ReportScreen(lineId = r.lineId, lat = r.lat, lng = r.lng, initialType = r.type, onBack = { nav.popBackStack() }, onContribute = { nav.tab(session, Tab.CONTRIBUTE) })
        }
        composable<RouteEditorRoute> { entry ->
            val r = entry.toRoute<RouteEditorRoute>()
            RouteEditorScreen(lineId = r.lineId, variantId = r.variantId, onDone = { nav.popBackStack() })
        }
        composable<ThanksRoute> {
            ThanksScreen(onLines = { session.tab = Tab.LINES; nav.navigate(HomeRoute) { popUpTo(0) } }, onHome = { session.tab = Tab.SEARCH; nav.navigate(HomeRoute) { popUpTo(0) } })
        }
    }
}

/** Tab switch from a deeper screen: select the tab in the session and pop back to the tabs screen. */
private fun NavHostController.tab(session: TripSession, tab: Tab) {
    session.tab = tab
    popBackStack(HomeRoute, inclusive = false)
}
