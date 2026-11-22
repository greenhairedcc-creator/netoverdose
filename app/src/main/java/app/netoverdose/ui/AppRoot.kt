package app.netoverdose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.screens.HorizonScreen
import app.netoverdose.ui.screens.IdeasScreen
import app.netoverdose.ui.screens.LabScreen
import app.netoverdose.ui.screens.MainScreen
import app.netoverdose.ui.screens.MoreScreen
import app.netoverdose.ui.screens.StatsScreen
import app.netoverdose.ui.screens.StubScreen
import app.netoverdose.ui.screens.TaskCardScreen
import app.netoverdose.ui.theme.MonoFamily
import java.util.Locale

private object Routes {
    const val MAIN = "main"
    const val HORIZON = "horizon"
    const val STATS = "stats"
    const val MORE = "more"
    const val LAB = "more/lab"
    const val IDEAS = "more/ideas"
    const val TASK = "task/{id}"

    fun task(id: Long) = "task/$id"
}

private val topLevel = setOf(Routes.MAIN, Routes.HORIZON, Routes.STATS, Routes.MORE)

@Composable
fun AppRoot(app: NetoverdoseApp) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.MAIN
    val palette = LocalDesign.current

    LaunchedEffect(app) {
        app.messages.collect { text -> snackbar.showSnackbar(text) }
    }

    Column(Modifier.fillMaxSize().background(palette.bg).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = Routes.MAIN, modifier = Modifier.fillMaxSize()) {
                composable(Routes.MAIN) {
                    MainScreen(
                        onOpenTask = { id -> navController.navigate(Routes.task(id)) }
                    )
                }
                composable(Routes.HORIZON) {
                    HorizonScreen(onOpenTask = { id -> navController.navigate(Routes.task(id)) })
                }
                composable(Routes.STATS) {
                    StatsScreen(onOpenTask = { id -> navController.navigate(Routes.task(id)) })
                }
                composable(Routes.MORE) {
                    MoreScreen(
                        onOpenLab = { navController.navigate(Routes.LAB) },
                        onOpenIdeas = { navController.navigate(Routes.IDEAS) }
                    )
                }
                composable(Routes.LAB) {
                    LabScreen(controller = app.designController, onBack = { navController.popBackStack() })
                }
                composable(Routes.IDEAS) {
                    IdeasScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    Routes.TASK,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) {
                    // The id travels through SavedStateHandle into TaskCardViewModel.
                    TaskCardScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = palette.ink,
                    contentColor = palette.bg,
                    actionColor = palette.bg,
                    shape = palette.shape
                )
            }
        }
        if (route in topLevel) {
            BottomNav(current = route, onSelect = { target ->
                if (target != route) {
                    navController.navigate(target) {
                        popUpTo(Routes.MAIN) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            })
        }
    }
}

@Composable
private fun BottomNav(current: String, onSelect: (String) -> Unit) {
    val palette = LocalDesign.current
    val items = listOf(
        Triple(Routes.MAIN, R.string.nav_main, "01"),
        Triple(Routes.HORIZON, R.string.nav_horizon, "02"),
        Triple(Routes.STATS, R.string.nav_stats, "03"),
        Triple(Routes.MORE, R.string.nav_more, "04")
    )
    Row(
        Modifier.fillMaxWidth().background(palette.bg).navigationBarsPadding()
            .padding(horizontal = palette.pad, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (target, label, index) ->
            val active = current == target
            Box(
                Modifier.weight(1f).clickable { onSelect(target) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        index,
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = if (active) palette.accentText else palette.muted
                    )
                    Text(
                        stringResource(label).uppercase(Locale.forLanguageTag("ru")),
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 0.5.sp),
                        color = if (active) palette.ink else palette.muted
                    )
                }
            }
        }
    }
}
