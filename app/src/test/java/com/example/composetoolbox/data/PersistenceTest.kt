package com.example.composetoolbox.data

import com.example.composetoolbox.MainDispatcherRule
import com.example.composetoolbox.feature.challenge.ChallengeSnapshot
import com.example.composetoolbox.feature.challenge.ChallengeStore
import com.example.composetoolbox.feature.challenge.ChallengeTask
import com.example.composetoolbox.feature.challenge.ChallengeViewModel
import com.example.composetoolbox.feature.expense.Expense
import com.example.composetoolbox.feature.expense.ExpenseCategory
import com.example.composetoolbox.feature.expense.ExpenseStore
import com.example.composetoolbox.feature.expense.ExpenseViewModel
import com.example.composetoolbox.feature.todo.Priority
import com.example.composetoolbox.feature.todo.Todo
import com.example.composetoolbox.feature.todo.TodoStore
import com.example.composetoolbox.feature.todo.TodoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** ViewModels load from their store on creation and write every change back. */
class PersistenceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeTodoStore(var saved: List<Todo> = emptyList()) : TodoStore {
        override suspend fun load() = saved
        override suspend fun save(todos: List<Todo>) { saved = todos }
    }

    private class FakeChallengeStore(var saved: ChallengeSnapshot) : ChallengeStore {
        override suspend fun load() = saved
        override suspend fun save(snapshot: ChallengeSnapshot) { saved = snapshot }
    }

    @Test
    fun todo_loadsSavedTasks_andSavesChanges() {
        val store = FakeTodoStore(listOf(Todo(1, "Saved task")))
        val vm = TodoViewModel(store = store)
        assertEquals(listOf("Saved task"), vm.todos.value.map { it.title })

        vm.add("New task", "", Priority.HIGH, setOf("Work"), reminder = false)
        assertEquals(listOf("New task", "Saved task"), store.saved.map { it.title })
        assertTrue("new id must not clash", store.saved.map { it.id }.toSet().size == 2)

        vm.delete(store.saved.first { it.title == "Saved task" })
        assertEquals(listOf("New task"), store.saved.map { it.title })
    }

    @Test
    fun challenge_loadsSavedProgress_andSavesChanges() {
        val start = LocalDate.of(2026, 1, 1)
        val store = FakeChallengeStore(
            ChallengeSnapshot(listOf(ChallengeTask(1, "Run")), startDate = start, completions = mapOf(1 to setOf(1L))),
        )
        val vm = ChallengeViewModel(clock = { start.plusDays(1) }, store = store)
        val s = vm.state.value
        assertEquals(2, s.currentDay)
        assertEquals(1, s.completedDays)

        vm.toggle(1)
        assertEquals(setOf(1L), store.saved.completions[2])

        vm.endChallenge()
        assertEquals(null, store.saved.startDate)
        vm.addTask("Read")
        assertEquals(listOf("Run", "Read"), store.saved.tasks.map { it.title })
    }

    private class FakeExpenseStore(var saved: List<Expense>, var budget: Float?) : ExpenseStore {
        override suspend fun loadExpenses() = saved
        override suspend fun saveExpenses(expenses: List<Expense>) { saved = expenses }
        override suspend fun loadBudget() = budget
        override suspend fun saveBudget(budget: Float) { this.budget = budget }
    }

    @Test
    fun expense_loadsSavedData_andSavesChanges() {
        val day = LocalDate.of(2026, 2, 1)
        val store = FakeExpenseStore(listOf(Expense(1, 50.0, ExpenseCategory.FOOD, "Tea", day)), budget = 10_000f)
        val vm = ExpenseViewModel(store = store)
        assertEquals(listOf("Tea"), vm.expenses.value.map { it.note })
        assertEquals(10_000f, vm.budget.value, 0f)

        vm.add(200.0, ExpenseCategory.TRANSPORT, "Taxi", day)
        assertEquals(listOf("Taxi", "Tea"), store.saved.map { it.note })
        vm.delete(store.saved.first { it.note == "Tea" })
        assertEquals(listOf("Taxi"), store.saved.map { it.note })

        vm.setBudget(8_000f)
        assertEquals(8_000f, store.budget!!, 0f)
    }

    @Test
    fun expense_untouchedDefaultBudget_isNotSaved() {
        val store = FakeExpenseStore(emptyList(), budget = null)
        ExpenseViewModel(store = store)
        assertEquals(null, store.budget)
    }
}
