package com.worddaily.worddaily_video_exporter
import org.junit.Assert.assertEquals
import org.junit.Test
class CornerScheduleTest {
    @Test fun changesOnlyAtSixSecondBoundaries() {
        assertEquals(0, CornerSchedule.index(0))
        assertEquals(0, CornerSchedule.index(5_999_999))
        assertEquals(1, CornerSchedule.index(6_000_000))
        assertEquals(2, CornerSchedule.index(12_000_000))
        assertEquals(3, CornerSchedule.index(18_000_000))
        assertEquals(0, CornerSchedule.index(24_000_000))
        assertEquals(0, CornerSchedule.index(-1))
    }
    @Test fun hidesWatermarkDuringCornerChangeWithoutSliding() {
        assertEquals(1f, CornerSchedule.alphaScale(0), 0.0001f)
        assertEquals(1f, CornerSchedule.alphaScale(5_800_000), 0.0001f)
        assertEquals(0.5f, CornerSchedule.alphaScale(5_900_000), 0.0001f)
        assertEquals(0.005f, CornerSchedule.alphaScale(5_999_000), 0.0001f)
        assertEquals(0f, CornerSchedule.alphaScale(6_000_000), 0.0001f)
        assertEquals(0.5f, CornerSchedule.alphaScale(6_100_000), 0.0001f)
        assertEquals(1f, CornerSchedule.alphaScale(6_200_000), 0.0001f)
        assertEquals(1f, CornerSchedule.alphaScale(10_000_000), 0.0001f)
        assertEquals(0f, CornerSchedule.alphaScale(12_000_000), 0.0001f)
        assertEquals(1f, CornerSchedule.alphaScale(-1), 0.0001f)
        // At the exact switch time, the anchor changes but the overlay is hidden.
        assertEquals(-1f to 1f, CornerSchedule.anchor(5_999_999))
        assertEquals(1f to 1f, CornerSchedule.anchor(6_000_000))
    }

    @Test fun visitsAllFourCornersClockwise() {
        assertEquals(-1f to 1f, CornerSchedule.anchor(0))
        assertEquals(1f to 1f, CornerSchedule.anchor(6_000_000))
        assertEquals(1f to -1f, CornerSchedule.anchor(12_000_000))
        assertEquals(-1f to -1f, CornerSchedule.anchor(18_000_000))
    }
}
