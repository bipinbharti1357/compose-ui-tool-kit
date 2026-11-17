package com.example.composetoolbox.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.composetoolbox.feature.challenge.ChallengeSnapshot
import com.example.composetoolbox.feature.challenge.ChallengeStore
import com.example.composetoolbox.feature.challenge.ChallengeTask
import java.time.LocalDate

/** The user's own daily tasks, in display order. */
@Entity(tableName = "challenge_tasks")
data class ChallengeTaskEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String,
    val position: Int,
)

/** "Task [taskId] was done on challenge day [day]". */
@Entity(
    tableName = "challenge_completions",
    primaryKeys = ["day", "taskId"],
    foreignKeys = [ForeignKey(ChallengeTaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class ChallengeCompletionEntity(val day: Int, val taskId: Long)

/** Single row (id = 0) holding the start date; null start = not started. */
@Entity(tableName = "challenge_progress")
data class ChallengeProgressEntity(
    @PrimaryKey val id: Int = 0,
    /** Epoch day of Day 1. */
    val startDate: Long?,
)

@Dao
abstract class ChallengeDao {
    @Query("SELECT * FROM challenge_tasks ORDER BY position")
    abstract suspend fun tasks(): List<ChallengeTaskEntity>

    @Query("SELECT * FROM challenge_completions")
    abstract suspend fun completions(): List<ChallengeCompletionEntity>

    @Query("SELECT * FROM challenge_progress WHERE id = 0")
    abstract suspend fun progress(): ChallengeProgressEntity?

    @Query("DELETE FROM challenge_tasks")
    protected abstract suspend fun deleteAllTasks()

    @Query("DELETE FROM challenge_progress")
    protected abstract suspend fun deleteProgress()

    @Insert
    protected abstract suspend fun insertTasks(tasks: List<ChallengeTaskEntity>)

    @Insert
    protected abstract suspend fun insertCompletions(rows: List<ChallengeCompletionEntity>)

    @Insert
    protected abstract suspend fun insertProgress(progress: ChallengeProgressEntity)

    /** Replaces all challenge data atomically (completions go with their task via CASCADE). */
    @Transaction
    open suspend fun replaceAll(
        tasks: List<ChallengeTaskEntity>,
        completions: List<ChallengeCompletionEntity>,
        progress: ChallengeProgressEntity,
    ) {
        deleteAllTasks()
        deleteProgress()
        insertTasks(tasks)
        insertCompletions(completions)
        insertProgress(progress)
    }
}

class RoomChallengeStore(private val dao: ChallengeDao) : ChallengeStore {
    override suspend fun load(): ChallengeSnapshot = ChallengeSnapshot(
        tasks = dao.tasks().map { ChallengeTask(it.id, it.title, it.description) },
        startDate = dao.progress()?.startDate?.let(LocalDate::ofEpochDay),
        completions = dao.completions().groupBy({ it.day }, { it.taskId }).mapValues { it.value.toSet() },
    )

    override suspend fun save(snapshot: ChallengeSnapshot) {
        val taskIds = snapshot.tasks.map { it.id }.toSet()
        dao.replaceAll(
            tasks = snapshot.tasks.mapIndexed { i, t -> ChallengeTaskEntity(t.id, t.title, t.description, i) },
            completions = snapshot.completions.flatMap { (day, ids) ->
                ids.filter { it in taskIds }.map { ChallengeCompletionEntity(day, it) }
            },
            progress = ChallengeProgressEntity(startDate = snapshot.startDate?.toEpochDay()),
        )
    }
}
