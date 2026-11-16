package com.kryptex.vault.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveFolderTest {
    @Test
    fun buildsRootAndSubFolders() {
        assertEquals("Pictures", buildSaveFolder("Pictures", ""))
        assertEquals("DCIM/Trips/2026", buildSaveFolder("DCIM", " Trips // 2026/ "))
    }

    @Test
    fun refusesEscapesAndForbiddenCharacters() {
        assertNull(buildSaveFolder("Pictures", ".."))
        assertNull(buildSaveFolder("Pictures", "a/../b"))
        assertNull(buildSaveFolder("Pictures", "a:b"))
        assertNull(buildSaveFolder("Pictures", "a\\b"))
        assertNull(buildSaveFolder("Downloads", ""))
        assertNull(buildSaveFolder("Pictures", "x".repeat(65)))
    }

    @Test
    fun splitsStoredFolders() {
        assertEquals("Pictures" to "", splitSaveFolder("Pictures"))
        assertEquals("DCIM" to "Trips/2026", splitSaveFolder("DCIM/Trips/2026"))
        assertEquals("Pictures" to "", splitSaveFolder("Somewhere/else"))
    }
}
