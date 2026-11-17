package com.example.composetoolbox

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/**
 * "Slow motion" for watching UI tests on a device.
 *
 * The pause length comes from the `slowMo` instrumentation argument (milliseconds), which
 * app/build.gradle.kts sets from the Gradle property `-PslowMo=<ms>` (default 1500).
 * Use `-PslowMo=0` to run at full speed (e.g. on CI).
 */
object SlowMo {
    val delayMs: Long by lazy {
        InstrumentationRegistry.getArguments().getString("slowMo")?.toLongOrNull() ?: 0L
    }
}

/** Lets the UI settle, then holds the current screen for [ms] so a person can see it. */
fun ComposeTestRule.pause(ms: Long = SlowMo.delayMs) {
    if (ms <= 0) return
    waitForIdle()
    Thread.sleep(ms)
}
