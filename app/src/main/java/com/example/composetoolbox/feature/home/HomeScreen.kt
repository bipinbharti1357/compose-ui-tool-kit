@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.composetoolbox.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.composetoolbox.R
import com.example.composetoolbox.feature.expense.ExpenseViewModel
import com.example.composetoolbox.feature.parking.ParkingViewModel
import com.example.composetoolbox.feature.todo.TodoViewModel
import com.example.composetoolbox.navigation.Routes
import com.example.composetoolbox.ui.LocalAppSettings
import com.example.composetoolbox.ui.components.DrawerMenuButton
import com.example.composetoolbox.ui.components.UniformWidthRow
import com.example.composetoolbox.ui.formatMoney
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.roundToInt

private data class QuickAction(val label: String, val icon: ImageVector, val route: String)

private val DailyTips = listOf(
    "Swipe a task left to delete it, then tap Undo in the snackbar.",
    "Double-tap a task card to mark it done.",
    "Export your expenses as CSV or PDF from the download icon.",
    "Drag a waiting vehicle onto a free parking slot to park it.",
    "Pinch the parking map to zoom; double-tap to reset.",
)

private val ComponentsUsed = listOf(
    "Scaffold", "LazyColumn", "LazyVerticalGrid", "Staggered grids", "Pager", "Canvas",
    "ConstraintLayout", "BottomSheets", "Dialogs", "SwipeToDismiss", "PullToRefresh",
    "Drag & drop", "Shared elements", "Navigation", "AndroidView", "ComposeView",
)

