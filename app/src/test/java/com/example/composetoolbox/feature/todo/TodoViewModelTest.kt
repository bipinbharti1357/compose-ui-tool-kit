package com.example.composetoolbox.feature.todo

import com.example.composetoolbox.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.now()

    private fun TodoViewModel.find(id: Long) = todos.value.firstOrNull { it.id == id }

    @Test
    fun defaultConstructor_startsEmpty() {
        val vm = TodoViewModel()
        assertTrue(vm.todos.value.isEmpty())
        vm.add("First", "", Priority.LOW, emptySet(), false)
        assertEquals(listOf("First"), vm.todos.value.map { it.title })
    }

    @Test
    fun startsWithSampleTasks() {
        val vm = TodoViewModel(sampleTodos())
        assertEquals(7, vm.todos.value.size)
        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun add_prependsTrimmedTaskWithUniqueId() {
        val vm = TodoViewModel(sampleTodos())
        vm.add("  Buy milk  ", "  2 litres ", Priority.HIGH, setOf("Shopping"), reminder = true)
        vm.add("Second", "", Priority.LOW, emptySet(), reminder = false)

        val (second, first) = vm.todos.value.take(2)
        assertEquals("Second", second.title)
        assertEquals("Buy milk", first.title)
        assertEquals("2 litres", first.notes)
        assertEquals(Priority.HIGH, first.priority)
        assertEquals(setOf("Shopping"), first.tags)
        assertTrue(first.reminder)
        assertEquals(today, first.date)
        assertEquals(TaskStatus.TODO, first.status)
        assertTrue(first.id != second.id)
        assertEquals(9, vm.todos.value.size)
    }

    @Test
    fun add_dailyTask_keepsDateAndRepeatFlag() {
        val vm = TodoViewModel(sampleTodos())
        val start = today.plusDays(2)
        vm.add("Stretch", "", Priority.LOW, emptySet(), false, date = start, repeatDaily = true)
        val todo = vm.todos.value.first()
        assertTrue(todo.repeatDaily)
        assertEquals(start, todo.date)
    }

    @Test
    fun toggle_oneOffTask_flipsBetweenTodoAndDone() {
        val vm = TodoViewModel(sampleTodos())
        // id 2 "Buy vegetables and milk" starts as To do
        vm.toggle(2)
        assertEquals(TaskStatus.DONE, vm.find(2)!!.status)
        vm.toggle(2)
        assertEquals(TaskStatus.TODO, vm.find(2)!!.status)
    }

    @Test
    fun toggle_dailyTask_onlyChangesTodaysStatus() {
        val vm = TodoViewModel(sampleTodos())
        // id 7 "Drink 8 glasses of water": yesterday DONE, today IN_PROGRESS
        vm.toggle(7)
        val todo = vm.find(7)!!
        assertEquals(TaskStatus.DONE, todo.statusOn(today))
        assertEquals(TaskStatus.DONE, todo.statusOn(today.minusDays(1)))
        assertEquals(TaskStatus.TODO, todo.status)
    }

    @Test
    fun toggle_unknownId_isIgnored() {
        val vm = TodoViewModel(sampleTodos())
        val before = vm.todos.value
        vm.toggle(9_999)
        assertEquals(before, vm.todos.value)
    }

    @Test
    fun setStatus_dailyTask_storesStatusForThatDayOnly() {
        val vm = TodoViewModel(sampleTodos())
        val day = today.plusDays(4)
        vm.setStatus(3, day, TaskStatus.IN_PROGRESS)
        val todo = vm.find(3)!!
        assertEquals(TaskStatus.IN_PROGRESS, todo.statusOn(day))
        assertEquals(TaskStatus.TODO, todo.statusOn(day.plusDays(1)))
    }

    @Test
    fun setStatus_oneOffTask_updatesItsStatus() {
        val vm = TodoViewModel(sampleTodos())
        vm.setStatus(4, today.plusDays(1), TaskStatus.IN_PROGRESS)
        assertEquals(TaskStatus.IN_PROGRESS, vm.find(4)!!.status)
    }

    @Test
    fun delete_thenRestore_bringsTaskBack() {
        val vm = TodoViewModel(sampleTodos())
        val todo = vm.find(5)!!
        vm.delete(todo)
        assertNull(vm.find(5))
        vm.restore(todo)
        assertEquals(todo, vm.find(5))
    }

    @Test
    fun restore_doesNotDuplicateExistingTask() {
        val vm = TodoViewModel(sampleTodos())
        vm.restore(vm.find(1)!!)
        assertEquals(1, vm.todos.value.count { it.id == 1L })
    }

    @Test
    fun clearCompleted_removesOnlyFinishedOneOffTasks() {
        val vm = TodoViewModel(sampleTodos())
        vm.clearCompleted()
        // id 6 is a finished one-off task; id 3 is a daily task done today and must stay
        assertNull(vm.find(6))
        assertTrue(vm.find(3) != null)
        assertEquals(6, vm.todos.value.size)
        assertTrue(vm.todos.value.none { it.isClearable })
    }

    @Test
    fun visibleTodos_filtersByTitleOrTag_caseInsensitive() = runTest {
        val vm = TodoViewModel(sampleTodos())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.visibleTodos.collect {} }

        vm.setQuery("MILK")
        assertEquals(listOf("Buy vegetables and milk"), vm.visibleTodos.value.map { it.title })

        vm.setQuery("health")
        assertEquals(setOf(3L, 7L), vm.visibleTodos.value.map { it.id }.toSet())

        vm.setQuery("   ")
        assertEquals(vm.todos.value, vm.visibleTodos.value)
    }

    @Test
    fun visibleTodos_reflectsNewlyAddedTasks() = runTest {
        val vm = TodoViewModel(sampleTodos())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.visibleTodos.collect {} }
        vm.setQuery("yoga")
        assertTrue(vm.visibleTodos.value.isEmpty())

        vm.add("Morning yoga", "", Priority.LOW, emptySet(), false)
        assertEquals(listOf("Morning yoga"), vm.visibleTodos.value.map { it.title })
    }

    @Test
    fun refresh_setsRefreshingForAWhile() = runTest {
        val vm = TodoViewModel(sampleTodos())
        vm.refresh()
        assertTrue(vm.isRefreshing.value)
        advanceTimeBy(1_100)
        assertTrue(vm.isRefreshing.value)
        advanceUntilIdle()
        assertFalse(vm.isRefreshing.value)
    }
}
