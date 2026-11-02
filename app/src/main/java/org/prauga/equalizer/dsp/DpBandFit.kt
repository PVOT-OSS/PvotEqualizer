package org.prauga.equalizer.dsp

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.prauga.equalizer.domain.EqProfile

/**
 * One band as `DynamicsProcessing` wants it: a cutoff and a gain, with no Q.
 *
 * @param cutoffHz The top of this band. The gain holds from the previous band's cutoff up to here.
 * @param gainDb The gain applied flat across that span
 */
data class DpBand(val cutoffHz: Double, val gainDb: Double)

/**
 * A profile squeezed into [DpBand]s, ready for the engine's pre-EQ stage.
 *
 * [dbAt] reproduces the engine's staircase, so the same object can be used to check the fit and,
 * later, to draw what the engine will actually do.
 */
data class DpFit(val bands: List<DpBand>) {

    /** The gain the engine applies at [frequencyHz], by finding the band that covers it. */
    fun dbAt(frequencyHz: Double): Double {
        if (bands.isEmpty()) return 0.0
        var low = 0
        var high = bands.size - 1
        var found = bands.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            if (frequencyHz <= bands[mid].cutoffHz) {
                found = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return bands[found].gainDb
    }
}

/**
 * Fits a parametric curve onto the bands the engine can hold.
 *
 * `DynamicsProcessing` has no Q: its pre-EQ is a set of cutoffs, each with one gain that AOSP applies
 * flat across the FFT bins it spans. A parametric profile therefore has to be approximated, and how
 * well it is approximated is decided by where the cutoffs go.
 *
 * A uniform grid is not good enough. A bell at Q 8 is only about a ninth of an octave wide, so a
 * fixed 1/13-octave grid averages the tip away; that was measured at 3.2 dB low. Instead cutoffs are
 * placed greedily: start from the range edges plus an exact cutoff at every band centre, then
 * repeatedly bisect whichever span fits its single gain worst. Sharp features earn the resolution,
 * flat stretches keep one band.
 *
 * Bisecting rather than splitting at the worst *point* is deliberate: on a steady slope the worst
 * point is always a span's edge, so point-splitting shaves one step off the end at a time and the
 * error barely moves. It measured worse than the uniform grid it replaced before it was changed.
 *
 * The public API clamps the pre-EQ band count at [MAX_BAND_COUNT]; asking for more throws.
 */
object DpBandFit {

    /** The most bands the public pre-EQ API accepts. */
    const val MAX_BAND_COUNT: Int = 128

    /** Bands a fit uses by default. */
    const val DEFAULT_BAND_COUNT: Int = MAX_BAND_COUNT

    /** The bottom of the fitted range. */
    const val LOWEST_HZ: Double = 20.0

    /** The top of the fitted range, below the Nyquist limit of a 44.1 kHz stream. */
    const val HIGHEST_HZ: Double = 22_000.0

    /**
     * Points the curve is sampled on while placing cutoffs: finer than any band spacing, so a band
     * edge can land on the feature it needs to keep.
     */
    private const val GRID_SIZE: Int = 1_024

    /**
     * Fits [profile] onto at most [bandCount] engine bands.
     *
     * @param profile The profile to approximate
     * @param sampleRateHz Sample rate the response is designed at
     * @param bandCount Bands to use, clamped to `2..`[MAX_BAND_COUNT]
     */
    fun fit(
        profile: EqProfile,
        sampleRateHz: Double,
        bandCount: Int = DEFAULT_BAND_COUNT
    ): DpFit {
        val grid = Grid(profile, sampleRateHz)
        val boundaries = grid.placeCutoffs(bandCount.coerceIn(2, MAX_BAND_COUNT))
        return DpFit(
            boundaries.mapIndexed { index, boundary ->
                DpBand(
                    cutoffHz = grid.frequencyAt(boundary),
                    gainDb = grid.meanDb(
                        from = if (index == 0) 0 else boundaries[index - 1] + 1,
                        to = boundary
                    )
                )
            }
        )
    }

    /** The cutoff frequencies [fit] would place, in Hz, ascending. */
    fun cutoffs(
        profile: EqProfile,
        bandCount: Int = DEFAULT_BAND_COUNT,
        sampleRateHz: Double = 48_000.0
    ): List<Double> {
        val grid = Grid(profile, sampleRateHz)
        return grid.placeCutoffs(bandCount.coerceIn(2, MAX_BAND_COUNT)).map(grid::frequencyAt)
    }

    /** A run of grid points that will become one engine band. */
    private class Span(
        val start: Int,
        val end: Int,
        val deviation: Double,
        val splitAt: Int
    )