@Composable
fun HomeScreen(
    todoVm: TodoViewModel,
    expenseVm: ExpenseViewModel,
    parkingVm: ParkingViewModel,
    onNavigate: (String) -> Unit,
) {
    val todos by todoVm.todos.collectAsStateWithLifecycle()
    val expenses by expenseVm.expenses.collectAsStateWithLifecycle()
    val budget by expenseVm.budget.collectAsStateWithLifecycle()
    val parking by parkingVm.state.collectAsStateWithLifecycle()
    val currency = LocalAppSettings.current.currency

    val pending = todos.count { !it.done }
    val todoProgress = if (todos.isEmpty()) 0f else todos.count { it.done } / todos.size.toFloat()
    val monthSpent = expenses.filter { YearMonth.from(it.date) == YearMonth.now() }.sumOf { it.amount }
    val occupied = parking.slots.count { !it.isFree }
    val occupancy = occupied / parking.slots.size.coerceAtLeast(1).toFloat()

    // produceState: turns a suspending "load" into Compose state (null while loading)
    val tip by produceState<String?>(initialValue = null) {
        delay(1_500)
        value = DailyTips.random()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val quickActions = remember {
        listOf(
            QuickAction("Add a task", Icons.Filled.AddTask, Routes.TODO),
            QuickAction("Log an expense", Icons.Filled.Receipt, Routes.EXPENSE),
            QuickAction("Export report", Icons.Filled.FileDownload, Routes.EXPENSE),
            QuickAction("Park a car", Icons.Filled.DirectionsCar, Routes.PARKING),
            QuickAction("Lot map", Icons.Filled.Map, Routes.PARKING),
            QuickAction("Settings", Icons.Filled.Settings, Routes.SETTINGS),
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(greeting()) },
                navigationIcon = { DrawerMenuButton() },
                actions = { InfoPopupButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(170.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero", span = StaggeredGridItemSpan.FullLine) {
                HeroCard(
                    todoProgress = todoProgress,
                    stats = listOf("$pending to do", formatMoney(monthSpent, currency), "$occupied parked"),
                )
            }
            item(key = "tip", span = StaggeredGridItemSpan.FullLine) { TipCard(tip) }
            item(key = "todo") {
                FeatureCard(
                    title = "Todo list",
                    icon = Icons.Filled.Checklist,
                    value = "$pending pending",
                    caption = "${(todoProgress * 100).roundToInt()}% complete",
                    progress = todoProgress,
                    badge = pending,
                    onClick = { onNavigate(Routes.TODO) },
                )
            }
            item(key = "expense") {
                FeatureCard(
                    title = "Expenses",
                    icon = Icons.Filled.AccountBalanceWallet,
                    value = formatMoney(monthSpent, currency),
                    caption = "of ${formatMoney(budget.toDouble(), currency)} budget this month",
                    progress = (monthSpent / budget).toFloat(),
                    badge = null,
                    onClick = { onNavigate(Routes.EXPENSE) },
                )
            }
            item(key = "parking") {
                FeatureCard(
                    title = "Parking",
                    icon = Icons.Filled.LocalParking,
                    value = "$occupied / ${parking.slots.size}",
                    caption = "${parking.queue.size} waiting at the gate",
                    progress = occupancy,
                    badge = parking.queue.size,
                    onClick = { onNavigate(Routes.PARKING) },
                )
            }
            item(key = "quickTitle", span = StaggeredGridItemSpan.FullLine) {
                Text("Quick actions", style = MaterialTheme.typography.titleMedium)
            }
            item(key = "quick", span = StaggeredGridItemSpan.FullLine) {
                // LazyHorizontalStaggeredGrid: two rows, items of different widths
                LazyHorizontalStaggeredGrid(
                    rows = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    horizontalItemSpacing = 8.dp,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(quickActions) { action ->
                        AssistChip(
                            onClick = { onNavigate(action.route) },
                            label = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, contentDescription = null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            item(key = "builtWith", span = StaggeredGridItemSpan.FullLine) { BuiltWithCard() }
        }
    }
}

@Composable
private fun HeroCard(todoProgress: Float, stats: List<String>) {
    // rememberInfiniteTransition: the logo "breathes" forever
    val infinite = rememberInfiniteTransition(label = "hero")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    // Animatable: imperatively animate the ring whenever progress changes
    val ring = remember { Animatable(0f) }
    LaunchedEffect(todoProgress) { ring.animateTo(todoProgress, tween(900)) }

    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 8.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(stroke / 2, stroke / 2)
                        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                        drawArc(ringColor, -90f, 360f * ring.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Image(
                        painter = painterResource(R.drawable.ic_app_logo),
                        contentDescription = "App logo",
                        modifier = Modifier
                            .size(60.dp)
                            .graphicsLayer {
                                scaleX = pulse
                                scaleY = pulse
                            },
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Compose Toolbox", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${(todoProgress * 100).roundToInt()}% of today's tasks done",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // SubcomposeLayout: all three pills get the width of the widest one
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                UniformWidthRow(items = stats) { label ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TipCard(tip: String?) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        // Crossfade between the loading spinner and the loaded tip
        Crossfade(targetState = tip, label = "tip") { current ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (current == null) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Loading today's tip…")
                } else {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(current)
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    icon: ImageVector,
    value: String,
    caption: String,
    progress: Float,
    badge: Int?,
    onClick: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), label = "featureProgress")
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgedBox(badge = { if (badge != null && badge > 0) Badge { Text("$badge") } }) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** FlowColumn inside a horizontally scrolling, fixed-height box: items wrap into new columns. */
@Composable
private fun BuiltWithCard() {
    Column {
        Text("Built with", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ComponentsUsed.forEach { name ->
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/** Popup: a lightweight floating surface anchored to the info button. */
@Composable
private fun InfoPopupButton() {
    var show by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { show = !show }) {
            Icon(Icons.Filled.Info, contentDescription = "About this app")
        }
        if (show) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 140),
                onDismissRequest = { show = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier.widthIn(max = 260.dp),
                ) {
                    Text(
                        "Three mini-apps in one: a Todo list, an Expense tracker with CSV/PDF export, " +
                            "and Car Parking management. Each screen demonstrates a different set of Compose components.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
