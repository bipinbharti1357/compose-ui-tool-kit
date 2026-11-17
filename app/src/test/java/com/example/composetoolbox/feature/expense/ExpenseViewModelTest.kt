package com.example.composetoolbox.feature.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseViewModelTest {

    @Test
    fun defaultConstructor_startsEmpty() {
        val vm = ExpenseViewModel()
        assertTrue(vm.expenses.value.isEmpty())
        assertEquals(DEFAULT_BUDGET, vm.budget.value, 0f)
    }

    @Test
    fun startsWithSampleExpensesAndDefaultBudget() {
        val vm = ExpenseViewModel(sampleExpenses())
        assertEquals(10, vm.expenses.value.size)
        assertEquals(25_000f, vm.budget.value, 0f)
    }

    @Test
    fun add_prependsExpenseWithNewIds() {
        val vm = ExpenseViewModel(sampleExpenses())
        val date = LocalDate.of(2026, 1, 15)
        vm.add(99.5, ExpenseCategory.FOOD, "Coffee", date)
        vm.add(10.0, ExpenseCategory.OTHER, "Pen", date)

        val (pen, coffee) = vm.expenses.value.take(2)
        assertEquals(Expense(coffee.id, 99.5, ExpenseCategory.FOOD, "Coffee", date), coffee)
        assertEquals("Pen", pen.note)
        assertTrue(pen.id != coffee.id)
        assertTrue(vm.expenses.value.map { it.id }.distinct().size == vm.expenses.value.size)
    }

    @Test
    fun delete_thenRestore() {
        val vm = ExpenseViewModel(sampleExpenses())
        val expense = vm.expenses.value.first { it.id == 3L }
        vm.delete(expense)
        assertNull(vm.expenses.value.firstOrNull { it.id == 3L })
        vm.restore(expense)
        assertEquals(expense, vm.expenses.value.first { it.id == 3L })
    }

    @Test
    fun restore_doesNotDuplicate() {
        val vm = ExpenseViewModel(sampleExpenses())
        vm.restore(vm.expenses.value.first())
        assertEquals(10, vm.expenses.value.size)
    }

    @Test
    fun setBudget_updatesBudget() {
        val vm = ExpenseViewModel(sampleExpenses())
        vm.setBudget(12_345f)
        assertEquals(12_345f, vm.budget.value, 0f)
    }
}
