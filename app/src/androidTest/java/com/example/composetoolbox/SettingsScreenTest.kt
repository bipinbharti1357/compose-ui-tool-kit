package com.example.composetoolbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.composetoolbox.feature.settings.SettingsScreen
import com.example.composetoolbox.feature.settings.SettingsViewModel
import com.example.composetoolbox.ui.ThemeMode
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val vm = SettingsViewModel()
    private var backClicks = 0

    @Before
    fun setUp() {
        composeRule.setContent {
            ComposeToolboxTheme(dynamicColor = false) { SettingsScreen(vm, onBack = { backClicks++ }) }
        }
        composeRule.pause()
    }

    @Test
    fun selectingThemeMode_updatesSelection() {
        composeRule.onNodeWithText("Follow system").assertIsSelected()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.pause()

        composeRule.onNodeWithText("Dark").assertIsSelected()
        composeRule.onNodeWithText("Follow system").assertIsNotSelected()
        assertEquals(ThemeMode.Dark, vm.settings.value.themeMode)
    }

    @Test
    fun dynamicColourSwitch_togglesSetting() {
        composeRule.onNode(isToggleable()).assertIsOn().performClick()
        composeRule.pause()
        composeRule.onNode(isToggleable()).assertIsOff()
        assertFalse(vm.settings.value.dynamicColor)
    }

    @Test
    fun selectingCurrency_updatesRateLabel() {
        composeRule.onNodeWithText("₹40 / hour").performScrollTo().assertIsDisplayed()
        composeRule.pause()
        composeRule.onNodeWithText("$").performClick()
        composeRule.pause()

        composeRule.onNodeWithText("$").assertIsSelected()
        composeRule.onNodeWithText("$40 / hour").assertIsDisplayed()
        assertEquals("$", vm.settings.value.currency)
    }

    @Test
    fun rateButtons_adjustAndClampRate() {
        composeRule.onNodeWithContentDescription("Increase rate").performScrollTo().performClick()
        composeRule.pause()
        composeRule.onNodeWithText("₹45 / hour").assertIsDisplayed()

        repeat(10) { composeRule.onNodeWithContentDescription("Decrease rate").performClick() }
        composeRule.pause()
        composeRule.onNodeWithText("₹10 / hour").assertIsDisplayed()
        assertEquals(10.0, vm.settings.value.hourlyRate, 0.0)
    }

    @Test
    fun backButton_invokesCallback() {
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.pause()
        assertTrue(backClicks == 1)
    }
}
