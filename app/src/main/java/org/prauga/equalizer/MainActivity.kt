package org.prauga.equalizer

import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.prauga.equalizer.audio.EqController
import org.prauga.equalizer.data.EqStateStore
import org.prauga.equalizer.ui.AppShell

@AndroidEntryPoint
class MainActivity : PvotBaseActivity() {

    @Inject lateinit var preferences: AppPreferences

    @Inject lateinit var stateStore: EqStateStore

    @Inject lateinit var equalizer: EqController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The equalizer was running when the app was last closed: bring it back without making the
        // user find the switch again. Boot restore is a separate, later step.
        if (stateStore.loadPowerOn()) {
            equalizer.setEnabled(true)
        }
        setPvotContent {
            CompositionLocalProvider(
                LocalPvotGlassIntensity provides preferences.glassIntensity
            ) {
                PvotAppTheme {
                    AppShell(preferences)
                }
            }
        }
    }
}

