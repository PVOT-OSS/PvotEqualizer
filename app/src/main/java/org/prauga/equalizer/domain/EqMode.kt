package org.prauga.equalizer.domain

/**
 * How much bandwidth the editor offers.
 *
 * @param bandLimit The most bands a profile may hold in this mode
 */
enum class EqMode(val bandLimit: Int) {
    /** Six bands: the shape most people expect from a phone equalizer, and all they usually need. */
    SIMPLE(6),

    /** Up to ten bands, for carving a specific problem out of a curve. */
    ADVANCED(10)
}
