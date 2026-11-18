package com.worddaily.worddaily_video_exporter

/** Clockwise: top-left, top-right, bottom-right, bottom-left; video time, not wall time. */
internal object CornerSchedule {
    fun index(presentationTimeUs: Long): Int =
        ((presentationTimeUs.coerceAtLeast(0) / 6_000_000L) % 4).toInt()
    fun anchor(presentationTimeUs: Long): Pair<Float, Float> = when (index(presentationTimeUs)) {
        0 -> -1f to 1f
        1 -> 1f to 1f
        2 -> 1f to -1f
        else -> -1f to -1f
    }
}
