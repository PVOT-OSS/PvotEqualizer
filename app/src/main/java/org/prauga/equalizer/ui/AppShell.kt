package org.prauga.equalizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import com.prauga.pvot.designsystem.navigation.PvotNavBar
import com.prauga.pvot.designsystem.navigation.PvotTabItem
import org.prauga.equalizer.R
import org.prauga.equalizer.di.AppContainer
import org.prauga.equalizer.ui.screens.equalizer.EqualizerScreen
import org.prauga.equalizer.ui.screens.presets.PresetsScreen
import org.prauga.equalizer.ui.screens.settings.SettingsScreen

/** Pixels of scroll in one event before the nav bar reacts, so a jitter doesn't flip it. */
private const val ScrollThreshold = 4f

/**
 * The app's frame: three tabs on a glass nav bar, with each screen drawing edge to edge
 * underneath it.
 */
@Composable
fun AppShell(container: AppContainer) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var navCompact by rememberSaveable { mutableStateOf(false) }
    // Swiping up through a list shrinks the nav bar out of the way; swiping back down restores it.
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -ScrollThreshold) navCompact = true
                else if (available.y > ScrollThreshold) navCompact = false
                return Offset.Zero
            }
        }
    }
    val backdrop = rememberPvotGlassBackdrop()

    val tabs = listOf(
        PvotTabItem(
            iconRes = R.drawable.ic_equalizer,
            labelRes = R.string.tab_equalizer,
            contentDescriptionRes = R.string.cd_equalizer
        ),
        PvotTabItem(
            iconRes = R.drawable.ic_presets,
            labelRes = R.string.tab_presets,
            contentDescriptionRes = R.string.cd_presets
        ),
        PvotTabItem(
            iconRes = R.drawable.ic_settings,
            labelRes = R.string.tab_settings,
            contentDescriptionRes = R.string.cd_settings
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // Screens handle their own insets and draw under the bar; the backdrop lets the glass
        // bar frost them.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            PvotNavBar(
                selectedTab = selectedTab,
                onTabClick = {
                    selectedTab = it
                    navCompact = false
                },
                tabs = tabs,
                compact = navCompact,
                backdrop = backdrop
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pvotGlassBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val screenModifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection)
                .padding(top = innerPadding.calculateTopPadding())

            when (selectedTab) {
                0 -> EqualizerScreen(container, screenModifier)
                1 -> PresetsScreen(screenModifier)
                else -> SettingsScreen(container, screenModifier)
            }
        }
    }
}
