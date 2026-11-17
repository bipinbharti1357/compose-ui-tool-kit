package com.example.composetoolbox

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.composetoolbox.feature.todo.Priority
import com.example.composetoolbox.feature.todo.TaskStatus
import com.example.composetoolbox.feature.todo.Todo
import com.example.composetoolbox.feature.todo.TodoItemCard
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoItemCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTitleNotesTagsAndReminder() {
        val todo = Todo(1, "Buy groceries", "Milk, eggs", Priority.HIGH, setOf("Shopping"), reminder = true)
        composeRule.setContent { ComposeToolboxTheme(dynamicColor = false) { TodoItemCard(todo, onToggle = {}) } }
        composeRule.pause()

        composeRule.onNodeWithText("Buy groceries").assertIsDisplayed()
        composeRule.onNodeWithText("Milk, eggs").assertIsDisplayed()
        composeRule.onNodeWithText("#Shopping").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reminder on").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun doneTask_isChecked_andHasNoReminderIcon() {
        val todo = Todo(2, "Morning run", status = TaskStatus.DONE)
        composeRule.setContent { ComposeToolboxTheme(dynamicColor = false) { TodoItemCard(todo, onToggle = {}) } }
        composeRule.pause()

        composeRule.onNode(isToggleable()).assertIsOn()
        composeRule.onNodeWithContentDescription("Reminder on").assertDoesNotExist()
    }

    @Test
    fun clickingCheckbox_invokesOnToggle() {
        var toggles = 0
        composeRule.setContent {
            ComposeToolboxTheme(dynamicColor = false) { TodoItemCard(Todo(3, "Read"), onToggle = { toggles++ }) }
        }
        composeRule.pause()
        composeRule.onNode(isToggleable()).performClick()
        composeRule.pause()
        assertEquals(1, toggles)
    }
}
