package com.example.composetoolbox.feature.settings

import androidx.lifecycle.ViewModel
import com.example.composetoolbox.ui.AppSettings
import com.example.composetoolbox.ui.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel : ViewModel() {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = _settings.update { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = _settings.update { it.copy(dynamicColor = enabled) }
    fun setCurrency(symbol: String) = _settings.update { it.copy(currency = symbol) }

    /** Each step = 5 per hour, clamped to 10..500. */
    fun adjustHourlyRate(steps: Int) = _settings.update {
        it.copy(hourlyRate = (it.hourlyRate + steps * 5).coerceIn(10.0, 500.0))
    }
}
