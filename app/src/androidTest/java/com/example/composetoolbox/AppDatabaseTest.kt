package com.example.composetoolbox

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.composetoolbox.data.AppDatabase
import com.example.composetoolbox.data.RoomChallengeStore
import com.example.composetoolbox.data.RoomExpenseStore
import com.example.composetoolbox.feature.expense.Expense
import com.example.composetoolbox.feature.expense.ExpenseCategory
import com.example.composetoolbox.data.RoomTodoStore
import com.example.composetoolbox.feature.challenge.ChallengeSnapshot
import com.example.composetoolbox.feature.challenge.ChallengeTask
import com.example.composetoolbox.feature.todo.Priority
import com.example.composetoolbox.feature.todo.TaskStatus
import com.example.composetoolbox.feature.todo.Todo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Round-trips both features through a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun todos_roundTrip_andReplace() = runTest {
        val store = RoomTodoStore(db.todoDao())
        val day = LocalDate.of(2026, 3, 1)
        val todos = listOf(
            Todo(1, "Pay bill", "by Friday", Priority.HIGH, setOf("Home", "Urgent"), reminder = true, date = day, createdAt = 5),
            Todo(2, "Walk", repeatDaily = true, date = day, dailyStatus = mapOf(day to TaskStatus.DONE), createdAt = 6),
        )
        store.save(todos)
        assertEquals(todos.toSet(), store.load().toSet())

        store.save(todos.take(1)) // deleting a todo also removes its daily statuses
        assertEquals(todos.take(1), store.load())
        assertEquals(0, db.todoDao().dailyStatuses().size)
    }

    @Test
    fun challenge_roundTrip() = runTest {
        val store = RoomChallengeStore(db.challengeDao())
        val snapshot = ChallengeSnapshot(
            tasks = listOf(ChallengeTask(10, "Run", "5 km"), ChallengeTask(3, "Read")),
            startDate = LocalDate.of(2026, 1, 1),
            completions = mapOf(1 to setOf(10L, 3L), 2 to setOf(3L)),
        )
        store.save(snapshot)
        assertEquals(snapshot, store.load())

        store.save(ChallengeSnapshot(snapshot.tasks, startDate = null, completions = emptyMap()))
        assertEquals(null, store.load().startDate)
    }

    @Test
    fun expenses_andBudget_roundTrip() = runTest {
        val store = RoomExpenseStore(db.expenseDao())
        val expenses = listOf(
            Expense(1, 450.0, ExpenseCategory.FOOD, "Lunch", LocalDate.of(2026, 3, 1)),
            Expense(2, 1_850.5, ExpenseCategory.BILLS, "Electricity", LocalDate.of(2026, 3, 2)),
        )
        store.saveExpenses(expenses)
        assertEquals(expenses.toSet(), store.loadExpenses().toSet())
        store.saveExpenses(expenses.drop(1))
        assertEquals(expenses.drop(1), store.loadExpenses())

        assertEquals(null, store.loadBudget())
        store.saveBudget(12_000f)
        store.saveBudget(15_000f)
        assertEquals(15_000f, store.loadBudget())
    }
}
