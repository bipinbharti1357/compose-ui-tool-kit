package com.example.composetoolbox.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.composetoolbox.feature.todo.Priority
import com.example.composetoolbox.feature.todo.TaskStatus
import com.example.composetoolbox.feature.todo.Todo
import com.example.composetoolbox.feature.todo.TodoStore
import java.time.LocalDate

/** One row per task. Dates are stored as epoch days, enums by name. */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val notes: String,
    val priority: String,
    /** Tags joined with [TAG_SEPARATOR]. */
    val tags: String,
    val reminder: Boolean,
    val date: Long,
    val repeatDaily: Boolean,
    val status: String,
    val createdAt: Long,
)

/** Per-day status of a daily (repeating) task. */
@Entity(
    tableName = "todo_daily_status",
    primaryKeys = ["todoId", "date"],
    foreignKeys = [ForeignKey(TodoEntity::class, ["id"], ["todoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("todoId")],
)
data class TodoDailyStatusEntity(val todoId: Long, val date: Long, val status: String)

@Dao
abstract class TodoDao {
    @Query("SELECT * FROM todos")
    abstract suspend fun todos(): List<TodoEntity>

    @Query("SELECT * FROM todo_daily_status")
    abstract suspend fun dailyStatuses(): List<TodoDailyStatusEntity>

    @Query("DELETE FROM todos")
    protected abstract suspend fun deleteAllTodos()

    @Insert
    protected abstract suspend fun insertTodos(todos: List<TodoEntity>)

    @Insert
    protected abstract suspend fun insertDailyStatuses(rows: List<TodoDailyStatusEntity>)

    /** Replaces the whole list atomically (daily statuses go with their todo via CASCADE). */
    @Transaction
    open suspend fun replaceAll(todos: List<TodoEntity>, dailyStatuses: List<TodoDailyStatusEntity>) {
        deleteAllTodos()
        insertTodos(todos)
        insertDailyStatuses(dailyStatuses)
    }
}

private const val TAG_SEPARATOR = "\u001F"

class RoomTodoStore(private val dao: TodoDao) : TodoStore {
    override suspend fun load(): List<Todo> {
        val statuses = dao.dailyStatuses().groupBy { it.todoId }
        return dao.todos().map { e ->
            Todo(
                id = e.id,
                title = e.title,
                notes = e.notes,
                priority = enumValueOrDefault(e.priority, Priority.MEDIUM),
                tags = e.tags.split(TAG_SEPARATOR).filter { it.isNotEmpty() }.toSet(),
                reminder = e.reminder,
                date = LocalDate.ofEpochDay(e.date),
                repeatDaily = e.repeatDaily,
                status = enumValueOrDefault(e.status, TaskStatus.TODO),
                dailyStatus = statuses[e.id].orEmpty().associate {
                    LocalDate.ofEpochDay(it.date) to enumValueOrDefault(it.status, TaskStatus.TODO)
                },
                createdAt = e.createdAt,
            )
        }
    }

    override suspend fun save(todos: List<Todo>) {
        dao.replaceAll(
            todos = todos.map {
                TodoEntity(
                    id = it.id,
                    title = it.title,
                    notes = it.notes,
                    priority = it.priority.name,
                    tags = it.tags.joinToString(TAG_SEPARATOR),
                    reminder = it.reminder,
                    date = it.date.toEpochDay(),
                    repeatDaily = it.repeatDaily,
                    status = it.status.name,
                    createdAt = it.createdAt,
                )
            },
            dailyStatuses = todos.flatMap { todo ->
                todo.dailyStatus.map { (day, status) -> TodoDailyStatusEntity(todo.id, day.toEpochDay(), status.name) }
            },
        )
    }
}

internal inline fun <reified E : Enum<E>> enumValueOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
