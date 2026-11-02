package org.prauga.equalizer

import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.prauga.equalizer.ui.AppShell

class MainActivity : PvotBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PvotEqualizerApp).container
        // The equalizer was running when the app was last closed: bring it back without making the
        // user find the switch again. Boot restore is a separate, later step.
        if (container.stateStore.loadPowerOn()) {
            container.equalizer.setEnabled(true)
        }
        setPvotContent {
            CompositionLocalProvider(
                LocalPvotGlassIntensity provides container.preferences.glassIntensity
            ) {
                PvotAppTheme {
                    AppShell(container)
                }
            }
        }
    }
}

