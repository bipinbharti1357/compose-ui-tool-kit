package com.example.composetoolbox.feature.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChallengeViewModelTest {
    private var today = LocalDate.of(2026, 1, 1)
    private val vm = ChallengeViewModel(clock = { today })

    private fun ids() = vm.state.value.tasks.map { it.id }
    private fun addTasks() { vm.addTask("Workout"); vm.addTask("Read", "10 pages"); vm.addTask("Water") }
    private fun completeToday() = ids().forEach(vm::toggle)
    private fun nextDay() { today = today.plusDays(1); vm.refreshDate() }

    @Test
    fun notStartedByDefault_withNoTasks() {
        assertFalse(vm.state.value.started)
        assertTrue(vm.state.value.tasks.isEmpty())
        assertEquals(0, vm.state.value.todayRemaining)
    }

    @Test
    fun cannotStartWithoutTasks() {
        vm.start()
        assertFalse(vm.state.value.started)
    }

    @Test
    fun addEditRemoveTasks_beforeStart() {
        addTasks()
        vm.addTask("   ") // blank ignored
        assertEquals(listOf("Workout", "Read", "Water"), vm.state.value.tasks.map { it.title })
        vm.updateTask(ids()[0], "Run", "5 km")
        assertEquals("Run", vm.state.value.tasks[0].title)
        assertEquals("5 km", vm.state.value.tasks[0].description)
        vm.removeTask(ids()[2])
        assertEquals(2, vm.state.value.tasks.size)
    }

    @Test
    fun tasksLockedAfterStart_andEndChallengeUnlocks() {
        addTasks(); vm.start()
        vm.addTask("Extra"); vm.removeTask(ids()[0])
        assertEquals(3, vm.state.value.tasks.size)
        vm.endChallenge()
        assertFalse(vm.state.value.started)
        vm.addTask("Extra")
        assertEquals(4, vm.state.value.tasks.size)
    }

    @Test
    fun start_makesTodayDayOneWithAllTasksRemaining() {
        addTasks(); vm.start()
        assertEquals(1, vm.state.value.currentDay)
        assertEquals(3, vm.state.value.todayRemaining)
    }

    @Test
    fun toggle_marksAndUnmarksTask() {
        addTasks(); vm.start()
        val water = ids()[2]
        vm.toggle(water)
        assertTrue(water in vm.state.value.tasksFor(1))
        vm.toggle(water)
        assertFalse(water in vm.state.value.tasksFor(1))
    }

    @Test
    fun completedDaysAdvance() {
        addTasks(); vm.start()
        completeToday(); nextDay(); completeToday(); nextDay()
        val s = vm.state.value
        assertEquals(3, s.currentDay)
        assertEquals(2, s.completedDays)
        assertFalse(s.failed)
        assertEquals(DayStatus.COMPLETE, s.statusOf(1))
        assertEquals(DayStatus.TODAY, s.statusOf(3))
    }

    @Test
    fun incompletePastDay_failsChallenge_andRestartResets() {
        addTasks(); vm.start()
        vm.toggle(ids()[0])
        nextDay()
        assertTrue(vm.state.value.failed)
        assertEquals(1, vm.state.value.firstMissedDay)

        vm.toggle(ids()[0]) // ignored while failed
        assertTrue(vm.state.value.tasksFor(2).isEmpty())

        vm.restart()
        assertFalse(vm.state.value.failed)
        assertEquals(1, vm.state.value.currentDay)
        assertEquals(today, vm.state.value.startDate)
        assertEquals(3, vm.state.value.tasks.size) // tasks kept on restart
    }

    @Test
    fun all75Days_finishesChallenge() {
        addTasks(); vm.start()
        repeat(CHALLENGE_DAYS) { completeToday(); nextDay() }
        assertTrue(vm.state.value.finished)
        assertEquals(CHALLENGE_DAYS, vm.state.value.completedDays)
    }
}
