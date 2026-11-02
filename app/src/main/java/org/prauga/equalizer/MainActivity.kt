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

