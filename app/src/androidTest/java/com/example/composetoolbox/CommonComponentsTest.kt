package com.example.composetoolbox

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.composetoolbox.ui.LocalOpenDrawer
import com.example.composetoolbox.ui.components.DrawerMenuButton
import com.example.composetoolbox.ui.components.EmptyState
import com.example.composetoolbox.ui.components.StatItem
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_showsTitleAndMessage() {
        composeRule.setContent {
            ComposeToolboxTheme(dynamicColor = false) { EmptyState(title = "Nothing here", message = "Tap + to add") }
        }
        composeRule.pause()
        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodeWithText("Tap + to add").assertIsDisplayed()
    }

    @Test
    fun statItem_showsValueAndLabel() {
        composeRule.setContent { ComposeToolboxTheme(dynamicColor = false) { StatItem(label = "Free", value = "32") } }
        composeRule.pause()
        composeRule.onNodeWithText("32").assertIsDisplayed()
        composeRule.onNodeWithText("Free").assertIsDisplayed()
    }

    @Test
    fun drawerMenuButton_isHiddenWithoutDrawer() {
        composeRule.setContent { ComposeToolboxTheme(dynamicColor = false) { DrawerMenuButton() } }
        composeRule.pause()
        composeRule.onNodeWithContentDescription("Open menu").assertDoesNotExist()
    }

    @Test
    fun drawerMenuButton_opensDrawerWhenProvided() {
        var opened = 0
        composeRule.setContent {
            ComposeToolboxTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalOpenDrawer provides { opened++ }) { DrawerMenuButton() }
            }
        }
        composeRule.pause()
        composeRule.onNodeWithContentDescription("Open menu").performClick()
        composeRule.pause()
        assertEquals(1, opened)
    }
}
