package com.example.composetoolbox.feature.settings

import com.example.composetoolbox.ui.AppSettings
import com.example.composetoolbox.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun startsWithDefaults() {
        assertEquals(AppSettings(), SettingsViewModel().settings.value)
    }

    @Test
    fun setters_updateOnlyTheirField() {
        val vm = SettingsViewModel()
        vm.setThemeMode(ThemeMode.Dark)
        vm.setDynamicColor(false)
        vm.setCurrency("€")

        val s = vm.settings.value
        assertEquals(ThemeMode.Dark, s.themeMode)
        assertFalse(s.dynamicColor)
        assertEquals("€", s.currency)
        assertEquals(40.0, s.hourlyRate, 0.0)
    }

    @Test
    fun adjustHourlyRate_movesInStepsOfFive() {
        val vm = SettingsViewModel()
        vm.adjustHourlyRate(2)
        assertEquals(50.0, vm.settings.value.hourlyRate, 0.0)
        vm.adjustHourlyRate(-3)
        assertEquals(35.0, vm.settings.value.hourlyRate, 0.0)
    }

    @Test
    fun adjustHourlyRate_isClampedBetween10And500() {
        val vm = SettingsViewModel()
        vm.adjustHourlyRate(-100)
        assertEquals(10.0, vm.settings.value.hourlyRate, 0.0)
        vm.adjustHourlyRate(1_000)
        assertEquals(500.0, vm.settings.value.hourlyRate, 0.0)
    }
}
