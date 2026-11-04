package com.example.composetoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme

/**
 * A classic View-based Activity (setContentView + XML layout) that hosts
 * a piece of Compose UI through ComposeView — the "migrate one screen at a time" path.
 */
class LegacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legacy)

        findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ComposeToolboxTheme {
                    var taps by rememberSaveable { mutableIntStateOf(0) }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("I'm a Compose card", style = MaterialTheme.typography.titleLarge)
                            Text("Hosted inside an XML LinearLayout via ComposeView. Compose state works as usual: tapped $taps time(s).")
                            Button(onClick = { taps++ }) { Text("Tap me") }
                            Button(onClick = { finish() }) { Text("Back to the Compose app") }
                        }
                    }
                }
            }
        }
    }
}
