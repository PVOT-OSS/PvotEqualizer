package org.prauga.equalizer.audio

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.prauga.equalizer.MainActivity
import org.prauga.equalizer.R
import org.prauga.equalizer.data.EqStateStore

@AndroidEntryPoint
class EqTileService : TileService() {

    @Inject lateinit var engine: AudioEngine

    @Inject lateinit var stateStore: EqStateStore

    @Inject lateinit var equalizer: EqController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            engine.status.collect { render(it.enabled) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val turnOn = !engine.status.value.enabled
        stateStore.savePowerOn(turnOn)
        if (!turnOn) {
            equalizer.setEnabled(false)
            render(false)
            return
        }
        try {
            equalizer.setEnabled(true)
            render(true)
        } catch (error: IllegalStateException) {
            // Android 12+ may refuse a foreground service started from a tile. The power flag is
            // already saved, so the app turns the equalizer on as it opens.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error !is ForegroundServiceStartNotAllowedException
            ) {
                throw error
            }
            Log.i(TAG, "tile could not start the engine, opening the app", error)
            openApp()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            // Only reached below Android 14, where the Intent form is the one that exists.
            @SuppressLint("StartActivityAndCollapseDeprecated")
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

private const val TAG = "EqTile"
