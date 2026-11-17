package com.example.composetoolbox.feature.todo

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Priority(val label: String, val color: Color) {
    LOW("Low", Color(0xFF22C55E)),
    MEDIUM("Medium", Color(0xFFF59E0B)),
    HIGH("High", Color(0xFFEF4444)),
}

enum class TaskStatus(val label: String, val color: Color) {
    TODO("To do", Color(0xFF94A3B8)),
    IN_PROGRESS("In progress", Color(0xFF3B82F6)),
    DONE("Done", Color(0xFF22C55E)),
}

data class Todo(
    val id: Long,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val tags: Set<String> = emptySet(),
    val reminder: Boolean = false,
    /** Due date; for daily tasks, the first day it repeats. */
    val date: LocalDate = LocalDate.now(),
    /** Daily tasks appear on every day from [date] on, with their own status per day. */
    val repeatDaily: Boolean = false,
    /** Status of a one-off task. */
    val status: TaskStatus = TaskStatus.TODO,
    /** Per-day status of a daily task (days without an entry are "To do"). */
    val dailyStatus: Map<LocalDate, TaskStatus> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun occursOn(day: LocalDate): Boolean = if (repeatDaily) !day.isBefore(date) else day == date

    fun statusOn(day: LocalDate): TaskStatus =
        if (repeatDaily) dailyStatus[day] ?: TaskStatus.TODO else status

    /** One-off: its status is Done. Daily task: today's status is Done. */
    val done: Boolean get() = statusOn(LocalDate.now()) == TaskStatus.DONE
}

enum class TodoFilter(val label: String, val predicate: (Todo) -> Boolean) {
    All("All", { true }),
    Active("Active", { !it.done }),
    Done("Done", { it.done }),
}

enum class TodoSort(val label: String, val comparator: Comparator<Todo>) {
    Newest("Newest first", compareByDescending<Todo> { it.createdAt }),
    ByPriority("Priority", compareByDescending<Todo> { it.priority.ordinal }.thenByDescending { it.createdAt }),
    Alphabetical("A → Z", compareBy<Todo> { it.title.lowercase() }),
}

/** Finished one-off tasks; "Clear completed" removes these. */
val Todo.isClearable: Boolean get() = !repeatDaily && status == TaskStatus.DONE

val TodoTags = listOf("Work", "Home", "Shopping", "Health", "Study", "Urgent")

/** Where todos are persisted (the app uses a Room table; tests pass none and stay in memory). */
interface TodoStore {
    suspend fun load(): List<Todo>
    suspend fun save(todos: List<Todo>)
}

/**
 * Starts with no tasks; [initial] lets tests seed the list (see [sampleTodos]).
 * With a [store], saved todos are loaded on creation and every change is written back.
 */
class TodoViewModel(
    initial: List<Todo> = emptyList(),
    private val store: TodoStore? = null,
) : ViewModel() {
    private var lastId = initial.maxOfOrNull { it.id } ?: 0L

    /** Time-based so ids never clash with rows already in the database. */
    private fun newId(): Long = maxOf(System.currentTimeMillis(), lastId + 1).also { lastId = it }

    private val _todos = MutableStateFlow(initial)
    val todos: StateFlow<List<Todo>> = _todos.asStateFlow()

    init {
        if (store != null) viewModelScope.launch {
            val saved = store.load()
            // Keep anything added while loading; saved rows fill in the rest.
            _todos.update { current -> current + saved.filter { s -> current.none { it.id == s.id } } }
            // StateFlow is conflated, so rapid edits collapse into one write.
            _todos.collect { store.save(it) }
        }
    }

    private val query = MutableStateFlow("")

    /** Search is applied here (in a real app this could be a database query). */
    val visibleTodos: StateFlow<List<Todo>> = combine(_todos, query) { list, q ->
        if (q.isBlank()) list
        else list.filter { todo ->
            todo.title.contains(q, ignoreCase = true) || todo.tags.any { it.contains(q, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _todos.value)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun setQuery(value: String) {
        query.value = value
    }

    fun add(
        title: String,
        notes: String,
        priority: Priority,
        tags: Set<String>,
        reminder: Boolean,
        date: LocalDate = LocalDate.now(),
        repeatDaily: Boolean = false,
    ) {
        val todo = Todo(newId(), title.trim(), notes.trim(), priority, tags, reminder, date, repeatDaily)
        _todos.update { listOf(todo) + it }
    }

    /** Checkbox in the list: flips today's status between Done and To do. */
    fun toggle(id: Long) {
        val todo = _todos.value.firstOrNull { it.id == id } ?: return
        setStatus(id, LocalDate.now(), if (todo.done) TaskStatus.TODO else TaskStatus.DONE)
    }

    /** Sets the status of a task on [day] (only daily tasks track status per day). */
    fun setStatus(id: Long, day: LocalDate, status: TaskStatus) {
        _todos.update { list ->
            list.map { todo ->
                when {
                    todo.id != id -> todo
                    todo.repeatDaily -> todo.copy(dailyStatus = todo.dailyStatus + (day to status))
                    else -> todo.copy(status = status)
                }
            }
        }
    }

    fun delete(todo: Todo) {
        _todos.update { list -> list.filterNot { it.id == todo.id } }
    }

    fun restore(todo: Todo) {
        _todos.update { list -> if (list.any { it.id == todo.id }) list else list + todo }
    }

    /** Removes finished one-off tasks; daily tasks are kept because they come back tomorrow. */
    fun clearCompleted() {
        _todos.update { list -> list.filterNot { it.isClearable } }
    }

    /** Simulated "sync" for pull-to-refresh. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(1_200)
            _isRefreshing.value = false
        }
    }

}

/** Demo data for tests only — the app itself starts empty. */
internal fun sampleTodos(): List<Todo> {
    val now = System.currentTimeMillis()
    val hour = 3_600_000L
    val today = LocalDate.now()
    return listOf(
        Todo(1, "Prepare sprint demo", "Slides + live build", Priority.HIGH, setOf("Work", "Urgent"), reminder = true, date = today, status = TaskStatus.IN_PROGRESS, createdAt = now - hour),
        Todo(2, "Buy vegetables and milk", "", Priority.MEDIUM, setOf("Shopping", "Home"), date = today, createdAt = now - 2 * hour),
        Todo(
            3, "30 min evening walk", "", Priority.LOW, setOf("Health"),
            date = today.minusDays(3), repeatDaily = true,
            dailyStatus = mapOf(today.minusDays(3) to TaskStatus.DONE, today.minusDays(2) to TaskStatus.DONE, today to TaskStatus.DONE),
            createdAt = now - 3 * hour,
        ),
        Todo(4, "Finish Compose state hoisting chapter", "Then try LazyColumn", Priority.MEDIUM, setOf("Study"), date = today.plusDays(1), createdAt = now - 4 * hour),
        Todo(5, "Pay electricity bill", "", Priority.HIGH, setOf("Home"), reminder = true, date = today.plusDays(3), createdAt = now - 5 * hour),
        Todo(6, "Reply to client email", "", Priority.MEDIUM, setOf("Work"), date = today.minusDays(1), status = TaskStatus.DONE, createdAt = now - 6 * hour),
        Todo(
            7, "Drink 8 glasses of water", "", Priority.LOW, setOf("Health"),
            date = today.minusDays(3), repeatDaily = true,
            dailyStatus = mapOf(today.minusDays(1) to TaskStatus.DONE, today to TaskStatus.IN_PROGRESS),
            createdAt = now - 7 * hour,
        ),
    )
}
