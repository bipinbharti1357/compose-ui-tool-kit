package com.example.composetoolbox.navigation

import android.content.Intent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.composetoolbox.LegacyActivity
import com.example.composetoolbox.R
import com.example.composetoolbox.data.AppDatabase
import com.example.composetoolbox.data.RoomChallengeStore
import com.example.composetoolbox.data.RoomExpenseStore
import com.example.composetoolbox.data.RoomTodoStore
import com.example.composetoolbox.feature.challenge.ChallengeScreen
import com.example.composetoolbox.feature.challenge.ChallengeViewModel
import com.example.composetoolbox.feature.expense.ExpenseScreen
import com.example.composetoolbox.feature.expense.ExpenseViewModel
import com.example.composetoolbox.feature.home.HomeScreen
import com.example.composetoolbox.feature.parking.ParkingScreen
import com.example.composetoolbox.feature.parking.ParkingViewModel
import com.example.composetoolbox.feature.settings.SettingsScreen
import com.example.composetoolbox.feature.settings.SettingsViewModel
import com.example.composetoolbox.feature.todo.TodoScreen
import com.example.composetoolbox.feature.todo.TodoViewModel
import com.example.composetoolbox.ui.LocalOpenDrawer
import com.example.composetoolbox.ui.ScreenTracker
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val TODO = "todo"
    const val EXPENSE = "expense"
    const val PARKING = "parking"
    const val CHALLENGE = "challenge"
    const val SETTINGS = "settings"
}

enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, "Home", Icons.Filled.Dashboard),
    Todo(Routes.TODO, "Todo", Icons.Filled.Checklist),
    Expense(Routes.EXPENSE, "Expenses", Icons.Filled.AccountBalanceWallet),
    Parking(Routes.PARKING, "Parking", Icons.Filled.LocalParking),
    Challenge(Routes.CHALLENGE, "75 Hard", Icons.Filled.LocalFireDepartment),
}

/**
 * Adaptive app shell chosen with BoxWithConstraints:
 *  - phone  (< 600dp): ModalNavigationDrawer + NavigationBar
 *  - medium (< 840dp): ModalNavigationDrawer + NavigationRail
 *  - large  (≥ 840dp): PermanentNavigationDrawer
 */
@Composable
fun AppRoot(settingsVm: SettingsViewModel) {
    val navController = rememberNavController()
    // Activity-scoped ViewModels so Home can summarise all three features
    // Todo, Expense and 75 Hard are saved in the Room database (separate tables per feature)
    val db = AppDatabase.get(LocalContext.current)
    val todoVm: TodoViewModel = viewModel { TodoViewModel(store = RoomTodoStore(db.todoDao())) }
    val expenseVm: ExpenseViewModel = viewModel { ExpenseViewModel(store = RoomExpenseStore(db.expenseDao())) }
    val parkingVm: ParkingViewModel = viewModel()
    val challengeVm: ChallengeViewModel = viewModel { ChallengeViewModel(store = RoomChallengeStore(db.challengeDao())) }

    val todos by todoVm.todos.collectAsStateWithLifecycle()
    val parking by parkingVm.state.collectAsStateWithLifecycle()
    val challenge by challengeVm.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // SideEffect: publish Compose state to a non-Compose object after each successful composition
    SideEffect { ScreenTracker.currentScreen = currentRoute }

    val badges = mapOf(
        TopDestination.Todo to todos.count { !it.done },
        TopDestination.Parking to parking.active.size,
        TopDestination.Challenge to challenge.todayRemaining,
    )

    val navigateTo: (String) -> Unit = remember(navController) {
        { route: String ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // movableContentOf keeps the NavHost (and its state) alive when the layout switches shells
    val navHost = remember(navController) {
        movableContentOf { modifier: Modifier ->
            AppNavHost(navController, todoVm, expenseVm, parkingVm, challengeVm, settingsVm, navigateTo, modifier)
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerContent: @Composable () -> Unit = {
        DrawerContent(
            currentRoute = currentRoute,
            badges = badges,
            onItemClick = { route ->
                navigateTo(route)
                scope.launch { drawerState.close() }
            },
            onLegacyClick = {
                context.startActivity(Intent(context, LegacyActivity::class.java))
                scope.launch { drawerState.close() }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        when {
            maxWidth < 600.dp -> ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen, // don't fight with pagers
                drawerContent = { ModalDrawerSheet { drawerContent() } },
            ) {
                CompositionLocalProvider(LocalOpenDrawer provides { scope.launch { drawerState.open() } }) {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                TopDestination.entries.forEach { destination ->
                                    NavigationBarItem(
                                        selected = currentRoute == destination.route,
                                        onClick = { navigateTo(destination.route) },
                                        icon = { DestinationIcon(destination, badges[destination]) },
                                        label = { Text(destination.label) },
                                    )
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets(0),
                    ) { padding ->
                        navHost(Modifier.padding(padding))
                    }
                }
            }

            maxWidth < 840.dp -> ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = { ModalDrawerSheet { drawerContent() } },
            ) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        header = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                            }
                        },
                    ) {
                        Spacer(Modifier.height(12.dp))
                        TopDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateTo(destination.route) },
                                icon = { DestinationIcon(destination, badges[destination]) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                    navHost(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }

            else -> PermanentNavigationDrawer(
                drawerContent = { PermanentDrawerSheet(Modifier.width(280.dp)) { drawerContent() } },
            ) {
                navHost(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    todoVm: TodoViewModel,
    expenseVm: ExpenseViewModel,
    parkingVm: ParkingViewModel,
    challengeVm: ChallengeViewModel,
    settingsVm: SettingsViewModel,
    navigateTo: (String) -> Unit,
    modifier: Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(220)) },
    ) {
        composable(Routes.HOME) { HomeScreen(todoVm, expenseVm, parkingVm, onNavigate = navigateTo) }
        composable(Routes.TODO) { TodoScreen(todoVm) }
        composable(Routes.EXPENSE) { ExpenseScreen(expenseVm) }
        composable(Routes.PARKING) { ParkingScreen(parkingVm) }
        composable(Routes.CHALLENGE) { ChallengeScreen(challengeVm) }
        composable(Routes.SETTINGS) { SettingsScreen(settingsVm, onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun DestinationIcon(destination: TopDestination, badge: Int?) {
    if (badge != null && badge > 0) {
        BadgedBox(badge = { Badge { Text("$badge") } }) {
            Icon(destination.icon, contentDescription = destination.label)
        }
    } else {
        Icon(destination.icon, contentDescription = destination.label)
    }
}

@Composable
private fun DrawerContent(
    currentRoute: String?,
    badges: Map<TopDestination, Int>,
    onItemClick: (String) -> Unit,
    onLegacyClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_app_logo), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text("Compose Toolbox", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        TopDestination.entries.forEach { destination ->
            NavigationDrawerItem(
                label = { Text(destination.label) },
                selected = currentRoute == destination.route,
                onClick = { onItemClick(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                badge = { badges[destination]?.takeIf { it > 0 }?.let { Text("$it") } },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = currentRoute == Routes.SETTINGS,
            onClick = { onItemClick(Routes.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("Legacy XML screen") },
            selected = false,
            onClick = onLegacyClick,
            icon = { Icon(Icons.Filled.Code, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}
