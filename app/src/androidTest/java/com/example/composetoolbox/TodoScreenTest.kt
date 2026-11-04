package com.example.composetoolbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.composetoolbox.feature.todo.TodoScreen
import com.example.composetoolbox.feature.todo.TodoViewModel
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Semantics-based UI test: the same semantics tree TalkBack uses. */
@RunWith(AndroidJUnit4::class)
class TodoScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addingATask_showsItInTheList() {
        val vm = TodoViewModel()
        composeRule.setContent { ComposeToolboxTheme { TodoScreen(vm) } }

        composeRule.onNodeWithTag("add_todo_fab").performClick()
        composeRule.onNodeWithTag("todo_title_input").performTextInput("Buy milk")
        composeRule.onNodeWithText("Add task").performClick()

        composeRule.onNodeWithText("Buy milk").assertIsDisplayed()
    }
}
