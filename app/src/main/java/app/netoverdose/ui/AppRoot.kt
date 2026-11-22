package app.netoverdose.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.screens.HorizonScreen
import app.netoverdose.ui.screens.IdeasScreen
import app.netoverdose.ui.screens.JournalScreen
import app.netoverdose.ui.screens.LabScreen
import app.netoverdose.ui.screens.MainScreen
import app.netoverdose.ui.screens.MoreScreen
import app.netoverdose.ui.screens.StatsScreen
import app.netoverdose.ui.screens.StubScreen
import app.netoverdose.ui.screens.TaskCardSheet
import app.netoverdose.ui.theme.MonoFamily
import java.util.Locale

private object Routes {
    const val MAIN = "main"
    const val HORIZON = "horizon"
    const val STATS = "stats"
    const val MORE = "more"
    const val LAB = "more/lab"
    const val IDEAS = "more/ideas"
    const val JOURNAL = "more/journal"
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
            NavHost(
                navController = navController,
                startDestination = Routes.MAIN,
                modifier = Modifier.fillMaxSize(),
                // Cross-fade plus a small lift makes tab switches feel continuous.
                enterTransition = {
                    fadeIn(animationSpec = tween(220)) +
                        slideInVertically(animationSpec = tween(260)) { it / 24 }
                },
                exitTransition = { fadeOut(animationSpec = tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                popExitTransition = { fadeOut(animationSpec = tween(160)) }
            ) {
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
                        onOpenIdeas = { navController.navigate(Routes.IDEAS) },
                        onOpenJournal = { navController.navigate(Routes.JOURNAL) }
                    )
                }
                composable(Routes.JOURNAL) {
                    JournalScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.LAB) {
                    LabScreen(controller = app.designController, onBack = { navController.popBackStack() })
                }
                composable(Routes.IDEAS) {
                    IdeasScreen(onBack = { navController.popBackStack() })
                }
                // The destination must exist so task/{id} navigations and deep links resolve;
                // it renders nothing because the card is drawn as an overlay below (B04).
                composable(
                    Routes.TASK,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { }
            }

            // The card is an overlay above the current tab rather than a full screen (B04).
            val taskEntry by navController.currentBackStackEntryAsState()
            val taskId = remember(taskEntry) {
                if (taskEntry?.destination?.route == Routes.TASK) {
                    taskEntry?.arguments?.getLong("id")
                } else null
            }
            if (taskId != null && taskId != 0L) {
                TaskCardSheet(
                    taskId = taskId,
                    onDismiss = { navController.popBackStack() }
                )
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
    Column(Modifier.fillMaxWidth().background(palette.backdrop).navigationBarsPadding()) {
        // top rule + spectral underline on the active tab, as in the reference
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items.forEach { (target, label, _) ->
                val active = current == target
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .semantics { selected = active }
                        .clickable(role = Role.Tab) { onSelect(target) },
                    contentAlignment = Alignment.Center
                ) {
                    if (active) {
                        RainbowHairline(
                            Modifier.align(Alignment.TopCenter).fillMaxWidth(0.6f),
                            alpha = palette.rainAlpha
                        )
                    }
                    Text(
                        stringResource(label).uppercase(Locale.forLanguageTag("ru")),
                        style = TextStyle(
                            fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 0.8.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (active) palette.ink else palette.muted
                    )
                }
            }
        }
    }
}
