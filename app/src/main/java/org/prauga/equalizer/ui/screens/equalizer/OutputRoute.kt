package org.prauga.equalizer.ui.screens.equalizer

import android.annotation.SuppressLint
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.prauga.equalizer.R

/**
 * Where media is most likely playing, as a string resource, kept current as devices come and go.
 *
 * Android does not name the active media route on every supported version, so this picks the
 * device media would normally prefer: Bluetooth, then USB, then wired, then the speaker.
 */
@Composable
@StringRes
fun rememberOutputRoute(): Int {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    var route by remember { mutableIntStateOf(currentRoute(audio)) }
    DisposableEffect(audio) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                route = currentRoute(audio)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                route = currentRoute(audio)
            }
        }
        audio?.registerAudioDeviceCallback(callback, null)
        onDispose { audio?.unregisterAudioDeviceCallback(callback) }
    }
    return route
}

@StringRes
private fun currentRoute(audio: AudioManager?): Int {
    val types = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.map { it.type }.orEmpty()
    return when {
        types.any { it in BLUETOOTH } -> R.string.eq_route_bluetooth
        types.any { it in USB } -> R.string.eq_route_usb
        types.any { it in WIRED } -> R.string.eq_route_wired
        else -> R.string.eq_route_speaker
    }
}

// Older releases never report the LE audio types, so listing them there is harmless.
@SuppressLint("InlinedApi")
private val BLUETOOTH = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER
)
private val USB = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
private val WIRED = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)
