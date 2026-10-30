package com.robotcentinela.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.robotcentinela.app.R
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.identity.domain.HomeScope
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.ui.app.uiDependencies
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.EmptyState
import com.robotcentinela.app.ui.events.AlertDetailSheet
import com.robotcentinela.app.ui.events.EventsScreen
import com.robotcentinela.app.ui.history.HistoryScreen
import com.robotcentinela.app.ui.map.MapScreen
import com.robotcentinela.app.ui.missions.MissionsScreen
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.settings.SettingsSheet
import com.robotcentinela.app.ui.summary.ChannelDetailSheet
import com.robotcentinela.app.ui.summary.SummaryScreen
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing
import kotlinx.serialization.Serializable

// --- Rutas (navegación con tipos, Navigation 2.8+) ----------------------------------------------

@Serializable data object SummaryRoute

@Serializable data object MapRoute

@Serializable data object MissionsRoute

@Serializable data object HistoryRoute

@Serializable data object EventsRoute


/** Destinos de la barra de navegación (mockup móvil: Resumen, Mapa, Misiones e Historial). Eventos se abre con la campana. */
private enum class Tab(val label: String, val icon: Int, val route: Any) {
    Summary("Resumen", R.drawable.ic_assignment, SummaryRoute),
    MapTab("Mapa", R.drawable.ic_map, MapRoute),
    Missions("Misiones", R.drawable.ic_edit_road, MissionsRoute),
    History("Historial", R.drawable.ic_history, HistoryRoute),
}

@Composable
fun MainScreen(scope: HomeScope, onChangeHome: () -> Unit) {
    val deps = uiDependencies
    val vm: MainViewModel = viewModel(key = "main-${scope.home.id.value}") { MainViewModel(deps.container, scope) }
    val live by vm.state.collectAsStateWithLifecycle()
    val colors = CentinelaTheme.colors

    // La sincronización solo corre con la pantalla visible (RB28).
    LifecycleStartEffect(vm) {
        vm.onStart()
        onStopOrDispose { vm.onStop() }
    }

    val navController = rememberNavController()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf<ChannelKey?>(null) }
    var selectedAlert by remember { mutableStateOf<Alert?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide && live.robot != null) SideRail(navController)
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = colors.background,
                contentWindowInsets = if (wide || live.robot == null) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0),
                topBar = {
                    Header(
                        displayName = deps.preferences.displayName(),
                        homeName = scope.home.name,
                        robotCode = live.robot?.publicCode,
                        robots = live.robots.map { it.publicCode },
                        openAlerts = if (live.bannerAlert != null) 1 else 0,
                        onRobotSelected = { code -> live.robots.firstOrNull { it.publicCode == code }?.let(vm::selectRobot) },
                        onBell = { navController.navigateTab(EventsRoute) },
                        onMenu = { settingsOpen = true },
                    )
                },
                bottomBar = { if (!wide && live.robot != null) BottomBar(navController) },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    AnimatedVisibility(
                        visible = live.bannerAlert != null,
                        enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(180)),
                    ) {
                        live.bannerAlert?.let { alert ->
                            Banner(
                                text = "${OperationPresentation.severity(alert.severity).text}: ${OperationPresentation.alertTitle(alert.type)}",
                                actionLabel = "Ver detalle",
                                onAction = { selectedAlert = alert },
                                critical = true,
                                onDismiss = { vm.dismissBanner(alert.id) },
                            )
                        }
                    }
                    if (live.robot == null) {
                        EmptyState(
                            icon = R.drawable.ic_info,
                            title = "Este domicilio no tiene robot",
                            message = "No hay un robot vinculado a ${scope.home.name}. Cuando se vincule uno, aquí verás " +
                                "sus lecturas, su posición estimada y sus eventos.",
                            actionLabel = "Cambiar de domicilio",
                            onAction = onChangeHome,
                        )
                    } else {
                        MainNavHost(
                            navController = navController,
                            live = live,
                            vm = vm,
                            onOpenChannel = { selectedChannel = it },
                            onOpenAlert = { selectedAlert = it },
                            onChangeHome = onChangeHome,
                        )
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        SettingsSheet(
            homeName = scope.home.name,
            onChangeHome = { settingsOpen = false; onChangeHome() },
            onDismiss = { settingsOpen = false },
        )
    }
    val channelStatus = selectedChannel?.let { live.channelStatuses[it] }
    if (channelStatus != null) {
        ChannelDetailSheet(status = channelStatus, zone = live.zone, onDismiss = { selectedChannel = null })
    }
    selectedAlert?.let { alert ->
        AlertDetailSheet(alert = alert, zone = live.zone, today = live.today, onDismiss = { selectedAlert = null })
    }
}

