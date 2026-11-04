@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.composetoolbox.feature.expense

import android.content.Intent
import android.net.Uri
import android.view.ContextThemeWrapper
import android.widget.CalendarView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.composetoolbox.ui.LocalAppSettings
import com.example.composetoolbox.ui.components.DrawerMenuButton
import com.example.composetoolbox.ui.components.EmptyState
import com.example.composetoolbox.ui.formatMoney
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun ExpenseScreen(vm: ExpenseViewModel) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val currency = LocalAppSettings.current.currency // CompositionLocal
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val categories: List<ExpenseCategory?> = remember { listOf<ExpenseCategory?>(null) + ExpenseCategory.entries }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val maxAmount = remember(expenses) {
        val max = (expenses.maxOfOrNull { it.amount } ?: 0.0).toFloat().coerceAtLeast(1_000f)
        ceil(max / 500f) * 500f
    }
    var amountRange by remember(maxAmount) { mutableStateOf(0f..maxAmount) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var presetCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val selectedCategory = categories[selectedTab]
    val filtered = remember(expenses, selectedCategory, amountRange) {
        expenses
            .filter { (selectedCategory == null || it.category == selectedCategory) && it.amount.toFloat() in amountRange }
            .sortedByDescending { it.date }
    }
    val thisMonth = remember(expenses) { expenses.filter { YearMonth.from(it.date) == YearMonth.now() } }
    val monthSpent = thisMonth.sumOf { it.amount }
    val byCategory = remember(thisMonth) {
        thisMonth.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
            .toMap()
    }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun export(uri: Uri?, label: String, writer: (OutputStream) -> Unit) {
        if (uri == null) return // user cancelled the file picker
        val count = filtered.size
        scope.launch {
            exporting = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use(writer) ?: error("Could not open file")
                }
            }
            exporting = false
            showMessage(
                if (result.isSuccess) "$label report saved ($count expenses)"
                else "Export failed: ${result.exceptionOrNull()?.message}",
            )
        }
    }

    // Storage Access Framework: the user picks where to save, no permissions needed
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        export(uri, "CSV") { out -> ReportExporter.writeCsv(out, filtered, currency) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        export(uri, "PDF") { out -> ReportExporter.writePdf(out, filtered, currency) }
    }

    fun shareSummary() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Expense summary")
            putExtra(Intent.EXTRA_TEXT, ReportExporter.buildTextSummary(filtered, currency))
        }
        context.startActivity(Intent.createChooser(intent, "Share summary"))
    }

    fun deleteWithUndo(expense: Expense) {
        vm.delete(expense)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar("Expense deleted", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) vm.restore(expense)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Expense Tracker") },
                navigationIcon = { DrawerMenuButton() },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Export report") } },
                        state = rememberTooltipState(),
                    ) {
                        Box {
                            IconButton(onClick = { exportMenuOpen = true }) {
                                Icon(Icons.Filled.FileDownload, contentDescription = "Export report")
                            }
                            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export CSV (Excel)") },
                                    leadingIcon = { Icon(Icons.Filled.TableChart, null) },
                                    onClick = {
                                        exportMenuOpen = false
                                        csvLauncher.launch("expense-report-${LocalDate.now()}.csv")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export PDF") },
                                    leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                                    onClick = {
                                        exportMenuOpen = false
                                        pdfLauncher.launch("expense-report-${LocalDate.now()}.pdf")
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Share summary") },
                                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                                    onClick = { exportMenuOpen = false; shareSummary() },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add expense") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                expanded = fabExpanded, // collapses to an icon once you scroll
                onClick = { presetCategory = null; showAddDialog = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AnimatedVisibility(visible = exporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(category?.label ?: "All") },
                        icon = { Icon(category?.icon ?: Icons.Filled.Apps, contentDescription = null) },
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") { SummaryCard(monthSpent, budget, byCategory, currency) }
                item(key = "budget") { BudgetCard(budget, monthSpent, currency, onBudgetChange = vm::setBudget) }
                item(key = "quick") {
                    QuickAddGrid(onQuickAdd = { category -> presetCategory = category; showAddDialog = true })
                }
                item(key = "filter") {
                    AmountFilterCard(
                        range = amountRange,
                        max = maxAmount,
                        currency = currency,
                        resultCount = filtered.size,
                        onRangeChange = { amountRange = it },
                        onReset = { amountRange = 0f..maxAmount; selectedTab = 0 },
                    )
                }
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = "No matching expenses",
                            message = "Change the category tab or amount filter.",
                            icon = Icons.Filled.Receipt,
                        )
                    }
                }
                filtered.groupBy { it.date }.forEach { (date, dayItems) ->
                    stickyHeader(key = "header-$date") {
                        DateHeader(date, dayItems.sumOf { it.amount }, currency)
                    }
                    items(dayItems, key = { it.id }) { expense ->
                        ExpenseRow(
                            expense = expense,
                            currency = currency,
                            onDelete = { deleteWithUndo(expense) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            initialCategory = presetCategory,
            currency = currency,
            onDismiss = { showAddDialog = false },
            onSave = { amount, category, note, date ->
                vm.add(amount, category, note, date)
                showAddDialog = false
                showMessage("Added ${formatMoney(amount, currency)} to ${category.label}")
            },
        )
    }
}

@Composable
private fun SummaryCard(
    monthSpent: Double,
    budget: Float,
    byCategory: Map<ExpenseCategory, Double>,
    currency: String,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "This month · ${YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // AnimatedContent: the total slides up/down when it changes
                    AnimatedContent(
                        targetState = monthSpent,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                            } else {
                                (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                            }
                        },
                        label = "monthTotal",
                    ) { value ->
                        Text(
                            formatMoney(value, currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("spent", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    byCategory.entries.take(4).forEach { (category, amount) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(category.color),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(category.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(formatMoney(amount, currency), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                CategoryDonut(byCategory, Modifier.size(120.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                MiniStat("Budget", formatMoney(budget.toDouble(), currency), Modifier.weight(1f))
                VerticalDivider()
                MiniStat("Left", formatMoney((budget - monthSpent).coerceAtLeast(0.0), currency), Modifier.weight(1f))
                VerticalDivider()
                MiniStat("Avg / day", formatMoney(monthSpent / LocalDate.now().dayOfMonth, currency), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Canvas donut chart; arcs sweep in on first composition via animateFloatAsState. */
@Composable
fun CategoryDonut(byCategory: Map<ExpenseCategory, Double>, modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val sweepProgress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "donutSweep",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = byCategory.values.sum()

    Canvas(modifier) {
        val stroke = 20.dp.toPx()
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        if (total <= 0.0) return@Canvas
        var startAngle = -90f
        byCategory.forEach { (category, amount) ->
            val sweep = (amount / total * 360.0).toFloat() * sweepProgress
            drawArc(category.color, startAngle, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            startAngle += sweep
        }
    }
}

/** Slider + LinearProgressIndicator; the colour animates as you approach the budget. */
@Composable
private fun BudgetCard(budget: Float, spent: Double, currency: String, onBudgetChange: (Float) -> Unit) {
    val ratio = (spent / budget).toFloat()
    val animatedRatio by animateFloatAsState(ratio.coerceIn(0f, 1f), label = "budgetRatio")
    val barColor by animateColorAsState(
        when {
            ratio >= 1f -> MaterialTheme.colorScheme.error
            ratio >= 0.8f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "budgetColor",
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatMoney(budget.toDouble(), currency), style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = budget,
                onValueChange = onBudgetChange,
                valueRange = 5_000f..100_000f,
                steps = 18, // 5,000 increments
            )
            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = barColor,
            )
            val percent = (ratio * 100).roundToInt()
            Text(
                when {
                    ratio >= 1f -> "Over budget by ${formatMoney(spent - budget, currency)}"
                    ratio >= 0.8f -> "Careful — $percent% of budget used"
                    else -> "$percent% of budget used"
                },
                style = MaterialTheme.typography.bodySmall,
                color = barColor,
            )
        }
    }
}

/** LazyHorizontalGrid of ElevatedButtons: one-tap shortcuts that pre-select a category. */
@Composable
private fun QuickAddGrid(onQuickAdd: (ExpenseCategory) -> Unit) {
    Column {
        Text("Quick add", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(ExpenseCategory.entries) { category ->
                ElevatedButton(onClick = { onQuickAdd(category) }) {
                    Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(category.label)
                }
            }
        }
    }
}

/** RangeSlider filter + FilledTonalButton reset. */
@Composable
private fun AmountFilterCard(
    range: ClosedFloatingPointRange<Float>,
    max: Float,
    currency: String,
    resultCount: Int,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onReset: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Amount filter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onReset) { Text("Reset filters") }
            }
            RangeSlider(value = range, onValueChange = onRangeChange, valueRange = 0f..max)
            Row {
                Text(formatMoney(range.start.toDouble(), currency), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(formatMoney(range.endInclusive.toDouble(), currency), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "$resultCount matching expense(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, total: Double, currency: String) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(formatMoney(total, currency), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Long-press a row to open a DropdownMenu with "Delete". */
@Composable
private fun ExpenseRow(expense: Expense, currency: String, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        ListItem(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true }),
            headlineContent = { Text(expense.note.ifBlank { expense.category.label }) },
            supportingContent = { Text(expense.category.label) },
            leadingContent = {
                Surface(shape = CircleShape, color = expense.category.color.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(expense.category.icon, contentDescription = null, tint = expense.category.color)
                    }
                }
            },
            trailingContent = {
                Text(
                    "-" + formatMoney(expense.amount, currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/**
 * Custom Dialog with OutlinedTextField, ExposedDropdownMenuBox, TextField and a
 * legacy android.widget.CalendarView embedded through AndroidView (interop).
 */
@Composable
fun AddExpenseDialog(
    initialCategory: ExpenseCategory?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Double, ExpenseCategory, String, LocalDate) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(initialCategory ?: ExpenseCategory.FOOD) }
    var note by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add expense", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount") },
                    prefix = { Text(currency) },
                    singleLine = true,
                    isError = amountText.isNotEmpty() && amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = categoryMenuOpen, onExpandedChange = { categoryMenuOpen = it }) {
                    OutlinedTextField(
                        value = category.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        leadingIcon = { Icon(category.icon, contentDescription = null, tint = category.color) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        ExpenseCategory.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                leadingIcon = { Icon(option.icon, contentDescription = null, tint = option.color) },
                                onClick = { category = option; categoryMenuOpen = false },
                            )
                        }
                    }
                }
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Date · ${date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))}",
                    style = MaterialTheme.typography.labelLarge,
                )
                // AndroidView: a classic View inside Compose. key() rebuilds it if the theme flips.
                key(isDark) {
                    AndroidView(
                        factory = { ctx ->
                            val themed = ContextThemeWrapper(
                                ctx,
                                if (isDark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light,
                            )
                            CalendarView(themed).apply {
                                maxDate = System.currentTimeMillis()
                                setOnDateChangeListener { _, year, month, day ->
                                    date = LocalDate.of(year, month + 1, day)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { if (amount != null) onSave(amount, category, note.trim(), date) },
                        enabled = amount != null && amount > 0,
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryDonutPreview() {
    ComposeToolboxTheme(dynamicColor = false) {
        CategoryDonut(
            mapOf(ExpenseCategory.FOOD to 900.0, ExpenseCategory.BILLS to 1_800.0, ExpenseCategory.TRAVEL to 600.0),
            Modifier
                .padding(16.dp)
                .size(140.dp),
        )
    }
}
