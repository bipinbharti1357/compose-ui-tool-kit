@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.composetoolbox.feature.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.composetoolbox.ui.components.DrawerMenuButton

@Composable
fun ChallengeScreen(vm: ChallengeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // null = closed; id 0 = new task; otherwise editing that task.
    var editing by remember { mutableStateOf<ChallengeTask?>(null) }

    // A new calendar day may have started while the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshDate() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("75 Hard Challenge") },
                navigationIcon = { DrawerMenuButton() },
                actions = {
                    if (state.started) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Restart from Day 1") },
                                    onClick = { menuOpen = false; confirmRestart = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("End & edit tasks") },
                                    onClick = { menuOpen = false; confirmEnd = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.started) {
            SetupContent(
                tasks = state.tasks,
                canStart = state.canStart,
                onAdd = { editing = ChallengeTask(0, "") },
                onEdit = { editing = it },
                onRemove = { vm.removeTask(it.id) },
                onStart = vm::start,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { ProgressCard(state) }
                when {
                    state.failed -> item {
                        StatusCard(
                            icon = Icons.Filled.Warning,
                            title = "Day ${state.firstMissedDay} was missed",
                            message = "75 Hard has no exceptions — restart from Day 1 to keep going.",
                            actionLabel = "Restart from Day 1",
                            onAction = vm::restart,
                            container = MaterialTheme.colorScheme.errorContainer,
                        )
                    }
                    state.finished -> item {
                        StatusCard(
                            icon = Icons.Filled.EmojiEvents,
                            title = "Challenge complete!",
                            message = "You finished all $CHALLENGE_DAYS days.",
                            actionLabel = "Start again",
                            onAction = vm::restart,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                    else -> {
                        item {
                            Text("Today · Day ${state.currentDay}", style = MaterialTheme.typography.titleMedium)
                        }
                        items(state.tasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                checked = state.isDone(state.currentDay, task),
                                onToggle = { vm.toggle(task.id) },
                            )
                        }
                    }
                }
                item { Text("All $CHALLENGE_DAYS days", style = MaterialTheme.typography.titleMedium) }
                item { DayGrid(state) }
            }
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Restart challenge?") },
            text = { Text("All progress will be cleared and today becomes Day 1.") },
            confirmButton = {
                TextButton(onClick = { vm.restart(); confirmRestart = false }) { Text("Restart") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel") } },
        )
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End challenge?") },
            text = { Text("All progress will be cleared. Your tasks are kept so you can edit them and start again.") },
            confirmButton = {
                TextButton(onClick = { vm.endChallenge(); confirmEnd = false }) { Text("End") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } },
        )
    }

    editing?.let { task ->
        TaskEditorDialog(
            initial = task,
            onDismiss = { editing = null },
            onSave = { title, description ->
                if (task.id == 0L) vm.addTask(title, description) else vm.updateTask(task.id, title, description)
                editing = null
            },
        )
    }
}

@Composable
private fun TaskEditorDialog(
    initial: ChallengeTask,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add daily task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task") },
                    placeholder = { Text("e.g. Workout 45 min") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_title_field"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("task_description_field"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, description) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetupContent(
    tasks: List<ChallengeTask>,
    canStart: Boolean,
    onAdd: () -> Unit,
    onEdit: (ChallengeTask) -> Unit,
    onRemove: (ChallengeTask) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text("75 days. No compromises.", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Add your own daily tasks. Once you start, complete every one of them, every day, for " +
                    "$CHALLENGE_DAYS days. Miss one and you start over at Day 1.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tasks.isEmpty()) {
            item {
                Text(
                    "No tasks yet — tap \"Add task\" to create your first one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(tasks, key = { it.id }) { task ->
            ListItem(
                headlineContent = { Text(task.title) },
                supportingContent = task.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                leadingContent = { Icon(Icons.Filled.TaskAlt, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onRemove(task) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${task.title}")
                    }
                },
                modifier = Modifier.clickable { onEdit(task) },
            )
        }
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add task")
            }
        }
        item {
            Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth()) { Text("Start Day 1 today") }
        }
    }
}

@Composable
private fun ProgressCard(state: ChallengeState) {
    val shownDay = state.currentDay.coerceIn(1, CHALLENGE_DAYS)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Day $shownDay of $CHALLENGE_DAYS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { state.completedDays / CHALLENGE_DAYS.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${state.completedDays} days completed · ${CHALLENGE_DAYS - state.completedDays} to go",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskRow(task: ChallengeTask, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(task.title) },
        supportingContent = task.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        leadingContent = { Icon(Icons.Filled.TaskAlt, contentDescription = null) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .testTag("task_${task.id}"),
    )
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    container: Color,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 75 small day cells, 10 per row, coloured by status. */
@Composable
private fun DayGrid(state: ChallengeState) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..CHALLENGE_DAYS).chunked(10).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { day ->
                    val status = state.statusOf(day)
                    val (bg, fg) = when (status) {
                        DayStatus.COMPLETE -> colors.primary to colors.onPrimary
                        DayStatus.PARTIAL -> colors.secondaryContainer to colors.onSecondaryContainer
                        DayStatus.MISSED -> colors.errorContainer to colors.onErrorContainer
                        DayStatus.TODAY -> colors.surface to colors.primary
                        DayStatus.UPCOMING -> colors.surfaceVariant to colors.onSurfaceVariant
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(bg, RoundedCornerShape(6.dp))
                            .then(
                                if (status == DayStatus.TODAY) Modifier.border(2.dp, colors.primary, RoundedCornerShape(6.dp))
                                else Modifier,
                            )
                            .semantics { contentDescription = "Day $day, ${status.name.lowercase()}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$day", style = MaterialTheme.typography.labelSmall, color = fg)
                    }
                }
                // Keep the last (short) row's cells the same size as the others.
                repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
