package com.worddaily.worddaily_video_exporter

import kotlin.math.max
import kotlin.math.min

/** Display dimensions, after applying the video's rotation metadata. */
internal data class DisplayDimensions(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Video dimensions must be positive." }
    }

    val orientation: VideoOrientation
        get() {
            // Allow a small tolerance for near-square video formats.
            if (min(width, height).toDouble() / max(width, height) >= 0.90) {
                return VideoOrientation.SQUARE
            }
            return if (height > width) VideoOrientation.PORTRAIT else VideoOrientation.LANDSCAPE
        }
}

internal enum class VideoOrientation(val wireValue: String) {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape"),
    SQUARE("square")
}

internal object VideoDimensions {
    fun displayed(width: Int, height: Int, rotationDegrees: Int): DisplayDimensions {
        require(width > 0 && height > 0) { "Video dimensions must be positive." }
        val rotation = ((rotationDegrees % 360) + 360) % 360
        require(rotation % 90 == 0) { "Unsupported video rotation: $rotationDegrees." }
        return if (rotation == 90 || rotation == 270) {
            DisplayDimensions(height, width)
        } else {
            DisplayDimensions(width, height)
        }
    }
}
