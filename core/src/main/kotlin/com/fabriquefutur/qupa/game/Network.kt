package com.fabriquefutur.qupa.game

import kotlin.random.Random

object RoomCode {
    /** Four digits, zero-padded: "0042". */
    fun generate(random: Random = Random.Default): String = random.nextInt(0, 10_000).toString().padStart(4, '0')

    fun isValid(code: String?): Boolean = code != null && code.length == 4 && code.all { it in '0'..'9' }

    /** True only for the exact code; a missing or different code is rejected. */
    fun matches(expected: String, given: String?): Boolean = given != null && given == expected
}

/** Which IPv4 addresses a phone on the same Wi-Fi can reach. */
object LanAddress {
    /** RFC 1918 private ranges. Loopback, link-local (169.254/16) and public addresses are not. */
    fun isPrivateIPv4(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val b = parts.map { it.toIntOrNull() ?: return false }
        if (b.any { it !in 0..255 }) return false
        return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168)
    }

    /** The first private address, or null when the TV has none (no Wi-Fi, no Ethernet). */
    fun pick(candidates: List<String>): String? = candidates.firstOrNull { isPrivateIPv4(it) }

    fun joinUrl(host: String, port: Int, roomCode: String): String = "http://$host:$port/?r=$roomCode"
}
