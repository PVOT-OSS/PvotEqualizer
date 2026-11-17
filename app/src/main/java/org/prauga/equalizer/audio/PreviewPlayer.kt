package org.prauga.equalizer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loops a few seconds of pink noise on the app's own audio session and hands that session to the
 * engine, so the curve can be heard without another app. Pink noise has equal energy per octave,
 * which makes every band's change audible.
 *
 * Generating and starting the track takes a few milliseconds; call [start] off the main thread.
 */
@Singleton
class PreviewPlayer @Inject constructor(
    @ApplicationContext context: Context,
    private val engine: AudioEngine
) {

    private val packageName: String = context.packageName
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private var track: AudioTrack? = null

    @Synchronized
    fun start() {
        if (track != null) return
        val samples = pinkNoise(SAMPLE_RATE * LOOP_SECONDS * CHANNELS)
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .build()
        created.write(samples, 0, samples.size)
        created.setLoopPoints(0, samples.size / CHANNELS, -1)
        engine.attach(created.audioSessionId, packageName)
        created.play()
        track = created
        _playing.value = true
    }

    @Synchronized
    fun stop() {
        val current = track ?: return
        track = null
        current.stop()
        engine.detach(current.audioSessionId)
        current.release()
        _playing.value = false
    }

    private fun pinkNoise(count: Int): ShortArray {
        val random = Random(SEED)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        return ShortArray(count) {
            val white = random.nextDouble(-1.0, 1.0)
            b0 = 0.99765 * b0 + white * 0.0990460
            b1 = 0.96300 * b1 + white * 0.2965164
            b2 = 0.57000 * b2 + white * 1.0526913
            val pink = (b0 + b1 + b2 + white * 0.1848) * PINK_SCALE
            (pink.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

private const val SAMPLE_RATE = 48_000
private const val CHANNELS = 2
private const val LOOP_SECONDS = 3
private const val SEED = 7

/** Quiet on purpose, so the curve's boosts have room before the limiter acts. */
private const val PINK_SCALE = 0.04
