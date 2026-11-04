@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalAnimationGraphicsApi::class,
    ExperimentalFoundationApi::class,
    FlowPreview::class,
)

package com.example.composetoolbox.feature.todo

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.composetoolbox.R
import com.example.composetoolbox.ui.components.DrawerMenuButton
import com.example.composetoolbox.ui.components.EmptyState
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TodoScreen(vm: TodoViewModel) {
    val allTodos by vm.todos.collectAsStateWithLifecycle()
    val visibleTodos by vm.visibleTodos.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { TodoFilter.entries.size })

    // rememberSaveable keeps these across rotation / process death
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(TodoSort.Newest) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    // Calendar / daily view
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }

    // snapshotFlow turns Compose state into a Flow -> debounce typing before searching
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(250)
            .distinctUntilChanged()
            .collect { vm.setQuery(it) }
    }

    val doneCount = allTodos.count { it.done }
    val clearableCount = allTodos.count { it.isClearable }
    val progress by animateFloatAsState(
        targetValue = if (allTodos.isEmpty()) 0f else doneCount / allTodos.size.toFloat(),
        label = "todoProgress",
    )

    val deleteWithUndo: (Todo) -> Unit = { todo ->
        vm.delete(todo)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "\"${todo.title}\" deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.restore(todo)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        AnimatedContent(targetState = searchActive, label = "titleOrSearch") { active ->
                            if (active) SearchField(query = query, onQueryChange = { query = it })
                            else Text(if (showCalendar) "Daily Tasks" else "Todo List")
                        }
                    },
                    navigationIcon = { DrawerMenuButton() },
                    actions = {
                        IconButton(onClick = { showCalendar = !showCalendar }) {
                            Icon(
                                if (showCalendar) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.CalendarMonth,
                                contentDescription = if (showCalendar) "Show list" else "Show calendar",
                            )
                        }
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) query = ""
                        }) {
                            Icon(
                                if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searchActive) "Close search" else "Search",
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort and actions")
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                TodoSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort.label) },
                                        onClick = { sortOrder = sort; sortMenuOpen = false },
                                        trailingIcon = { if (sort == sortOrder) Icon(Icons.Filled.Check, null) },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Clear completed") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                                    enabled = clearableCount > 0,
                                    onClick = { sortMenuOpen = false; showClearDialog = true },
                                )
                            }
                        }
                    },
                )
                if (!showCalendar) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        TodoFilter.entries.forEachIndexed { index, filter ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text("${filter.label} (${allTodos.count(filter.predicate)})") },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // AnimatedVectorDrawable: "+" rotates into "×" while the sheet is open
            val avd = AnimatedImageVector.animatedVectorResource(R.drawable.avd_plus_to_close)
            FloatingActionButton(
                onClick = { showAddSheet = !showAddSheet },
                modifier = Modifier.testTag("add_todo_fab"),
            ) {
                Icon(rememberAnimatedVectorPainter(avd, atEnd = showAddSheet), contentDescription = "Add task")
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, actionColor = MaterialTheme.colorScheme.inversePrimary)
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (showCalendar) {
            DailyPlanner(
                todos = visibleTodos,
                selectedDate = selectedDate,
                onSelectDate = { selectedDate = it },
                onStatusChange = { todo, status -> vm.setStatus(todo.id, selectedDate, status) },
                onDelete = deleteWithUndo,
                modifier = Modifier.padding(padding),
            )
        } else {
            // HorizontalPager synced with the TabRow above: swipe between All / Active / Done
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) { page ->
                val filter = TodoFilter.entries[page]
                val pageItems = remember(visibleTodos, filter, sortOrder) {
                    visibleTodos.filter(filter.predicate).sortedWith(sortOrder.comparator)
                }
                TodoPage(
                    items = pageItems,
                    isRefreshing = isRefreshing,
                    onRefresh = vm::refresh,
                    onToggle = vm::toggle,
                    onDelete = deleteWithUndo,
                )
            }
        }
    }

    if (showAddSheet) {
        AddTodoSheet(
            initialDate = if (showCalendar) selectedDate else LocalDate.now(),
            onDismiss = { showAddSheet = false },
            onSave = { title, notes, priority, tags, reminder, date, repeatDaily ->
                vm.add(title, notes, priority, tags, reminder, date, repeatDaily)
                showAddSheet = false
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            title = { Text("Clear completed?") },
            text = { Text("This removes $clearableCount completed task(s). Daily tasks are kept. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.clearCompleted(); showClearDialog = false }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TodoPage(
    items: List<Todo>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Todo) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // derivedStateOf: only recomposes when the boolean flips, not on every scroll pixel
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing here",
                        message = "Tap + to add a task. Pull down to sync.",
                        icon = Icons.Filled.Checklist,
                    )
                }
            }
            items(items, key = { it.id }) { todo ->
                SwipeToDeleteContainer(
                    onDelete = { onDelete(todo) },
                    modifier = Modifier.animateItem(),
                ) {
                    TodoItemCard(todo = todo, onToggle = { onToggle(todo.id) })
                }
            }
        }
        AnimatedVisibility(
            visible = showScrollToTop,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }
    }
}

