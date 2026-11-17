package com.example.composetoolbox.feature.challenge

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val CHALLENGE_DAYS = 75

/** A daily task the user defined for their challenge. Every one must be done, every day. */
data class ChallengeTask(val id: Long, val title: String, val description: String = "")

/** The persisted part of [ChallengeState] (everything except "today"). */
data class ChallengeSnapshot(
    val tasks: List<ChallengeTask>,
    val startDate: LocalDate?,
    val completions: Map<Int, Set<Long>>,
)

/** Where challenge data is persisted (the app uses Room tables; tests pass none and stay in memory). */
interface ChallengeStore {
    suspend fun load(): ChallengeSnapshot
    suspend fun save(snapshot: ChallengeSnapshot)
}

enum class DayStatus { COMPLETE, PARTIAL, MISSED, TODAY, UPCOMING }

data class ChallengeState(
    /** The user's own daily tasks. Empty until they add some; locked once the challenge starts. */
    val tasks: List<ChallengeTask> = emptyList(),
    /** Null until the user starts the challenge. */
    val startDate: LocalDate? = null,
    /** Day number (1..75) → ids of tasks ticked off on that day. */
    val completions: Map<Int, Set<Long>> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
) {
    val started: Boolean get() = startDate != null

    fun snapshot() = ChallengeSnapshot(tasks, startDate, completions)

    val canStart: Boolean get() = !started && tasks.isNotEmpty()

    /** 1-based day number for [today]; 0 when not started. */
    val currentDay: Int
        get() = startDate?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 } ?: 0

    fun tasksFor(day: Int): Set<Long> = completions[day].orEmpty()

    fun isDone(day: Int, task: ChallengeTask): Boolean = task.id in tasksFor(day)

    fun isDayComplete(day: Int): Boolean =
        tasks.isNotEmpty() && tasks.all { it.id in tasksFor(day) }

    /** First past day that wasn't fully completed — the rules say that resets you to Day 1. */
    val firstMissedDay: Int?
        get() = (1 until currentDay.coerceAtMost(CHALLENGE_DAYS + 1)).firstOrNull { !isDayComplete(it) }

    val failed: Boolean get() = started && firstMissedDay != null

    val finished: Boolean
        get() = started && !failed && (1..CHALLENGE_DAYS).all { isDayComplete(it) }

    val completedDays: Int get() = (1..CHALLENGE_DAYS).count { isDayComplete(it) }

    val todayRemaining: Int
        get() = if (!started || failed || currentDay !in 1..CHALLENGE_DAYS) 0
        else tasks.count { it.id !in tasksFor(currentDay) }

    fun statusOf(day: Int): DayStatus = when {
        day == currentDay -> DayStatus.TODAY
        day > currentDay -> DayStatus.UPCOMING
        isDayComplete(day) -> DayStatus.COMPLETE
        tasksFor(day).isNotEmpty() -> DayStatus.PARTIAL
        else -> DayStatus.MISSED
    }
}

/**
 * 75-day challenge tracker. Starts with no tasks: the user adds their own, then starts the challenge.
 * With a [store], saved data is loaded on creation and every change is written back.
 * [clock] is injectable so tests can move "today" forward.
 */
class ChallengeViewModel(
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val store: ChallengeStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ChallengeState(today = clock()))
    val state: StateFlow<ChallengeState> = _state.asStateFlow()

    private var lastId = 0L

    /** Time-based so ids never clash with rows already in the database. */
    private fun newId(): Long = maxOf(System.currentTimeMillis(), lastId + 1).also { lastId = it }

    init {
        if (store != null) viewModelScope.launch {
            val saved = store.load()
            // Only apply saved data if the user hasn't already changed anything while it loaded.
            _state.update { s ->
                if (s.tasks.isEmpty() && !s.started) {
                    s.copy(tasks = saved.tasks, startDate = saved.startDate, completions = saved.completions)
                } else s
            }
            // Day roll-overs don't change the snapshot, so they don't trigger a write.
            _state.map { it.snapshot() }.distinctUntilChanged().collect { store.save(it) }
        }
    }

    /** Tasks can only be added/edited/removed before the challenge starts. */
    fun addTask(title: String, description: String = "") = _state.update { s ->
        val t = title.trim()
        if (s.started || t.isEmpty()) s
        else s.copy(tasks = s.tasks + ChallengeTask(newId(), t, description.trim()))
    }

    fun updateTask(id: Long, title: String, description: String = "") = _state.update { s ->
        val t = title.trim()
        if (s.started || t.isEmpty()) s
        else s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(title = t, description = description.trim()) else it })
    }

    fun removeTask(id: Long) = _state.update { s ->
        if (s.started) s else s.copy(tasks = s.tasks.filterNot { it.id == id })
    }

    fun start() = _state.update { s ->
        if (s.tasks.isEmpty()) s
        else s.copy(startDate = clock(), completions = emptyMap(), today = clock())
    }

    /** Start over from Day 1 today with the same tasks — what the rules require after a missed day. */
    fun restart() = start()

    /** Abandon the current run and go back to setup so the task list can be changed. */
    fun endChallenge() = _state.update { it.copy(startDate = null, completions = emptyMap(), today = clock()) }

    /** Tasks can only be ticked for the current day. */
    fun toggle(taskId: Long) = _state.update { s ->
        val day = s.currentDay
        if (!s.started || s.failed || day !in 1..CHALLENGE_DAYS || s.tasks.none { it.id == taskId }) return@update s
        val done = s.tasksFor(day)
        val updated = if (taskId in done) done - taskId else done + taskId
        s.copy(completions = s.completions + (day to updated))
    }

    /** Re-reads the clock (call when the screen resumes, so a new day rolls over). */
    fun refreshDate() = _state.update { it.copy(today = clock()) }
}
