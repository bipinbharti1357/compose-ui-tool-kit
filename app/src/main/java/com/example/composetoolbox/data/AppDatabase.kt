package com.example.composetoolbox.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local SQLite database. Each feature has its own tables:
 *  - Todo:     `todos`, `todo_daily_status`
 *  - 75 Hard:  `challenge_tasks`, `challenge_completions`, `challenge_progress`
 *  - Expense:  `expenses`, `expense_budget`
 *
 * Version history: 1 = Todo + 75 Hard; 2 = adds the Expense tables (auto-migrated, no data lost).
 */
@Database(
    entities = [
        TodoEntity::class,
        TodoDailyStatusEntity::class,
        ChallengeTaskEntity::class,
        ChallengeCompletionEntity::class,
        ChallengeProgressEntity::class,
        ExpenseEntity::class,
        ExpenseBudgetEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "compose_toolbox.db")
                .build()
                .also { instance = it }
        }
    }
}
