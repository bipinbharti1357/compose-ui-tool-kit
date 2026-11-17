package com.example.composetoolbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.composetoolbox.feature.parking.ParkingScreen
import com.example.composetoolbox.feature.parking.ParkingViewModel
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParkingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var vm: ParkingViewModel

    @Before
    fun setUp() {
        vm = ParkingViewModel()
        // The screen has a 1-second ticking clock, so let tests drive time explicitly.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { ComposeToolboxTheme(dynamicColor = false) { ParkingScreen(vm) } }
        composeRule.pause()
        composeRule.mainClock.advanceTimeBy(500)
    }

    private fun settle() = composeRule.mainClock.advanceTimeBy(1_000)

    @Test
    fun showsTitleTabsAndQueue() {
        composeRule.onNodeWithText("Car Parking").assertIsDisplayed()
        listOf("Slots", "Active", "History", "Map").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("RJ14CD7777").assertIsDisplayed()
    }

    @Test
    fun simulateArrival_addsVehicleToQueue() {
        composeRule.onNodeWithContentDescription("Simulate arrival").performClick()
        composeRule.pause()
        settle()
        assertEquals(3, vm.state.value.queue.size)
        composeRule.onNodeWithText("A vehicle arrived at the gate. Drag it onto a free slot.").assertIsDisplayed()
    }

    @Test
    fun historyTab_showsPastTickets() {
        composeRule.onNodeWithText("History").performClick()
        composeRule.pause()
        settle()
        composeRule.onNodeWithText("KL07BB2020", substring = true).assertIsDisplayed()
    }

    @Test
    fun parkingInAFreeSlot_createsActiveTicket() {
        composeRule.onNodeWithText("L1-03").performClick()
        composeRule.pause()
        settle()
        composeRule.onNodeWithText("Slot L1-03").assertIsDisplayed()

        val plateField = composeRule.onNode(hasSetTextAction() and hasText("Vehicle number"))
        plateField.performTextInput("ka01")
        composeRule.pause()
        settle()
        composeRule.onNodeWithText("Park vehicle").assertIsNotEnabled()
        composeRule.onNodeWithText("6–12 letters or digits").assertIsDisplayed()

        plateField.performTextInput("ab2345")
        composeRule.pause()
        settle()
        composeRule.onNodeWithText("Park vehicle").assertIsEnabled().performClick()
        composeRule.pause()
        settle()

        val ticket = vm.state.value.active.values.firstOrNull { it.plate == "KA01AB2345" }
        assertTrue("vehicle should be parked", ticket != null)
        assertEquals("L1-3", ticket!!.slotId)
    }
}
