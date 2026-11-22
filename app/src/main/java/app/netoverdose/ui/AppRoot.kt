package app.netoverdose.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.screens.FontsScreen
import app.netoverdose.ui.screens.HorizonScreen
import app.netoverdose.ui.screens.IdeasScreen
import app.netoverdose.ui.screens.JournalScreen
import app.netoverdose.ui.screens.LabScreen
import app.netoverdose.ui.screens.MainScreen
import app.netoverdose.ui.screens.MoreScreen
import app.netoverdose.ui.screens.StatsScreen
import app.netoverdose.ui.screens.TaskCardSheet
import app.netoverdose.ui.theme.MonoFamily
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Type-safe destinations.
 *
 * The previous string routes were built by concatenation (`"task/$id"`), and a single
 * mistyped route threw IllegalArgumentException at navigation time, crashed the activity
 * and looked like a frozen tab on the next launch. A serialized destination cannot express
 * that class of mistake.
 */
@Serializable
data object MainRoute

@Serializable
data object HorizonRoute

@Serializable
data object StatsRoute

@Serializable
data object MoreRoute

@Serializable
data object LabRoute

@Serializable
data object IdeasRoute

@Serializable
data object JournalRoute

@Serializable
data object FontsRoute

@Serializable
data class TaskRoute(val id: Long)

/** Tabs that keep the bottom bar visible. */
private fun isTopLevel(route: Any?): Boolean = when (route) {
    MainRoute, HorizonRoute, StatsRoute, MoreRoute -> true
    else -> false
}

/**
 * Which tab is highlighted for the current destination.
 *
 * The destination is a NavDestination, not the route object, so the comparison goes through
 * the serialized route name. Comparing the destination itself always fell through to Main,
 * which is why the bar highlighted "Главная" while "Ещё" was on screen.
 */
private fun tabFor(destination: NavDestination?): Any {
    val name = destination?.route.orEmpty()
    return when {
        name.startsWith(HorizonRoute::class.qualifiedName.orEmpty()) -> HorizonRoute
        name.startsWith(StatsRoute::class.qualifiedName.orEmpty()) -> StatsRoute
        name.startsWith(MoreRoute::class.qualifiedName.orEmpty()) ||
            name.startsWith(LabRoute::class.qualifiedName.orEmpty()) ||
            name.startsWith(IdeasRoute::class.qualifiedName.orEmpty()) ||
            name.startsWith(JournalRoute::class.qualifiedName.orEmpty()) ||
            name.startsWith(FontsRoute::class.qualifiedName.orEmpty()) -> MoreRoute
        else -> MainRoute
    }
}

private const val TAB_FADE_MS = 160

@Composable
fun AppRoot(app: NetoverdoseApp) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = tabFor(destination)

    LaunchedEffect(app) {
        app.messages.collect { text -> snackbar.showSnackbar(text) }
    }

    // A shared file always belongs to the main tab: MainScreen is the only destination that
    // owns the intake panel, and a share that arrived while another tab was open used to be
    // dropped on the floor. This observer only routes; it never clears the value, because
    // consumption must stay in one place (MainScreen.takeShare).
    val pendingShare by app.pendingShare.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShare) {
        if (pendingShare == null) return@LaunchedEffect
        // Typed routes are serialized; comparing the route name avoids the internal
        // hasRoute(KClass) overload, which is not part of the public surface.
        val routeName = navController.currentDestination?.route.orEmpty()
        if (!routeName.startsWith(MainRoute::class.qualifiedName.orEmpty())) {
            navController.navigate(MainRoute) {
                popUpTo<MainRoute> { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    // The window background is painted here so the status bar never flashes a colour of its
    // own while a tab is drawn: both bars sit on the same surface as the content.
    Box(Modifier.fillMaxSize().background(LocalDesign.current.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = MainRoute,
                    modifier = Modifier.fillMaxSize(),
                    // A cross-fade only. The old slide-in kept two destinations alive at once
                    // and let taps land on the outgoing screen.
                    enterTransition = { fadeIn(animationSpec = tween(TAB_FADE_MS)) },
                    exitTransition = { fadeOut(animationSpec = tween(TAB_FADE_MS)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(TAB_FADE_MS)) },
                    popExitTransition = { fadeOut(animationSpec = tween(TAB_FADE_MS)) }
                ) {
                    composable<MainRoute> {
                        MainScreen(onOpenTask = { id -> navController.navigate(TaskRoute(id)) })
                    }
                    composable<HorizonRoute> {
                        HorizonScreen(onOpenTask = { id -> navController.navigate(TaskRoute(id)) })
                    }
                    composable<StatsRoute> {
                        StatsScreen(onOpenTask = { id -> navController.navigate(TaskRoute(id)) })
                    }
                    composable<MoreRoute> {
                        MoreScreen(
                            onOpenLab = { navController.navigate(LabRoute) },
                            onOpenIdeas = { navController.navigate(IdeasRoute) },
                            onOpenJournal = { navController.navigate(JournalRoute) },
                            onOpenFonts = { navController.navigate(FontsRoute) }
                        )
                    }
                    composable<JournalRoute> {
                        JournalScreen(onBack = { navController.popBackStack() })
                    }
                    composable<LabRoute> {
                        LabScreen(
                            controller = app.designController,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<IdeasRoute> {
                        IdeasScreen(onBack = { navController.popBackStack() })
                    }
                    composable<FontsRoute> {
                        FontsScreen(
                            controller = app.designController,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    // The destination exists so the card route resolves; the card itself is
                    // drawn as an overlay below (B04).
                    composable<TaskRoute> { }
                }

                // The card is an overlay above the current tab rather than a full screen.
                // toRoute() deserializes the destination back into the typed route, so no
                // string comparison against the route pattern is needed.
                val taskId = remember(entry) {
                    runCatching { entry?.toRoute<TaskRoute>()?.id }.getOrNull()
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
                    val palette = LocalDesign.current
                    Snackbar(
                        snackbarData = data,
                        containerColor = palette.ink,
                        contentColor = palette.bg,
                        actionColor = palette.bg,
                        shape = palette.shape
                    )
                }
            }

            if (isTopLevel(currentTab)) {
                BottomNav(
                    current = currentTab,
                    onSelect = { target -> navigateToTab(navController, target) }
                )
            }
        }
    }
}

/**
 * Switches tabs and keeps the stack shape at MAIN + one tab, so a sub-screen opened from a
 * tab pops back to that tab instead of leaving the app.
 *
 * The popUpTo anchor has to be Main and it has to keep the target tab in the graph; a
 * sub-screen route must always sit directly above its own tab. Without the anchor a tab
 * opened from a sub-screen stacked on top of the sub-screen, and Back left the app.
 */
private fun navigateToTab(navController: NavHostController, target: Any) {
    navController.navigate(target) {
        popUpTo<MainRoute> { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
private fun BottomNav(current: Any, onSelect: (Any) -> Unit) {
    val palette = LocalDesign.current
    val items = listOf(
        Triple<Any, Int, String>(MainRoute, R.string.nav_main, "01"),
        Triple<Any, Int, String>(HorizonRoute, R.string.nav_horizon, "02"),
        Triple<Any, Int, String>(StatsRoute, R.string.nav_stats, "03"),
        Triple<Any, Int, String>(MoreRoute, R.string.nav_more, "04")
    )
    Column(Modifier.fillMaxWidth().background(palette.backdrop).navigationBarsPadding()) {
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
