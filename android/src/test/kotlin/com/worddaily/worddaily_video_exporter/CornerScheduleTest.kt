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
    @Test fun visitsAllFourCornersClockwise() {
        assertEquals(-1f to 1f, CornerSchedule.anchor(0))
        assertEquals(1f to 1f, CornerSchedule.anchor(6_000_000))
        assertEquals(1f to -1f, CornerSchedule.anchor(12_000_000))
        assertEquals(-1f to -1f, CornerSchedule.anchor(18_000_000))
    }
}