/** SwipeToDismissBox: swipe from right to left to delete. */
@Composable
internal fun SwipeToDeleteContainer(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (state.targetValue == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.errorContainer
                else Color.Transparent,
                label = "swipeBackground",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CardDefaults.shape)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        content()
    }
}

/**
 * Stateless (hoisted) item: receives data + callbacks, owns no business state.
 * Double-tap anywhere on the card also toggles it (detectTapGestures).
 */
@Composable
fun TodoItemCard(todo: Todo, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // updateTransition: one state (done) drives several coordinated animations
    val transition = updateTransition(targetState = todo.done, label = "todoDone")
    val doneColor = MaterialTheme.colorScheme.surfaceVariant
    val openColor = MaterialTheme.colorScheme.surfaceContainerLow
    val containerColor by transition.animateColor(label = "todoContainer") { done -> if (done) doneColor else openColor }
    val elevation by transition.animateDp(label = "todoElevation") { done -> if (done) 0.dp else 2.dp }
    val contentAlpha by transition.animateFloat(label = "todoAlpha") { done -> if (done) 0.6f else 1f }
    val currentOnToggle by rememberUpdatedState(onToggle)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { currentOnToggle() }) },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            Modifier
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = todo.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (todo.notes.isNotBlank()) {
                    Text(todo.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TodoDateRow(todo)
                if (todo.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        todo.tags.forEach { tag ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (todo.reminder) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = "Reminder on",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(todo.priority.color),
            )
        }
    }
}

/** Due date (or "Daily") plus an "In progress" badge when the task has been started. */
@Composable
private fun TodoDateRow(todo: Todo) {
    val today = LocalDate.now()
    val overdue = !todo.repeatDaily && !todo.done && todo.date.isBefore(today)
    val tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            if (todo.repeatDaily) Icons.Filled.Repeat else Icons.Filled.Event,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            when {
                todo.repeatDaily -> "Daily"
                overdue -> "Overdue · ${friendlyDate(todo.date, today)}"
                else -> friendlyDate(todo.date, today)
            },
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
        if (todo.statusOn(today) == TaskStatus.IN_PROGRESS) {
            Spacer(Modifier.width(4.dp))
            StatusBadge(TaskStatus.IN_PROGRESS)
        }
    }
}

/** BasicTextField: the low-level text input, styled by hand for the app bar. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search tasks or #tags…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                innerTextField()
            }
        },
    )
}

/** ModalBottomSheet with OutlinedTextField, RadioButtons, FilterChips in a FlowRow and a Switch. */
@Composable
fun AddTodoSheet(
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        notes: String,
        priority: Priority,
        tags: Set<String>,
        reminder: Boolean,
        date: LocalDate,
        repeatDaily: Boolean,
    ) -> Unit,
    initialDate: LocalDate = LocalDate.now(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(Priority.MEDIUM) }
    var tags by remember { mutableStateOf(emptySet<String>()) }
    var reminder by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var repeatDaily by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) action() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Inside the sheet's own composition, so the title field is attached before focusing it
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New task", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("todo_title_input"),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Priority", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.selectableGroup()) {
                Priority.entries.forEach { p ->
                    Row(
                        Modifier
                            .selectable(selected = priority == p, onClick = { priority = p }, role = Role.RadioButton)
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = priority == p, onClick = null)
                        Spacer(Modifier.width(4.dp))
                        Text(p.label)
                    }
                }
            }

            Text("Tags", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodoTags.forEach { tag ->
                    FilterChip(
                        selected = tag in tags,
                        onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                        label = { Text(tag) },
                    )
                }
            }

            Text(if (repeatDaily) "Starts on" else "Date", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(friendlyDate(date))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Repeat daily")
                    Text(
                        "Show it every day with its own status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = repeatDaily, onCheckedChange = { repeatDaily = it })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reminder")
                    Text(
                        "Show a bell on this task",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = reminder, onCheckedChange = { reminder = it })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = { closeThen(onDismiss) }) { Text("Cancel") }
                Button(
                    onClick = { closeThen { onSave(title, notes, priority, tags, reminder, date, repeatDaily) } },
                    enabled = title.isNotBlank(),
                ) { Text("Add task") }
            }
        }
    }

    if (showDatePicker) {
        // DatePicker works in UTC milliseconds
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TodoItemCardPreview() {
    ComposeToolboxTheme(dynamicColor = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TodoItemCard(Todo(1, "Buy groceries", "Milk, eggs, bread", Priority.HIGH, setOf("Shopping", "Home"), reminder = true), onToggle = {})
            TodoItemCard(Todo(2, "Morning run", priority = Priority.LOW, status = TaskStatus.DONE), onToggle = {})
        }
    }
}
