package org.prauga.equalizer.audio

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.prauga.equalizer.core.model.EqProfile
import org.prauga.equalizer.core.dsp.DpBand
import org.prauga.equalizer.core.dsp.DpBandFit
import org.prauga.equalizer.core.dsp.DpFit

/**
 * The real engine: one `DynamicsProcessing` per audio session, using the pre-EQ stage for the curve
 * and the limiter so a boosted curve cannot push the signal past full scale.
 *
 * Every call is marshalled onto one background thread, because an audio effect must not be
 * configured from the UI thread and the calls would otherwise interleave. The band count is fixed
 * when an effect is created, which is why [DpBandFit] always returns exactly the count it is asked
 * for.
 */
class DynamicsProcessingEngine(
    private val sampleRateHz: Double = SAMPLE_RATE_HZ
) : AudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val _status = MutableStateFlow(EngineStatus())
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val instances = LinkedHashMap<Int, DynamicsProcessing>()

    /**
     * Sessions we have been told about, whether or not the equalizer was on at the time.
     *
     * A session id stays valid for as long as its track lives, so remembering it lets the equalizer
     * be switched off and on again without the user having to restart playback.
     */
    private val known = LinkedHashMap<Int, String?>()
    private val packages = mutableMapOf<Int, String>()

    private var bandCount = ENGINE_BANDS
    private var profile: EqProfile = EqProfile.flat()
    private var fitted: DpFit? = null
    private var isEnabled = false
    private var isGlobal = false
    private var isBypassed = false
    private var failure: String? = null

    override fun setEnabled(enabled: Boolean) {
        scope.launch {
            isEnabled = enabled
            failure = null
            if (!enabled) isBypassed = false
            if (enabled) attachAll() else releaseInstances()
            publish()
        }
    }

    override fun setGlobal(global: Boolean) {
        scope.launch {
            if (isGlobal == global) return@launch
            isGlobal = global
            failure = null
            releaseInstances()
            if (isEnabled) attachAll()
            publish()
        }
    }

    override fun setBypassed(bypassed: Boolean) {
        scope.launch {
            isBypassed = bypassed && isEnabled
            for (effect in instances.values) {
                runCatching { effect.setEnabled(!isBypassed) }
                    .onFailure { failure = describe(it) }
            }
            publish()
        }
    }

    private fun attachAll() {
        if (isGlobal) {
            createInstance(OUTPUT_MIX_SESSION)
            return
        }
        // Re-attach to the sessions already playing instead of waiting for a broadcast that will
        // never come: the player announced its session once, at the start.
        Log.i(TAG, "enable: known sessions=${known.keys}")
        for (sessionId in known.keys.toList()) {
            if (!createInstance(sessionId, reportFailure = false)) {
                known.remove(sessionId)
            }
        }
    }

    override fun setProfile(profile: EqProfile) {
        scope.launch {
            this@DynamicsProcessingEngine.profile = profile
            fitted = null
            if (isEnabled) {
                val bands = fitNow().bands
                for (sessionId in instances.keys.toList()) {
                    applyBands(sessionId, bands)
                }
            }
            publish()
        }
    }

    override fun attach(sessionId: Int, packageName: String?) {
        scope.launch {
            if (sessionId <= 0) return@launch
            known[sessionId] = packageName ?: known[sessionId]
            Log.i(TAG, "attach session=$sessionId package=$packageName enabled=$isEnabled")
            if (packageName != null) packages[sessionId] = packageName
            if (!isEnabled || isGlobal || instances.containsKey(sessionId)) {
                publish()
                return@launch
            }
            createInstance(sessionId)
        }
    }

    override fun detach(sessionId: Int) {
        scope.launch {
            Log.i(TAG, "detach session=$sessionId")
            known.remove(sessionId)
            instances.remove(sessionId)?.let { effect ->
                runCatching { effect.setEnabled(false) }
                runCatching { effect.release() }
            }
            publish()
        }
    }

    override fun release() {
        scope.launch {
            releaseInstances()
            publish()
        }
    }

    /**
     * @param reportFailure whether a failure is worth telling the user about. A session we
     *   remembered going away is not: its player ended the track, and the honest state is "waiting".
     * @return whether the effect is now attached to [sessionId]
     */
    private fun createInstance(sessionId: Int, reportFailure: Boolean = true): Boolean {
        if (instances.containsKey(sessionId)) return true
        var effect: DynamicsProcessing? = null
        var attached = false
        try {
            effect = DynamicsProcessing(PRIORITY, sessionId, buildConfig())
            val bands = fitNow().bands
            effect.setPreEqAllChannelsTo(eqFor(bands))
            if (bandCount > CLAMPED_BANDS && clampsBands(effect, bands)) {
                Log.i(TAG, "effect HAL clamps pre-EQ to $CLAMPED_BANDS bands; refitting")
                runCatching { effect.release() }
                useClampedBandCount()
                return instances.containsKey(sessionId)
            }
            effect.setEnabled(!isBypassed)
            instances[sessionId] = effect
            failure = null
            attached = true
            Log.i(TAG, "created effect for session=$sessionId")
        } catch (error: Throwable) {
            // Some devices reject the effect on some routes, and a remembered session may have
            // finished since we saw it. Say so instead of crashing or pretending it works.
            if (reportFailure) failure = describe(error)
            Log.i(TAG, "could not create effect for session=$sessionId: ${describe(error)}")
            runCatching { effect?.release() }
        }
        publish()
        return attached
    }

    /**
     * Android 14+ effect HALs may cap pre-EQ at 32 bands, and the framework then clamps every higher
     * band index to the last one instead of failing. Band [CLAMPED_BANDS] - 1 is then left holding
     * whatever was written last, which reveals the cap.
     */
    private fun clampsBands(effect: DynamicsProcessing, bands: List<DpBand>): Boolean {
        val probe = CLAMPED_BANDS - 1
        val actual = effect.getPreEqBandByChannelIndex(0, probe).cutoffFrequency.toDouble()
        return abs(actual - bands[probe].cutoffHz) > CUTOFF_TOLERANCE_HZ
    }

    private fun useClampedBandCount() {
        bandCount = CLAMPED_BANDS
        fitted = null
        releaseInstances()
        attachAll()
    }

    private fun applyBands(sessionId: Int, bands: List<DpBand>) {
        val effect = instances[sessionId] ?: return
        runCatching { effect.setPreEqAllChannelsTo(eqFor(bands)) }
            .onFailure { failure = describe(it) }
    }

    private fun buildConfig(): DynamicsProcessing.Config = DynamicsProcessing.Config.Builder(
        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
        CHANNELS,
        /* preEqInUse = */ true,
        /* preEqBandCount = */ bandCount,
        /* mbcInUse = */ false,
        /* mbcBandCount = */ 0,
        /* postEqInUse = */ false,
        /* postEqBandCount = */ 0,
        /* limiterInUse = */ true
    )
        .setPreferredFrameDuration(FRAME_DURATION_MS)
        .setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                /* inUse = */ true,
                /* enabled = */ true,
                /* linkGroup = */ 0,
                /* attackTime = */ LIMITER_ATTACK_MS,
                /* releaseTime = */ LIMITER_RELEASE_MS,
                /* ratio = */ LIMITER_RATIO,
                /* threshold = */ LIMITER_THRESHOLD_DB,
                /* postGain = */ 0f
            )
        )
        .build()

    private fun eqFor(bands: List<DpBand>): DynamicsProcessing.Eq {
        val eq = DynamicsProcessing.Eq(/* inUse = */ true, /* enabled = */ true, bands.size)
        for (index in bands.indices) {
            val band = bands[index]
            eq.setBand(
                index,
                DynamicsProcessing.EqBand(
                    /* enabled = */ true,
                    /* cutoffFrequency = */ band.cutoffHz.toFloat(),
                    /* gain = */ band.gainDb.toFloat().coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
                )
            )
        }
        return eq
    }

    private fun fitNow(): DpFit = fitted ?: DpBandFit.fit(profile, sampleRateHz, bandCount)
        .also { fitted = it }

    private fun releaseInstances() {
        for (effect in instances.values) {
            runCatching { effect.setEnabled(false) }
            runCatching { effect.release() }
        }
        instances.clear()
    }

    private fun publish() {
        _status.value = EngineStatus(
            enabled = isEnabled,
            bandCount = fitted?.bands?.size ?: 0,
            sessions = instances.size,
            attachedPackages = instances.keys.mapNotNull(packages::get).distinct().sorted(),
            failure = failure,
            global = isGlobal,
            bypassed = isBypassed
        )
    }

    private fun describe(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message ?: "no detail"}"
}

private const val TAG = "EqEngine"
private const val PRIORITY = 0
private const val OUTPUT_MIX_SESSION = 0
private const val CHANNELS = 2
private const val ENGINE_BANDS = DpBandFit.DEFAULT_BAND_COUNT
private const val CLAMPED_BANDS = 32
private const val CUTOFF_TOLERANCE_HZ = 1.0
private const val SAMPLE_RATE_HZ = 48_000.0

private const val FRAME_DURATION_MS = 10f

private const val LIMITER_ATTACK_MS = 1f
private const val LIMITER_RELEASE_MS = 60f
private const val LIMITER_RATIO = 10f
private const val LIMITER_THRESHOLD_DB = -1f

/**
 * The engine's own ceiling, applied before the band reaches `DynamicsProcessing` so an out-of-range
 * profile fails quietly instead of throwing. A preamp and a band can each be 12 dB, so a curve can
 * still exceed this and will be clamped rather than rejected.
 */
private const val MAX_GAIN_DB = 12.0f
