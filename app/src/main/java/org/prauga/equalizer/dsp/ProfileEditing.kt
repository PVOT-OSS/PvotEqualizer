package org.prauga.equalizer.dsp

import org.prauga.equalizer.domain.EqProfile

/**
 * Sets what is heard at a band's frequency, which is the value the chart draws at its handle.
 *
 * A bell contributes exactly its own gain at its centre, so the neighbours' share can be measured
 * once and subtracted; the stored band gain is therefore `target - neighbours`.
 */
fun EqProfile.withTargetGain(
    bandId: Int,
    targetDb: Double,
    sampleRateHz: Double = 48_000.0
): EqProfile {
    val band = bands.firstOrNull { it.id == bandId } ?: return this
    val heard = ProfileResponse(this, sampleRateHz).dbAt(band.frequencyHz)
    val neighbours = heard - band.gainDb
    val gain = (targetDb - neighbours).coerceIn(EqProfile.MIN_GAIN_DB, EqProfile.MAX_GAIN_DB)
    return copy(bands = bands.map { if (it.id == bandId) it.copy(gainDb = gain) else it })
}
