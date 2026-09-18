package dev.vimal.utl.core.domain.model

/**
 * Platform loudness normalization presets following EBU R128 / ITU-R BS.1770-4.
 * All values in LUFS (Loudness Units Full Scale).
 * True peak in dBTP.
 *
 * List is designed to be easily extended — add new entries here only.
 */
enum class LoudnessPreset(
    val label: String,
    val targetLufs: Float,
    val truePeakDbtp: Float,
) {
    YOUTUBE(label = "YouTube", targetLufs = -14f, truePeakDbtp = -1f),
    TIKTOK(label = "TikTok", targetLufs = -14f, truePeakDbtp = -1f),
    INSTAGRAM(label = "Instagram", targetLufs = -14f, truePeakDbtp = -1f),
    WHATSAPP_STATUS(label = "WhatsApp Status", targetLufs = -16f, truePeakDbtp = -1f),
    SPOTIFY(label = "Spotify", targetLufs = -14f, truePeakDbtp = -1f),
    CUSTOM(label = "Custom", targetLufs = -14f, truePeakDbtp = -1f), // overridden by user input
    ;

    companion object {
        /** All presets except CUSTOM, for display in preset picker. */
        val platformPresets: List<LoudnessPreset>
            get() = entries.filter { it != CUSTOM }
    }
}