@Composable
private fun MainNavHost(
    navController: NavHostController,
    live: LiveUiState,
    vm: MainViewModel,
    onOpenChannel: (ChannelKey) -> Unit,
    onOpenAlert: (Alert) -> Unit,
    onChangeHome: () -> Unit,
) {
    val robot = live.robot ?: return
    NavHost(
        navController = navController,
        startDestination = SummaryRoute,
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        modifier = Modifier.fillMaxSize(),
    ) {
        composable<SummaryRoute> {
            SummaryScreen(
                live = live,
                onRangeChange = vm::setAlertRange,
                onOpenChannel = onOpenChannel,
                onOpenMap = { navController.navigateTab(MapRoute) },
                onOpenEvents = { navController.navigateTab(EventsRoute) },
                onOpenAlert = onOpenAlert,
                onRetryAlerts = vm::retryAlerts,
            )
        }
        composable<MapRoute> {
            MapScreen(live = live)
        }
        composable<MissionsRoute> {
            MissionsScreen(
                robot = robot,
                zone = live.zone,
                currentMissionId = live.missionId,
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(robot = robot, zone = live.zone, onChangeHome = onChangeHome)
        }
        composable<EventsRoute> {
            EventsScreen(robot = robot, zone = live.zone, onOpenAlert = onOpenAlert, onChangeHome = onChangeHome)
        }
    }
}

/** Navega a una pestaña conservando su estado y sin apilar copias (gesto atrás vuelve al Resumen). */
private fun NavHostController.navigateTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val colors = CentinelaTheme.colors
    val entry by navController.currentBackStackEntryAsState()
    NavigationBar(containerColor = colors.chrome, contentColor = colors.onChrome) {
        Tab.entries.forEach { tab ->
            val selected = entry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateTab(tab.route) },
                icon = { AppIcon(tab.icon, size = 24.dp, tint = if (selected) colors.onWarning else colors.onChrome) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = colors.warning,
                    selectedTextColor = colors.onChrome,
                    unselectedTextColor = colors.onChrome,
                ),
            )
        }
    }
}

/** Barra lateral para pantallas anchas (tabletas), equivalente a la de la versión web. */
@Composable
private fun SideRail(navController: NavHostController) {
    val colors = CentinelaTheme.colors
    val entry by navController.currentBackStackEntryAsState()
    NavigationRail(containerColor = colors.chrome, contentColor = colors.onChrome, modifier = Modifier.statusBarsPadding()) {
        Tab.entries.forEach { tab ->
            val selected = entry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
            NavigationRailItem(
                selected = selected,
                onClick = { navController.navigateTab(tab.route) },
                icon = { AppIcon(tab.icon, size = 24.dp, tint = if (selected) colors.onWarning else colors.onChrome) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = colors.warning,
                    selectedTextColor = colors.onChrome,
                    unselectedTextColor = colors.onChrome,
                ),
            )
        }
    }
}

/**
 * Cabecera del mockup: avatar, saludo, domicilio y robot, avisos y ajustes.
 */
@Composable
private fun Header(
    displayName: String?,
    homeName: String,
    robotCode: String?,
    robots: List<String>,
    openAlerts: Int,
    onRobotSelected: (String) -> Unit,
    onBell: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = CentinelaTheme.colors
    var robotMenu by remember { mutableStateOf(false) }
    val greeting = if (!displayName.isNullOrBlank()) "Hola, $displayName!" else "Hola!"
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .statusBarsPadding()
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        AppIcon(R.drawable.ic_account_circle, size = 50.dp, tint = colors.onChrome)
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyLarge, color = colors.onChrome, maxLines = 1)
            val place = listOfNotNull(homeName, robotCode).joinToString(" · ")
            if (robots.size > 1) {
                Box {
                    TextButton(
                        onClick = { robotMenu = true },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(place, style = MaterialTheme.typography.titleSmall, color = colors.onChrome, maxLines = 1)
                        AppIcon(R.drawable.ic_expand_more, contentDescription = "Cambiar de robot", size = 18.dp, tint = colors.onChrome)
                    }
                    DropdownMenu(expanded = robotMenu, onDismissRequest = { robotMenu = false }) {
                        robots.forEach { code ->
                            DropdownMenuItem(text = { Text("Robot $code") }, onClick = { robotMenu = false; onRobotSelected(code) })
                        }
                    }
                }
            } else {
                Text(place, style = MaterialTheme.typography.titleSmall, color = colors.onChrome, maxLines = 1)
            }
        }
        IconButton(onClick = onBell) {
            BadgedBox(badge = { if (openAlerts > 0) Badge(containerColor = colors.warning, contentColor = colors.onWarning) }) {
                AppIcon(
                    R.drawable.ic_notifications,
                    contentDescription = if (openAlerts > 0) "Eventos: hay un aviso crítico abierto" else "Eventos",
                    size = 24.dp,
                    tint = colors.onChrome,
                )
            }
        }
        IconButton(onClick = onMenu) {
            AppIcon(R.drawable.ic_settings, contentDescription = "Ajustes y cuenta", size = 24.dp, tint = colors.onChrome)
        }
    }
}

/**
 * Aviso persistente en la sesión (§12.5): dirige al detalle sin bloquear la interacción. Se anuncia a
 * los lectores de pantalla como región viva.
 */
@Composable
private fun Banner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    critical: Boolean,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = CentinelaTheme.colors
    val container = if (critical) colors.critical else colors.warning
    val content = if (critical) colors.onCritical else colors.onWarning
    Row(
        Modifier
            .fillMaxWidth()
            .background(container)
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        AppIcon(if (critical) R.drawable.ic_error else R.drawable.ic_warning, tint = content)
        Text(text, style = MaterialTheme.typography.bodySmall, color = content, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction, shape = CentinelaShapes.control) { Text(actionLabel, color = content) }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                AppIcon(R.drawable.ic_close, contentDescription = "Ocultar aviso", tint = content)
            }
        }
    }
}
