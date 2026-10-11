package com.example.freevpn.vpn

import com.example.freevpn.data.Server

/** Swap this implementation to move to a different provider SDK (e.g. AdGuard white-label) without touching the UI. */
interface VpnProvider {
    /** Suspends until the tunnel is actually established (handshake done). Throws on failure/timeout. */
    suspend fun up(server: Server)
    suspend fun down()
    /** Cumulative (rx, tx) bytes, or null if the tunnel is not up. */
    /** Hostname/IP of the endpoint of the active tunnel, or null. */
    fun exitHost(): String?
    fun counters(): Pair<Long, Long>?
}
