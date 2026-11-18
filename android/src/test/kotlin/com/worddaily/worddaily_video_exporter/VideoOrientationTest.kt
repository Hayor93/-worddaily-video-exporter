package com.worddaily.worddaily_video_exporter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VideoOrientationTest {
    @Test fun portraitLandscapeAndSquare() {
        assertEquals(VideoOrientation.PORTRAIT, VideoDimensions.displayed(1080, 1920, 0).orientation)
        assertEquals(VideoOrientation.LANDSCAPE, VideoDimensions.displayed(1920, 1080, 0).orientation)
        assertEquals(VideoOrientation.SQUARE, VideoDimensions.displayed(1080, 1080, 0).orientation)
    }

    @Test fun rotationMetadataChangesDisplayOrientation() {
        assertEquals(VideoOrientation.PORTRAIT, VideoDimensions.displayed(1920, 1080, 90).orientation)
        assertEquals(VideoOrientation.PORTRAIT, VideoDimensions.displayed(1920, 1080, 270).orientation)
        assertEquals(VideoOrientation.LANDSCAPE, VideoDimensions.displayed(1920, 1080, 180).orientation)
        assertEquals(VideoOrientation.PORTRAIT, VideoDimensions.displayed(1920, 1080, -90).orientation)
    }

    @Test fun nearSquareUsesSquareOutro() {
        assertEquals(VideoOrientation.SQUARE, VideoDimensions.displayed(1000, 950, 0).orientation)
        assertEquals(VideoOrientation.PORTRAIT, VideoDimensions.displayed(1000, 890, 0).orientation)
        assertEquals(VideoOrientation.LANDSCAPE, VideoDimensions.displayed(1200, 1000, 0).orientation)
    }

    @Test fun invalidDimensionsAndRotationFail() {
        assertThrows(IllegalArgumentException::class.java) {
            VideoDimensions.displayed(0, 1080, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VideoDimensions.displayed(1080, 1920, 45)
        }
    }
}
