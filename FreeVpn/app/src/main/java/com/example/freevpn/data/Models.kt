package com.example.freevpn.data

data class Server(
    val id: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val probeUrl: String,
    val latencyMs: Long? = null,
)

/** Response of POST /v1/servers/{id}/peer — see docs/BACKEND.md */
data class PeerConfig(
    val address: String,        // e.g. 10.8.0.5/32 (and optionally ", fd00::5/128")
    val dns: String,            // e.g. 10.8.0.1
    val serverPublicKey: String,
    val endpoint: String,       // host:port
)

sealed interface ConnState {
    data object Disconnected : ConnState
    data class Connecting(val message: String = "Connecting…") : ConnState
    data class Connected(val since: Long, val server: Server) : ConnState
    data class Error(val message: String) : ConnState
}

data class Traffic(val rxBytes: Long = 0, val txBytes: Long = 0, val rxBps: Long = 0, val txBps: Long = 0)

enum class ThemeMode { System, Light, Dark }
