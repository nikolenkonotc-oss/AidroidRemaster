package app.aidroid.remaster.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PressScaleTest {
    @Test
    fun appliesTheSameAbsoluteShrinkToShortAndExpandedMessages() {
        val shrinkPx = 3f
        val shortHeight = 180f
        val expandedHeight = 2_400f

        val shortScale = fixedDimensionScale(shortHeight, shrinkPx, 1f)
        val expandedScale = fixedDimensionScale(expandedHeight, shrinkPx, 1f)

        assertEquals(shrinkPx, shortHeight * (1f - shortScale), 0.0001f)
        assertEquals(shrinkPx, expandedHeight * (1f - expandedScale), 0.0001f)
    }

    @Test
    fun interpolatesPressDistanceAndReturnsToOriginalSize() {
        val halfPress = fixedDimensionScale(600f, 4f, 0.5f)
        val released = fixedDimensionScale(600f, 4f, 0f)

        assertEquals(2f, 600f * (1f - halfPress), 0.0001f)
        assertEquals(1f, released, 0.0001f)
    }

    @Test
    fun safelyHandlesEmptyDimensionsAndClampsProgress() {
        assertEquals(1f, fixedDimensionScale(0f, 3f, 1f))
        assertEquals(1f, fixedDimensionScale(100f, 3f, -1f))
        assertEquals(0f, fixedDimensionScale(3f, 9f, 1f))
    }
}
