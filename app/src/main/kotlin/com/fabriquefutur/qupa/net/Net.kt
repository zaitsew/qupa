package com.fabriquefutur.qupa.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.fabriquefutur.qupa.game.LanAddress
import com.fabriquefutur.qupa.server.WebAssets
import java.net.Inet4Address

/** The TV's address on the home network, or null when it has no Wi-Fi or Ethernet. */
class NetworkAddress(context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun find(): String? {
        val manager = cm ?: return null
        val active = manager.activeNetwork
        @Suppress("DEPRECATION")
        val ordered = listOfNotNull(active) + manager.allNetworks.filter { it != active }
        val candidates = ordered
            .filter { isLocalTransport(manager, it) }
            .flatMap { ipv4Addresses(manager, it) }
        return LanAddress.pick(candidates)
    }

    private fun isLocalTransport(manager: ConnectivityManager, network: Network): Boolean {
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun ipv4Addresses(manager: ConnectivityManager, network: Network): List<String> =
        manager.getLinkProperties(network)?.linkAddresses.orEmpty()
            .mapNotNull { (it.address as? Inet4Address)?.hostAddress }
}

/** Serves the phone page from the APK: the server asks for "index.html", the file is `web/index.html`. */
class AssetsWeb(private val context: Context) : WebAssets {
    override fun read(path: String): ByteArray? {
        if (path.contains("..") || path.startsWith("/")) return null
        return try {
            context.applicationContext.assets.open("web/$path").use { it.readBytes() }
        } catch (_: java.io.IOException) {
            null
        }
    }
}
