package com.worddaily.worddaily_video_exporter

/**
 * Clockwise: top-left, top-right, bottom-right, bottom-left.
 * The watermark stays fixed in each corner and is invisible when changing corners.
 * Times are presentation timestamps, not wall-clock time.
 */
internal object CornerSchedule {
    private const val HOLD_US = 6_000_000L
    private const val FADE_US = 200_000L

    fun index(presentationTimeUs: Long): Int =
        ((presentationTimeUs.coerceAtLeast(0) / HOLD_US) % 4).toInt()

    fun anchor(presentationTimeUs: Long): Pair<Float, Float> = when (index(presentationTimeUs)) {
        0 -> -1f to 1f
        1 -> 1f to 1f
        2 -> 1f to -1f
        else -> -1f to -1f
    }

    /**
     * Fade out before a corner switch, switch while fully invisible, then fade
     * back in at the new corner. No watermark slides across the video.
     */
    fun alphaScale(presentationTimeUs: Long): Float {
        val timeUs = presentationTimeUs.coerceAtLeast(0)
        val phaseUs = timeUs % HOLD_US
        // Show the watermark immediately on the first frame.
        if (timeUs >= HOLD_US && phaseUs < FADE_US) {
            return phaseUs.toFloat() / FADE_US
        }
        if (phaseUs > HOLD_US - FADE_US) {
            return (HOLD_US - phaseUs).toFloat() / FADE_US
        }
        return 1f
    }
}
