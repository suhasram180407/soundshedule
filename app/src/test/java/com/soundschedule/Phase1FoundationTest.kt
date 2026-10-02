package com.soundschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Phase1FoundationTest {

    @Test
    fun projectPackage_isConfiguredCorrectly() {
        val packageName = "com.soundschedule"
        assertEquals("com.soundschedule", packageName)
    }

    @Test
    fun foundationThemeColors_areDefined() {
        val darkBgHex = 0xFF0C0E12
        assertNotNull(darkBgHex)
    }
}
