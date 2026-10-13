package com.oc.catemoji.catoc.data.model.custom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerTransformTest {
    @Test
    fun defaultTransformIsDefault() {
        assertTrue(LayerTransform().isDefault())
    }

    @Test
    fun reversingScaleStepsReturnsToDefault() {
        for (direction in listOf(-1f, 1f)) {
            for (steps in listOf(1, 3, 10, 14)) {
                var scale = 1f
                repeat(steps) { scale += direction * 0.05f }
                repeat(steps) { scale -= direction * 0.05f }

                assertTrue(
                    "Scale $scale should be default after reversing $steps steps ($direction)",
                    LayerTransform(scale = scale).isDefault()
                )
            }
        }
    }

    @Test
    fun repeatedScaleRoundTripsRemainDefault() {
        var scale = 1f
        repeat(100) {
            repeat(5) { scale += 0.05f }
            repeat(5) { scale -= 0.05f }
            assertTrue(LayerTransform(scale = scale).isDefault())
        }
    }

    @Test
    fun oneScaleStepRequiresReset() {
        assertFalse(LayerTransform(scale = 1f + 0.05f).isDefault())
        assertFalse(LayerTransform(scale = 1f - 0.05f).isDefault())
    }

    @Test
    fun restoredScaleStillRequiresResetForOtherTransforms() {
        val scale = (1f + 0.05f) - 0.05f
        assertFalse(LayerTransform(scale = scale, translationX = 20f).isDefault())
        assertFalse(LayerTransform(scale = scale, translationY = -20f).isDefault())
        assertFalse(LayerTransform(scale = scale, rotation = 5f).isDefault())
    }
}
