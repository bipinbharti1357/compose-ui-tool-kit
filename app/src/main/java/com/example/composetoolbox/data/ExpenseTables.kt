package com.example.composetoolbox.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.composetoolbox.feature.expense.Expense
import com.example.composetoolbox.feature.expense.ExpenseCategory
import com.example.composetoolbox.feature.expense.ExpenseStore
import java.time.LocalDate

/** One row per expense. Date is stored as epoch day, category by name. */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: Long,
    val amount: Double,
    val category: String,
    val note: String,
    val date: Long,
)

/** Single row (id = 0) holding the monthly budget. */
@Entity(tableName = "expense_budget")
data class ExpenseBudgetEntity(
    @PrimaryKey val id: Int = 0,
    val amount: Float,
)

@Dao
abstract class ExpenseDao {
    @Query("SELECT * FROM expenses")
    abstract suspend fun expenses(): List<ExpenseEntity>

    @Query("DELETE FROM expenses")
    protected abstract suspend fun deleteAllExpenses()

    @Insert
    protected abstract suspend fun insertExpenses(rows: List<ExpenseEntity>)

    /** Replaces the whole list atomically. */
    @Transaction
    open suspend fun replaceAll(rows: List<ExpenseEntity>) {
        deleteAllExpenses()
        insertExpenses(rows)
    }

    @Query("SELECT * FROM expense_budget WHERE id = 0")
    abstract suspend fun budget(): ExpenseBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setBudget(budget: ExpenseBudgetEntity)
}

class RoomExpenseStore(private val dao: ExpenseDao) : ExpenseStore {
    override suspend fun loadExpenses(): List<Expense> = dao.expenses().map {
        Expense(it.id, it.amount, enumValueOrDefault(it.category, ExpenseCategory.OTHER), it.note, LocalDate.ofEpochDay(it.date))
    }

    override suspend fun saveExpenses(expenses: List<Expense>) = dao.replaceAll(
        expenses.map { ExpenseEntity(it.id, it.amount, it.category.name, it.note, it.date.toEpochDay()) },
    )

    override suspend fun loadBudget(): Float? = dao.budget()?.amount

    override suspend fun saveBudget(budget: Float) = dao.setBudget(ExpenseBudgetEntity(amount = budget))
}
