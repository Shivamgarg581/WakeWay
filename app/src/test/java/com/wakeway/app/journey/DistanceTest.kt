package com.wakeway.app.journey

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {
    @Test
    fun samePointIsZero() {
        assertEquals(0.0, Distance.meters(25.0, 75.0, 25.0, 75.0), 0.0001)
    }

    @Test
    fun oneDegreeLatitudeIsApproximately111Km() {
        val meters = Distance.meters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, meters, 1_000.0)
    }

    @Test
    fun crossingDateLineUsesShortestArc() {
        val meters = Distance.meters(0.0, 179.9, 0.0, -179.9)
        assertEquals(22_239.0, meters, 1_500.0)
    }
}
