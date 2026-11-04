@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.composetoolbox.feature.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.composetoolbox.ui.components.EmptyState
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

val TaskStatus.icon: ImageVector
    get() = when (this) {
        TaskStatus.TODO -> Icons.Filled.RadioButtonUnchecked
        TaskStatus.IN_PROGRESS -> Icons.Filled.Autorenew
        TaskStatus.DONE -> Icons.Filled.CheckCircle
    }

/** "Today", "Tomorrow", "Yesterday" or e.g. "Fri, Nov 6". */
fun friendlyDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(
        DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy", Locale.getDefault()),
    )
}

/**
 * Calendar + daily task list: pick a day on the month grid, then see that
 * day's tasks and set each one to To do / In progress / Done.
 */
@Composable
fun DailyPlanner(
    todos: List<Todo>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onStatusChange: (Todo, TaskStatus) -> Unit,
    onDelete: (Todo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayTasks = remember(todos, selectedDate) {
        todos.filter { it.occursOn(selectedDate) }
            .sortedWith(
                compareBy<Todo> { it.statusOn(selectedDate) == TaskStatus.DONE }
                    .thenByDescending { it.priority.ordinal }
                    .thenBy { it.title.lowercase() },
            )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "calendar") {
            MonthCalendar(todos = todos, selectedDate = selectedDate, onSelectDate = onSelectDate)
        }
        item(key = "summary") {
            DaySummary(date = selectedDate, tasks = dayTasks)
        }
        if (dayTasks.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "No tasks on this day",
                    message = "Tap + to plan something for ${friendlyDate(selectedDate)}.",
                    icon = Icons.Filled.EventAvailable,
                )
            }
        }
        items(dayTasks, key = { it.id }) { todo ->
            SwipeToDeleteContainer(onDelete = { onDelete(todo) }, modifier = Modifier.animateItem()) {
                DailyTaskCard(
                    todo = todo,
                    status = todo.statusOn(selectedDate),
                    onStatusChange = { onStatusChange(todo, it) },
                )
            }
        }
    }
}

/** Month grid. Dots under a day: green = all done, primary = tasks still open. */
@Composable
fun MonthCalendar(
    todos: List<Todo>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate)) }
    // Follow the selection when it moves to another month (e.g. "Today" button).
    LaunchedEffect(selectedDate) { month = YearMonth.from(selectedDate) }

    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weekDays = remember(firstDayOfWeek) { (0L until 7L).map { firstDayOfWeek.plus(it) } }
    val leadingBlanks = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    // date -> (open, done) task counts for this month. Future days skip daily
    // tasks, otherwise every upcoming day would carry the same dot.
    val counts = remember(todos, month, today) {
        (1..month.lengthOfMonth()).associate { d ->
            val day = month.atDay(d)
            val onDay = todos.filter { it.occursOn(day) && (!it.repeatDaily || !day.isAfter(today)) }
            day to (onDay.count { it.statusOn(day) != TaskStatus.DONE } to onDay.count { it.statusOn(day) == TaskStatus.DONE })
        }
    }

    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            if (month != YearMonth.from(today) || selectedDate != today) {
                TextButton(
                    onClick = { month = YearMonth.from(today); onSelectDate(today) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Jump to today")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                weekDays.forEach { dow: DayOfWeek ->
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val (open, done) = counts[day] ?: (0 to 0)
                                DayCell(
                                    day = day,
                                    selected = day == selectedDate,
                                    isToday = day == today,
                                    openCount = open,
                                    doneCount = done,
                                    onClick = { onSelectDate(day) },
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    openCount: Int,
    doneCount: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary else colors.surface.copy(alpha = 0f), label = "dayBg")
    val dotColor = when {
        openCount > 0 -> if (selected) colors.onPrimary else colors.primary
        doneCount > 0 -> TaskStatus.DONE.color
        else -> null
    }
    Column(
        Modifier
            .padding(2.dp)
            .fillMaxSize()
            .clip(CircleShape)
            .background(background)
            .then(if (isToday && !selected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "Show tasks for $day", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${day.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor ?: colors.surface.copy(alpha = 0f)),
        )
    }
}

/** Header for the selected day: progress bar + count per status. */
@Composable
private fun DaySummary(date: LocalDate, tasks: List<Todo>) {
    val byStatus = TaskStatus.entries.associateWith { s -> tasks.count { it.statusOn(date) == s } }
    val done = byStatus.getValue(TaskStatus.DONE)
    val progress by animateFloatAsState(
        if (tasks.isEmpty()) 0f else done / tasks.size.toFloat(),
        label = "dayProgress",
    )
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(friendlyDate(date), style = MaterialTheme.typography.titleLarge)
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tasks.isNotEmpty()) {
                Text("$done/${tasks.size} done", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (tasks.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { progress },
                color = TaskStatus.DONE.color,
                modifier = Modifier.fillMaxWidth().clip(CircleShape),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskStatus.entries.forEach { s ->
                    StatusBadge(status = s, text = "${s.label}: ${byStatus.getValue(s)}")
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: TaskStatus, text: String = status.label, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = status.color.copy(alpha = 0.15f),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** A task in the daily list, with a status chip that opens a menu (To do / In progress / Done). */
@Composable
fun DailyTaskCard(
    todo: Todo,
    status: TaskStatus,
    onStatusChange: (TaskStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isDone = status == TaskStatus.DONE

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(todo.priority.color),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (todo.notes.isNotBlank()) {
                    Text(todo.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (todo.repeatDaily) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text("Daily", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            Box {
                AssistChip(
                    onClick = { menuOpen = true },
                    label = { Text(status.label) },
                    leadingIcon = { Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    border = BorderStroke(1.dp, status.color.copy(alpha = 0.6f)),
                    modifier = Modifier.padding(start = 8.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    TaskStatus.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            leadingIcon = { Icon(s.icon, contentDescription = null, tint = s.color) },
                            trailingIcon = { if (s == status) Icon(Icons.Filled.Check, contentDescription = null) },
                            onClick = { menuOpen = false; onStatusChange(s) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DailyPlannerPreview() {
    val today = LocalDate.now()
    ComposeToolboxTheme(dynamicColor = false) {
        DailyPlanner(
            todos = listOf(
                Todo(1, "Prepare sprint demo", "Slides + live build", Priority.HIGH, date = today, status = TaskStatus.IN_PROGRESS),
                Todo(2, "Morning run", priority = Priority.LOW, date = today.minusDays(2), repeatDaily = true, dailyStatus = mapOf(today to TaskStatus.DONE)),
                Todo(3, "Pay bill", priority = Priority.MEDIUM, date = today),
            ),
            selectedDate = today,
            onSelectDate = {},
            onStatusChange = { _, _ -> },
            onDelete = {},
        )
    }
}
