package com.example.composetoolbox.ui

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class ThemeMode(val label: String) { System("Follow system"), Light("Light"), Dark("Dark") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val currency: String = "₹",
    val hourlyRate: Double = 40.0,
)

/**
 * CompositionLocal: settings are passed implicitly to every screen
 * instead of threading them through every function parameter.
 */
val LocalAppSettings = compositionLocalOf { AppSettings() }

/** Provided by the app shell when a modal drawer exists; null otherwise. */
val LocalOpenDrawer = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Plain (non-Compose) object updated from a SideEffect after every successful recomposition. */
object ScreenTracker {
    var currentScreen: String? = null
        set(value) {
            if (field != value) Log.d("ScreenTracker", "Now showing: $value")
            field = value
        }
}

fun formatMoney(amount: Double, currency: String): String {
    val pattern = if (amount % 1.0 == 0.0) "%,.0f" else "%,.2f"
    return currency + String.format(Locale.getDefault(), pattern, amount)
}
