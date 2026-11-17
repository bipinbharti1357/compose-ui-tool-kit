package com.example.composetoolbox

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.composetoolbox.feature.todo.AddTodoSheet
import com.example.composetoolbox.feature.todo.Priority
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AddTodoSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Saved(val title: String, val notes: String, val priority: Priority, val tags: Set<String>, val date: LocalDate)

    @Test
    fun save_passesEnteredValues() {
        var saved: Saved? = null
        val date = LocalDate.now().plusDays(2)
        composeRule.setContent {
            ComposeToolboxTheme(dynamicColor = false) {
                AddTodoSheet(
                    onDismiss = {},
                    onSave = { title, notes, priority, tags, _, d, _ -> saved = Saved(title, notes, priority, tags, d) },
                    initialDate = date,
                )
            }
        }
        composeRule.pause()

        composeRule.onNodeWithTag("todo_title_input").performTextInput("Gym session")
        composeRule.pause()
        composeRule.onNodeWithText("Notes (optional)").performTextInput("Leg day")
        composeRule.pause()
        composeRule.onNodeWithText("High").performClick()
        composeRule.pause()
        composeRule.onNodeWithText("Health").performClick()
        composeRule.pause()
        // Close the IME first: if it closes during the sheet's hide animation, the hide is
        // interrupted and AddTodoSheet skips onSave/onDismiss.
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText("Add task").performScrollTo().assertIsEnabled().performClick()
        composeRule.pause()

        composeRule.waitUntil(timeoutMillis = 5_000) { saved != null }
        assertEquals(Saved("Gym session", "Leg day", Priority.HIGH, setOf("Health"), date), saved)
    }

    @Test
    fun cancel_dismissesWithoutSaving() {
        var dismissed = false
        var savedCalled = false
        composeRule.setContent {
            ComposeToolboxTheme(dynamicColor = false) {
                AddTodoSheet(onDismiss = { dismissed = true }, onSave = { _, _, _, _, _, _, _ -> savedCalled = true })
            }
        }
        composeRule.pause()
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        composeRule.pause()

        composeRule.waitUntil(timeoutMillis = 5_000) { dismissed }
        assertFalse(savedCalled)
    }
}
