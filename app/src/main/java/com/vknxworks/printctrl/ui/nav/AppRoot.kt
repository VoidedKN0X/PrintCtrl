package com.vknxworks.printctrl.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.LocalBottomClearance
import com.vknxworks.printctrl.ui.components.LocalHazeState
import com.vknxworks.printctrl.ui.printers.CameraScreen
import com.vknxworks.printctrl.ui.printers.PrinterDetailScreen
import com.vknxworks.printctrl.ui.printers.PrintersScreen
import com.vknxworks.printctrl.ui.settings.AccountScreen
import com.vknxworks.printctrl.ui.settings.SettingsScreen
import com.vknxworks.printctrl.ui.setup.ServerSetupScreen
import com.vknxworks.printctrl.ui.setup.WelcomeScreen
import com.vknxworks.printctrl.ui.spools.SpoolsScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val setup by c.servers.setup.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val s = setup
        when {
            s == null -> LoadingView()
            s.isEmpty -> WelcomeScreen()
            else -> {
                val tabs = buildList {
                    if (s.bambuddy != null) addAll(listOf(Tab.PRINTERS, Tab.QUEUE, Tab.ARCHIVE))
                    if (s.spoolman != null) add(Tab.SPOOLS)
                }
                // A server added or removed changes the tabs; start fresh with the new set.
                key(tabs) { MainScaffold(tabs) }
            }
        }
    }
}

enum class Tab(val route: Any, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    PRINTERS(PrintersRoute, R.string.tab_printers, Icons.Outlined.Print, Icons.Filled.Print),
    QUEUE(QueueRoute, R.string.tab_queue, Icons.Outlined.Queue, Icons.Filled.Queue),
    ARCHIVE(ArchiveRoute, R.string.tab_archive, Icons.Outlined.History, Icons.Filled.History),
    SPOOLS(SpoolsRoute, R.string.tab_spools, Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
}

@Composable
private fun MainScaffold(tabs: List<Tab>) {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = tabs.indexOfFirst { tab -> dest?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    val items = tabs.map { TabItem(stringResource(it.label), it.icon, it.selectedIcon) }

    fun selectTab(i: Int) {
        navController.navigate(tabs[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Notifications, the widget and the tile ask for a printer page.
    val c = LocalContext.current.container
    val open by c.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(open) {
        val (id, stop) = open ?: return@LaunchedEffect
        c.openRequest.value = null
        if (Tab.PRINTERS in tabs) navController.navigate(PrinterRoute(id, stop)) { launchSingleTop = true }
    }
    val openSpool by c.openSpoolRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openSpool) {
        val id = openSpool ?: return@LaunchedEffect
        c.openSpoolRequest.value = null
        if (Tab.SPOOLS in tabs) {
            navController.navigate(SpoolsRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
            if (id >= 0) navController.navigate(SpoolRoute(id))
        }
    }
    // A file shared to PrintCtrl from another app.
    val shared by c.sharedFile.collectAsStateWithLifecycle()
    shared?.let { uri ->
        if (Tab.PRINTERS in tabs) {
            com.vknxworks.printctrl.ui.queue.SharedUploadDialog(uri, onDone = { id ->
                c.sharedFile.value = null
                if (id != null) navController.navigate(SendRoute(id))
            })
        } else {
            LaunchedEffect(uri) { c.sharedFile.value = null }
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp && tabs.size > 1) {
                // Tablets and unfolded foldables: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        items.forEachIndexed { i, item ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { AppNavHost(navController, tabs.first()) }
                }
            } else {
                PhoneLayout(
                    navController = navController,
                    start = tabs.first(),
                    items = items,
                    // One tab needs no tab bar.
                    showTabs = selectedIndex >= 0 && tabs.size > 1,
                    selectedIndex = selectedIndex,
                    onSelect = ::selectTab,
                )
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under a floating glass island.
 * The island shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    start: Tab,
    items: List<TabItem>,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                AppNavHost(navController, start)
            }
            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } },
            ) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    GlassTabBar(items = items, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, start: Tab) {
    NavHost(navController, startDestination = start.route) {
        composable<PrintersRoute> { PrintersScreen() }
        composable<QueueRoute> { com.vknxworks.printctrl.ui.queue.QueueScreen() }
        composable<ArchiveRoute> { com.vknxworks.printctrl.ui.archive.ArchiveScreen() }
        composable<SpoolsRoute> { SpoolsScreen() }
        composable<PrinterRoute> {
            val r = it.toRoute<PrinterRoute>()
            PrinterDetailScreen(r.id, r.confirmStop)
        }
        composable<PrinterFilesRoute> { com.vknxworks.printctrl.ui.printers.PrinterFilesScreen(it.toRoute<PrinterFilesRoute>().id) }
        composable<SpoolRoute> { com.vknxworks.printctrl.ui.spools.SpoolDetailScreen(it.toRoute<SpoolRoute>().id) }
        composable<AddSpoolRoute> { com.vknxworks.printctrl.ui.spools.AddSpoolScreen() }
        composable<ScanRoute> { com.vknxworks.printctrl.ui.spools.ScanScreen() }
        composable<AssignSlotRoute> { com.vknxworks.printctrl.ui.spools.AssignSlotScreen(it.toRoute<AssignSlotRoute>().spoolId) }
        composable<LibraryRoute> { com.vknxworks.printctrl.ui.queue.LibraryScreen() }
        composable<SendRoute> {
            val r = it.toRoute<SendRoute>()
            com.vknxworks.printctrl.ui.queue.SendScreen(r.id, r.archive)
        }
        composable<ArchiveDetailRoute> { com.vknxworks.printctrl.ui.archive.ArchiveDetailScreen(it.toRoute<ArchiveDetailRoute>().id) }
        composable<TimelapseRoute> { com.vknxworks.printctrl.ui.archive.TimelapseScreen(it.toRoute<TimelapseRoute>().archiveId) }
        composable<NotificationsRoute> { com.vknxworks.printctrl.ui.settings.NotificationsScreen() }
        composable<CameraRoute> { CameraScreen(it.toRoute<CameraRoute>().id) }
        composable<AccountRoute> { AccountScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<ServerSetupRoute> {
            val navigator = LocalNavigator.current
            val kind = ServerKind.valueOf(it.toRoute<ServerSetupRoute>().kind)
            ServerSetupScreen(kind, onDone = navigator::back, onCancel = navigator::back)
        }
    }
}
