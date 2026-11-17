package com.example.composetoolbox.feature.expense

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

enum class ExpenseCategory(val label: String, val icon: ImageVector, val color: Color) {
    FOOD("Food", Icons.Filled.Restaurant, Color(0xFFEF4444)),
    TRANSPORT("Transport", Icons.Filled.DirectionsCar, Color(0xFF3B82F6)),
    SHOPPING("Shopping", Icons.Filled.ShoppingBag, Color(0xFFA855F7)),
    BILLS("Bills", Icons.Filled.Bolt, Color(0xFFF59E0B)),
    HEALTH("Health", Icons.Filled.LocalHospital, Color(0xFF10B981)),
    FUN("Fun", Icons.Filled.Movie, Color(0xFFEC4899)),
    TRAVEL("Travel", Icons.Filled.Flight, Color(0xFF06B6D4)),
    OTHER("Other", Icons.Filled.MoreHoriz, Color(0xFF6B7280)),
}

data class Expense(
    val id: Long,
    val amount: Double,
    val category: ExpenseCategory,
    val note: String,
    val date: LocalDate,
)

const val DEFAULT_BUDGET = 25_000f

/** Where expenses and the budget are persisted (the app uses Room tables; tests pass none and stay in memory). */
interface ExpenseStore {
    suspend fun loadExpenses(): List<Expense>
    suspend fun saveExpenses(expenses: List<Expense>)
    /** Null when the user has never changed the budget. */
    suspend fun loadBudget(): Float?
    suspend fun saveBudget(budget: Float)
}

/**
 * Starts with no expenses; [initial] lets tests seed the list (see [sampleExpenses]).
 * With a [store], saved data is loaded on creation and every change is written back.
 */
class ExpenseViewModel(
    initial: List<Expense> = emptyList(),
    private val store: ExpenseStore? = null,
) : ViewModel() {
    private var lastId = initial.maxOfOrNull { it.id } ?: 0L

    /** Time-based so ids never clash with rows already in the database. */
    private fun newId(): Long = maxOf(System.currentTimeMillis(), lastId + 1).also { lastId = it }

    private val _expenses = MutableStateFlow(initial)
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _budget = MutableStateFlow(DEFAULT_BUDGET)
    val budget: StateFlow<Float> = _budget.asStateFlow()

    init {
        if (store != null) {
            viewModelScope.launch {
                val saved = store.loadExpenses()
                // Keep anything added while loading; saved rows fill in the rest.
                _expenses.update { current -> current + saved.filter { s -> current.none { it.id == s.id } } }
                _expenses.collect { store.saveExpenses(it) }
            }
            viewModelScope.launch {
                store.loadBudget()?.let { saved -> _budget.compareAndSet(DEFAULT_BUDGET, saved) }
                // Skip the initial value so an untouched default isn't written.
                _budget.drop(1).collect { store.saveBudget(it) }
            }
        }
    }

    fun add(amount: Double, category: ExpenseCategory, note: String, date: LocalDate) {
        _expenses.update { listOf(Expense(newId(), amount, category, note, date)) + it }
    }

    fun delete(expense: Expense) {
        _expenses.update { list -> list.filterNot { it.id == expense.id } }
    }

    fun restore(expense: Expense) {
        _expenses.update { list -> if (list.any { it.id == expense.id }) list else list + expense }
    }

    fun setBudget(value: Float) {
        _budget.value = value
    }

}

/** Demo data for tests only — the app itself starts empty. */
internal fun sampleExpenses(): List<Expense> {
    val today = LocalDate.now()
    fun daysAgo(n: Long) = today.minusDays(n)
    return listOf(
        Expense(1, 450.0, ExpenseCategory.FOOD, "Lunch with team", daysAgo(0)),
        Expense(2, 120.0, ExpenseCategory.TRANSPORT, "Auto ride", daysAgo(0)),
        Expense(3, 2_499.0, ExpenseCategory.SHOPPING, "Running shoes", daysAgo(1)),
        Expense(4, 1_850.5, ExpenseCategory.BILLS, "Electricity", daysAgo(2)),
        Expense(5, 640.0, ExpenseCategory.HEALTH, "Pharmacy", daysAgo(3)),
        Expense(6, 799.0, ExpenseCategory.FUN, "Movie night", daysAgo(4)),
        Expense(7, 5_600.0, ExpenseCategory.TRAVEL, "Train tickets", daysAgo(6)),
        Expense(8, 310.0, ExpenseCategory.FOOD, "Groceries", daysAgo(7)),
        Expense(9, 999.0, ExpenseCategory.BILLS, "Mobile + internet", daysAgo(9)),
        Expense(10, 260.0, ExpenseCategory.OTHER, "Stationery", daysAgo(12)),
    )
}
