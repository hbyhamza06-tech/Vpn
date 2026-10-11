package com.example.freevpn.vpn

import android.content.Context
import com.example.freevpn.data.ApiClient
import com.example.freevpn.data.KeyStorage
import com.example.freevpn.data.Server
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WireGuardProvider(ctx: Context, private val api: ApiClient, private val keys: KeyStorage,
    private val dnsOverride: () -> String? = { null }) : VpnProvider {

    private val backend = GoBackend(ctx.applicationContext)
    @Volatile private var peerKey: Key? = null
    @Volatile private var endpointHost: String? = null

    private val tunnel = object : Tunnel {
        override fun getName() = "freevpn"
        override fun onStateChange(newState: Tunnel.State) {}
    }

    override suspend fun up(server: Server): Unit = withContext(Dispatchers.IO) {
        val (priv, pub) = keys.keyPair()
        val peer = api.registerPeer(server.id, pub)

        val cfg = Config.Builder()
            .setInterface(Interface.Builder()
                .parsePrivateKey(priv)
                .parseAddresses(peer.address)
                .parseDnsServers(dnsOverride() ?: peer.dns)   // DNS queries always go through the tunnel
                .build())
            .addPeer(Peer.Builder()
                .parsePublicKey(peer.serverPublicKey)
                .parseEndpoint(peer.endpoint)
                .parseAllowedIPs("0.0.0.0/0, ::/0") // route IPv4 AND IPv6 through the tunnel (no v6 leak)
                .setPersistentKeepalive(25)
                .build())
            .build()
        peerKey = Key.fromBase64(peer.serverPublicKey)
        endpointHost = peer.endpoint.substringBeforeLast(":")

        backend.setState(tunnel, Tunnel.State.UP, cfg)

        // Never report "connected" until a real handshake has completed.
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline) {
            val hs = backend.getStatistics(tunnel).peer(peerKey!!)?.latestHandshakeEpochMillis() ?: 0L
            if (hs > 0) return@withContext
            delay(500)
        }
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        throw java.io.IOException("Timed out: server did not respond to handshake")
    }

    override suspend fun down(): Unit = withContext(Dispatchers.IO) {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        peerKey = null
    }

    override fun exitHost(): String? = endpointHost

    override fun counters(): Pair<Long, Long>? {
        val k = peerKey ?: return null
        val p = runCatching { backend.getStatistics(tunnel).peer(k) }.getOrNull() ?: return null
        return p.rxBytes() to p.txBytes()
    }
}
