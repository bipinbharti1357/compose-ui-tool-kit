package com.example.composetoolbox.feature.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodoModelTest {

    private val today = LocalDate.now()

    @Test
    fun oneOffTask_occursOnlyOnItsDate() {
        val todo = Todo(1, "One-off", date = today)
        assertTrue(todo.occursOn(today))
        assertFalse(todo.occursOn(today.plusDays(1)))
        assertFalse(todo.occursOn(today.minusDays(1)))
    }

    @Test
    fun dailyTask_occursFromStartDateOnwards() {
        val todo = Todo(1, "Daily", date = today, repeatDaily = true)
        assertFalse(todo.occursOn(today.minusDays(1)))
        assertTrue(todo.occursOn(today))
        assertTrue(todo.occursOn(today.plusDays(30)))
    }

    @Test
    fun dailyTask_statusIsTrackedPerDay_andDefaultsToTodo() {
        val todo = Todo(
            1, "Daily", date = today.minusDays(5), repeatDaily = true,
            status = TaskStatus.DONE, // ignored for daily tasks
            dailyStatus = mapOf(today.minusDays(1) to TaskStatus.DONE),
        )
        assertEquals(TaskStatus.DONE, todo.statusOn(today.minusDays(1)))
        assertEquals(TaskStatus.TODO, todo.statusOn(today))
        assertFalse(todo.done)
    }

    @Test
    fun oneOffTask_statusIsTheSameOnEveryDay() {
        val todo = Todo(1, "One-off", status = TaskStatus.IN_PROGRESS)
        assertEquals(TaskStatus.IN_PROGRESS, todo.statusOn(today))
        assertEquals(TaskStatus.IN_PROGRESS, todo.statusOn(today.plusDays(10)))
    }

    @Test
    fun done_reflectsTodaysStatus() {
        assertTrue(Todo(1, "a", status = TaskStatus.DONE).done)
        assertFalse(Todo(1, "a", status = TaskStatus.IN_PROGRESS).done)
        assertTrue(Todo(1, "a", repeatDaily = true, dailyStatus = mapOf(today to TaskStatus.DONE)).done)
    }

    @Test
    fun isClearable_onlyForFinishedOneOffTasks() {
        assertTrue(Todo(1, "a", status = TaskStatus.DONE).isClearable)
        assertFalse(Todo(1, "a", status = TaskStatus.TODO).isClearable)
        assertFalse(Todo(1, "a", repeatDaily = true, dailyStatus = mapOf(today to TaskStatus.DONE)).isClearable)
    }

    @Test
    fun filters_splitTasksByDoneState() {
        val open = Todo(1, "open")
        val finished = Todo(2, "finished", status = TaskStatus.DONE)
        val list = listOf(open, finished)
        assertEquals(list, list.filter(TodoFilter.All.predicate))
        assertEquals(listOf(open), list.filter(TodoFilter.Active.predicate))
        assertEquals(listOf(finished), list.filter(TodoFilter.Done.predicate))
    }

    @Test
    fun sortNewest_ordersByCreatedAtDescending() {
        val a = Todo(1, "a", createdAt = 100)
        val b = Todo(2, "b", createdAt = 300)
        val c = Todo(3, "c", createdAt = 200)
        assertEquals(listOf(b, c, a), listOf(a, b, c).sortedWith(TodoSort.Newest.comparator))
    }

    @Test
    fun sortByPriority_highFirst_thenNewest() {
        val lowNew = Todo(1, "low", priority = Priority.LOW, createdAt = 500)
        val highOld = Todo(2, "high old", priority = Priority.HIGH, createdAt = 100)
        val highNew = Todo(3, "high new", priority = Priority.HIGH, createdAt = 200)
        val medium = Todo(4, "medium", priority = Priority.MEDIUM, createdAt = 300)
        assertEquals(
            listOf(highNew, highOld, medium, lowNew),
            listOf(lowNew, highOld, highNew, medium).sortedWith(TodoSort.ByPriority.comparator),
        )
    }

    @Test
    fun sortAlphabetical_ignoresCase() {
        val b = Todo(1, "banana")
        val a = Todo(2, "Apple")
        val c = Todo(3, "cherry")
        assertEquals(listOf(a, b, c), listOf(b, c, a).sortedWith(TodoSort.Alphabetical.comparator))
    }
}
