@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.composetoolbox.feature.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.composetoolbox.LegacyActivity
import com.example.composetoolbox.R
import com.example.composetoolbox.ui.ThemeMode
import com.example.composetoolbox.ui.formatMoney

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection("Appearance") {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.themeMode == mode,
                                    onClick = { vm.setThemeMode(mode) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.themeMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(mode.label)
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dynamic colour")
                        Text(
                            "Use wallpaper colours (Android 12+)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.dynamicColor, onCheckedChange = vm::setDynamicColor)
                }
            }

            SettingsSection("Currency") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("₹", "$", "€", "£").forEach { symbol ->
                        Row(
                            Modifier.selectable(
                                selected = settings.currency == symbol,
                                onClick = { vm.setCurrency(symbol) },
                                role = Role.RadioButton,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.currency == symbol, onClick = null)
                            Spacer(Modifier.width(4.dp))
                            Text(symbol, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            SettingsSection("Parking rate") {
                RateDial(
                    rateLabel = formatMoney(settings.hourlyRate, settings.currency) + " / hour",
                    onStep = vm::adjustHourlyRate,
                )
            }

            SettingsSection("Interoperability") {
                Text(
                    "Open a classic XML screen that hosts Compose through ComposeView. " +
                        "(The expense dialog shows the reverse: a legacy CalendarView inside Compose via AndroidView.)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { context.startActivity(Intent(context, LegacyActivity::class.java)) }) {
                    Text("Open legacy XML screen")
                }
            }

            SettingsSection("About") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_app_logo), contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Compose Toolbox 1.0", style = MaterialTheme.typography.titleMedium)
                        Text("Jetpack Compose · Material 3", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * Modifier.scrollable: drag up/down on the dial to change the value.
 * The scroll delta is accumulated and converted into whole "steps".
 */
@Composable
private fun RateDial(rateLabel: String, onStep: (Int) -> Unit) {
    val stepPx = with(LocalDensity.current) { 24.dp.toPx() }
    var accumulated by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollableState { delta ->
        accumulated -= delta // dragging up increases the rate
        val steps = (accumulated / stepPx).toInt()
        if (steps != 0) {
            accumulated -= steps * stepPx
            onStep(steps)
        }
        delta // consume it so the page doesn't scroll at the same time
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onStep(-1) }) { Icon(Icons.Filled.Remove, contentDescription = "Decrease rate") }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .weight(1f)
                .height(96.dp)
                .scrollable(state = scrollState, orientation = Orientation.Vertical),
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(rateLabel, style = MaterialTheme.typography.headlineSmall)
                Text("Drag up / down here to adjust", style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { onStep(1) }) { Icon(Icons.Filled.Add, contentDescription = "Increase rate") }
    }
}
