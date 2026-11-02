package org.prauga.equalizer.data

import java.util.UUID
import org.json.JSONObject
import org.prauga.equalizer.domain.EqMode
import org.prauga.equalizer.domain.Preset

/**
 * The stored and shared form of a preset.
 *
 * This is a file format, so it is versioned and forgiving: unknown fields are ignored, missing ones
 * fall back to sensible defaults, and anything unreadable returns null rather than throwing. A preset
 * shared from another device has to be able to arrive here without the app falling over.
 */
object PresetJson {

    const val VERSION = 1
    private const val KIND = "pvot-preset"

    fun encode(preset: Preset): JSONObject = JSONObject().apply {
        put("kind", KIND)
        put("version", VERSION)
        put("id", preset.id)
        put("name", preset.name)
        put("mode", preset.mode.name)
        put("preamp", preset.preampDb)
        put(
            "gains",
            JSONObject().apply {
                preset.gains.forEach { (frequency, gain) -> put(frequency.toString(), gain) }
            }
        )
    }

    fun decode(json: JSONObject): Preset? = runCatching {
        val name = json.optString("name").trim()
        if (name.isEmpty()) return null

        val rawGains = json.optJSONObject("gains") ?: JSONObject()
        val gains = buildMap {
            for (frequency in rawGains.keys()) {
                val hz = frequency.toDoubleOrNull() ?: continue
                if (hz > 0.0) put(hz, rawGains.optDouble(frequency, 0.0))
            }
        }

        Preset(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = name,
            mode = runCatching { EqMode.valueOf(json.optString("mode")) }
                .getOrDefault(EqMode.SIMPLE),
            preampDb = json.optDouble("preamp", 0.0),
            gains = gains
        )
    }.getOrNull()

    /** Pretty-printed so a shared preset is readable if someone opens it as text. */
    fun encodeToString(preset: Preset): String = encode(preset).toString(2)

    fun decodeFromString(text: String): Preset? =
        runCatching { decode(JSONObject(text)) }.getOrNull()
}
