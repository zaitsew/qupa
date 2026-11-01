package com.fabriquefutur.qupa.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SettingsAndNetworkTest {
    @Test fun categoriesRoundTripAndIgnoreJunk() {
        assertEquals("9,12,21", SettingsCodec.encodeCategories(setOf(21, 9, 12)))
        assertEquals(setOf(9, 21), SettingsCodec.decodeCategories("9, x, 21,999,,"))
        assertEquals(emptySet<Int>(), SettingsCodec.decodeCategories(null))
    }

    @Test fun difficultyRoundTrips() {
        assertEquals("any", SettingsCodec.encodeDifficulty(null))
        assertNull(SettingsCodec.decodeDifficulty("any"))
        assertEquals(Difficulty.HARD, SettingsCodec.decodeDifficulty(SettingsCodec.encodeDifficulty(Difficulty.HARD)))
    }

    @Test fun staleSettingsSnapToOfferedValues() {
        val n = GameSettings(questions = 13, answerSeconds = 99, categories = setOf(9, 500)).normalized()
        assertEquals(15, n.questions)
        assertEquals(30, n.answerSeconds)
        assertEquals(setOf(9), n.categories)
    }

    @Test fun roomCodesAreFourDigits() {
        repeat(200) { assertTrue(RoomCode.isValid(RoomCode.generate(Random(it)))) }
        assertEquals("0000".length, RoomCode.generate(Random(0)).length)
    }

    @Test fun roomCodeMustMatchExactly() {
        assertTrue(RoomCode.matches("0042", "0042"))
        assertFalse(RoomCode.matches("0042", "42"))
        assertFalse(RoomCode.matches("0042", null))
        assertFalse(RoomCode.matches("0042", "0043"))
        assertFalse(RoomCode.isValid("12a4"))
        assertFalse(RoomCode.isValid("12345"))
    }

    @Test fun onlyPrivateIPv4AddressesAreOffered() {
        assertTrue(LanAddress.isPrivateIPv4("192.168.1.20"))
        assertTrue(LanAddress.isPrivateIPv4("10.0.0.5"))
        assertTrue(LanAddress.isPrivateIPv4("172.16.0.1"))
        assertTrue(LanAddress.isPrivateIPv4("172.31.255.1"))
        assertFalse(LanAddress.isPrivateIPv4("172.32.0.1"))
        assertFalse(LanAddress.isPrivateIPv4("169.254.3.4"))
        assertFalse(LanAddress.isPrivateIPv4("127.0.0.1"))
        assertFalse(LanAddress.isPrivateIPv4("8.8.8.8"))
        assertFalse(LanAddress.isPrivateIPv4("fe80::1"))
        assertFalse(LanAddress.isPrivateIPv4("192.168.1"))
    }

    @Test fun pickTakesTheFirstPrivateAddress() {
        assertEquals("192.168.0.9", LanAddress.pick(listOf("169.254.1.1", "192.168.0.9", "10.1.1.1")))
        assertNull(LanAddress.pick(listOf("169.254.1.1", "8.8.4.4")))
        assertEquals("http://192.168.0.9:8080/?r=0042", LanAddress.joinUrl("192.168.0.9", 8080, "0042"))
    }
}
