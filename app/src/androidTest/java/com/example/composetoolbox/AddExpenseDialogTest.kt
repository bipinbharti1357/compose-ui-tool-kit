package com.example.composetoolbox

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.composetoolbox.feature.expense.AddExpenseDialog
import com.example.composetoolbox.feature.expense.ExpenseCategory
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AddExpenseDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Saved(val amount: Double, val category: ExpenseCategory, val note: String, val date: LocalDate)

    private var saved: Saved? = null
    private var dismissed = false

    private fun show(initialCategory: ExpenseCategory? = null) {
        composeRule.setContent {
            ComposeToolboxTheme(dynamicColor = false) {
                AddExpenseDialog(
                    initialCategory = initialCategory,
                    currency = "₹",
                    onDismiss = { dismissed = true },
                    onSave = { amount, category, note, date -> saved = Saved(amount, category, note, date) },
                )
            }
        }
        composeRule.pause()
    }

    private fun amountField() = composeRule.onNode(hasSetTextAction() and hasText("Amount"))
    private fun saveButton() = composeRule.onNodeWithText("Save").performScrollTo()

    @Test
    fun save_isDisabledUntilAValidAmountIsEntered() {
        show()
        saveButton().assertIsNotEnabled()
        amountField().performTextInput("0")
        composeRule.pause()
        saveButton().assertIsNotEnabled()
    }

    @Test
    fun amountField_dropsNonNumericCharacters() {
        show()
        amountField().performTextInput("12a3.5x")
        composeRule.pause()
        composeRule.onNode(hasSetTextAction() and hasText("123.5")).assertExists()
    }

    @Test
    fun save_passesAmountCategoryTrimmedNoteAndToday() {
        show(initialCategory = ExpenseCategory.TRANSPORT)
        amountField().performTextInput("250.5")
        composeRule.pause()
        composeRule.onNode(hasSetTextAction() and hasText("Note")).performTextInput("  Taxi  ")
        composeRule.pause()
        saveButton().assertIsEnabled().performClick()
        composeRule.pause()

        assertEquals(Saved(250.5, ExpenseCategory.TRANSPORT, "Taxi", LocalDate.now()), saved)
    }

    @Test
    fun cancel_invokesOnDismiss() {
        show()
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        composeRule.pause()
        assertTrue(dismissed)
        assertEquals(null, saved)
    }
}