    /**
     * The curve on a dense log grid, with prefix sums so any span's mean is constant time, and a
     * queue so placing cutoffs never rescans spans that did not change.
     */
    private class Grid(profile: EqProfile, sampleRateHz: Double) {

        private val top = min(HIGHEST_HZ, sampleRateHz * 0.49)

        private val frequencies: DoubleArray

        private val curve: DoubleArray
        private val prefixSums: DoubleArray

        /** Grid indices of the enabled bands' centre frequencies. */
        private val anchors: List<Int>

        /**
         * A band centre keeps its exact frequency rather than the nearest grid point, so the cutoffs
         * the engine receives land where the user put the band.
         */
        private val anchorFrequencies: Map<Int, Double>

        init {
            val step = (top / LOWEST_HZ).pow(1.0 / (GRID_SIZE - 1))
            frequencies = DoubleArray(GRID_SIZE) { index -> LOWEST_HZ * step.pow(index) }

            // One pass over the grid, not one pass per band: this is what keeps a refit inside a
            // frame on a phone.
            curve = ProfileResponse(profile, sampleRateHz).curve(frequencies)

            prefixSums = DoubleArray(GRID_SIZE + 1).also { sums ->
                for (index in 0 until GRID_SIZE) sums[index + 1] = sums[index] + curve[index]
            }

            val centres = profile.bands
                .asSequence()
                .filter { it.enabled }
                .map { it.frequencyHz.coerceIn(LOWEST_HZ, top) }
                .distinct()
                .sorted()
                .toList()
            anchorFrequencies = centres.associateBy { indexOf(it) }
            anchors = anchorFrequencies.keys.sorted()
        }

        /** The frequency of the band boundary at [index]: exact at a band centre, else the grid's. */
        fun frequencyAt(index: Int): Double = anchorFrequencies[index] ?: frequencies[index]

        /** The mean of the curve over grid indices [from]..[to], both inclusive. */
        fun meanDb(from: Int, to: Int): Double {
            if (to < from) return 0.0
            return (prefixSums[to + 1] - prefixSums[from]) / (to - from + 1)
        }

        /**
         * Chooses [count] band boundaries: the range edges, an exact one at every enabled band
         * centre, then repeated bisection of whichever span fits its single gain worst.
         */
        fun placeCutoffs(count: Int): List<Int> {
            val selected = sortedSetOf(0, GRID_SIZE - 1)
            for (anchor in anchors) {
                if (anchor > 0 && anchor < GRID_SIZE - 1) selected.add(anchor)
            }

            val queue = PriorityQueue(compareByDescending<Span> { it.deviation })
            val initial = selected.toIntArray()
            for (index in initial.indices) {
                val start = if (index == 0) -1 else initial[index - 1]
                span(start, initial[index])?.let(queue::add)
            }

            while (selected.size < count) {
                val worst = queue.poll() ?: break
                // A span already flat to within nothing has no use for another band; its slots are
                // better spent elsewhere, so drop it rather than stopping.
                if (worst.deviation <= 0.0) continue
                selected.add(worst.splitAt)
                span(worst.start, worst.splitAt)?.let(queue::add)
                span(worst.splitAt, worst.end)?.let(queue::add)
            }

            // The engine's band count is fixed when the effect is created, so a fit has to return
            // exactly the count it was asked for. When the curve has no error left to chase, keep
            // halving the widest span: its gains are already equal, so nothing changes audibly.
            while (selected.size < count) {
                val ordered = selected.toIntArray()
                var widestStart = -1
                var widest = 0
                for (index in ordered.indices) {
                    val start = if (index == 0) -1 else ordered[index - 1]
                    val width = ordered[index] - start
                    if (width > widest) {
                        widest = width
                        widestStart = start
                    }
                }
                if (widest < 2) break
                selected.add(widestStart + widest / 2)
            }
            return selected.toList()
        }

        /** The span from [start] to [end], with its worst deviation and the point it would split at. */
        private fun span(start: Int, end: Int): Span? {
            if (end - start < 2) return null
            val mean = meanDb(start + 1, end)
            var worst = 0.0
            for (index in (start + 1)..(end - 1)) {
                val deviation = abs(curve[index] - mean)
                if (deviation > worst) worst = deviation
            }
            return Span(start, end, worst, start + (end - start) / 2)
        }

        private fun indexOf(frequencyHz: Double): Int {
            val ratio = frequencyHz.coerceIn(LOWEST_HZ, top) / LOWEST_HZ
            val position = ln(ratio) / ln(top / LOWEST_HZ) * (GRID_SIZE - 1)
            return position.roundToInt().coerceIn(0, GRID_SIZE - 1)
        }
    }
}
