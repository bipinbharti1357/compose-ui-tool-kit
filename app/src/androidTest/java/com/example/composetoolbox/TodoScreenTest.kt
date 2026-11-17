package com.example.composetoolbox

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipeLeft
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.composetoolbox.feature.todo.TaskStatus
import com.example.composetoolbox.feature.todo.TodoScreen
import com.example.composetoolbox.feature.todo.TodoViewModel
import com.example.composetoolbox.feature.todo.sampleTodos
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Semantics-based UI test: the same semantics tree TalkBack uses. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TodoScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var vm: TodoViewModel

    @Before
    fun setUp() {
        vm = TodoViewModel(sampleTodos())
        composeRule.setContent { ComposeToolboxTheme(dynamicColor = false) { TodoScreen(vm) } }
        composeRule.pause()
    }

    @Test
    fun showsTitleAndTabCounts() {
        composeRule.onNodeWithText("Todo List").assertIsDisplayed()
        composeRule.onNodeWithText("All (7)").assertIsDisplayed()
        composeRule.onNodeWithText("Active (5)").assertIsDisplayed()
        composeRule.onNodeWithText("Done (2)").assertIsDisplayed()
    }

    @Test
    fun addingATask_showsItInTheList() {
        composeRule.onNodeWithTag("add_todo_fab").performClick()
        composeRule.pause()
        composeRule.onNodeWithTag("todo_title_input").performTextInput("Buy milk")
        composeRule.pause()
        // Close the IME first: if it closes during the sheet's hide animation, the hide is
        // interrupted and AddTodoSheet skips onSave/onDismiss.
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText("Add task").performScrollTo().performClick()
        composeRule.pause()

        // The sheet saves only after its hide animation, so wait for the new task to land.
        composeRule.waitUntil(timeoutMillis = 5_000) { vm.todos.value.size == 8 }
        composeRule.onNodeWithTag("todo_title_input").assertDoesNotExist()
        // New tasks are prepended; LazyColumn keeps the previous first item anchored,
        // so the new row may sit just above the viewport -> check existence, not visibility.
        composeRule.onNodeWithText("Buy milk").assertExists()
        assertEquals("Buy milk", vm.todos.value.first().title)
        composeRule.onNodeWithText("All (8)").assertIsDisplayed()
    }

    @Test
    fun addTaskButton_isDisabledWhileTitleIsBlank() {
        composeRule.onNodeWithTag("add_todo_fab").performClick()
        composeRule.pause()
        composeRule.onNodeWithText("New task").assertIsDisplayed()
        composeRule.onNodeWithText("Add task").performScrollTo().assertIsNotEnabled()
        composeRule.pause()
    }

    @Test
    fun doubleTappingATask_marksItDone() {
        composeRule.onAllNodesWithText("Buy vegetables and milk").onFirst()
            .performTouchInput { doubleClick() }
            composeRule.pause()

        composeRule.waitForIdle()
        assertEquals(TaskStatus.DONE, vm.todos.value.first { it.id == 2L }.status)
        composeRule.onNodeWithText("Done (3)").assertIsDisplayed()
    }

    @Test
    fun search_filtersVisibleTasks() {
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.pause()
        composeRule.onNode(hasSetTextAction()).performTextInput("electricity")
        composeRule.pause()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Prepare sprint demo").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Pay electricity bill").assertIsDisplayed()
    }

    @Test
    fun clearCompleted_removesFinishedOneOffTasksAfterConfirming() {
        composeRule.onNodeWithContentDescription("Sort and actions").performClick()
        composeRule.pause()
        composeRule.onNodeWithText("Clear completed").performClick()
        composeRule.pause()

        composeRule.onNodeWithText("Clear completed?").assertIsDisplayed()
        composeRule.onNode(hasText("This removes 1 completed task(s)", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Clear").performClick()
        composeRule.pause()

        composeRule.waitForIdle()
        assertNull(vm.todos.value.firstOrNull { it.id == 6L })
        composeRule.onNodeWithText("All (6)").assertIsDisplayed()
    }

    @Test
    fun clearCompleted_cancelKeepsTasks() {
        composeRule.onNodeWithContentDescription("Sort and actions").performClick()
        composeRule.pause()
        composeRule.onNodeWithText("Clear completed").performClick()
        composeRule.pause()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.pause()

        composeRule.onNodeWithText("Clear completed?").assertDoesNotExist()
        assertEquals(7, vm.todos.value.size)
    }

    @Test
    fun swipingATaskLeft_deletesIt_andUndoRestoresIt() {
        composeRule.mainClock.autoAdvance = false
        composeRule.onAllNodesWithText("Prepare sprint demo").onFirst().performTouchInput { swipeLeft() }
        composeRule.pause()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertTrue(vm.todos.value.none { it.id == 1L })
        composeRule.onNodeWithText("\"Prepare sprint demo\" deleted").assertIsDisplayed()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.pause()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertTrue(vm.todos.value.any { it.id == 1L })
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun calendarToggle_switchesToDailyTasks() {
        composeRule.onNodeWithContentDescription("Show calendar").performClick()
        composeRule.pause()
        composeRule.onNodeWithText("Daily Tasks").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Show list").performClick()
        composeRule.pause()
        composeRule.onNodeWithText("Todo List").assertIsDisplayed()
    }
}
