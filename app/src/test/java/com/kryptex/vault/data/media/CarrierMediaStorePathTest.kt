package com.kryptex.vault.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.TimeZone

class CarrierMediaStorePathTest {
    @Test
    fun foldersInsideThePhotoRootsAreAccepted() {
        assertEquals("Pictures/", CarrierMediaStore.relativePath("Pictures"))
        assertEquals("Pictures/", CarrierMediaStore.relativePath(" /Pictures/ "))
        assertEquals("DCIM/Camera/", CarrierMediaStore.relativePath("DCIM/Camera"))
        assertEquals("Pictures/Trips/2026/", CarrierMediaStore.relativePath("Pictures/Trips/2026"))
    }

    @Test
    fun foldersOutsideThePhotoRootsOrWithDotDotAreRefused() {
        for (bad in listOf("", "   ", "Download", "Documents/Pictures", "Pictures/../Download", "Pictures/..", "../Pictures", "/")) {
            assertThrows("'$bad'", IllegalArgumentException::class.java) { CarrierMediaStore.relativePath(bad) }
        }
    }

    @Test
    fun defaultNamesLookLikeACamera() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            // 2026-01-15 18:30:42 UTC
            assertEquals("IMG_20260115_183042.png", CarrierMediaStore.defaultName(1_768_501_842_000L))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun sha256MatchesTheKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CarrierMediaStore.sha256Hex("abc".toByteArray()),
        )
    }
}
