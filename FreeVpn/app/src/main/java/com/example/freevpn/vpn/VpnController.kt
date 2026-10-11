package com.example.freevpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.example.freevpn.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VpnController(
    ctx: Context,
    private val provider: VpnProvider,
    private val prefs: Prefs,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state
    private val _traffic = MutableStateFlow(Traffic())
    val traffic: StateFlow<Traffic> = _traffic

    private var wanted: Server? = null      // what the user asked for; null = user disconnected
    private var job: Job? = null
    private var statsJob: Job? = null

    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {   // Wi-Fi <-> mobile switch, or coming back online
                scope.launch {
                    val s = wanted ?: return@launch
                    if (prefs.autoReconnect && _state.value is ConnState.Error) connect(s)
                }
            }
            override fun onLost(network: Network) {
                scope.launch { if (wanted != null && _state.value is ConnState.Connected) _state.value = ConnState.Connecting("Waiting for network…") }
            }
        })
    }

    fun connect(server: Server) {
        wanted = server; prefs.lastServerId = server.id
        job?.cancel(); statsJob?.cancel()
        job = scope.launch {
            _state.value = ConnState.Connecting()
            var attempt = 0
            while (true) {
                try {
                    provider.up(server)
                    _state.value = ConnState.Connected(System.currentTimeMillis(), server)
                    startStats(); return@launch
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    attempt++
                    if (!prefs.autoReconnect || attempt >= 3) {
                        provider.down()
                        _state.value = ConnState.Error(friendly(e)); return@launch
                    }
                    _state.value = ConnState.Connecting("Retrying ($attempt/3)…")
                    delay(1500L * attempt)
                }
            }
        }
    }

    fun exitHost() = provider.exitHost()

    fun disconnect() {
        wanted = null; job?.cancel(); statsJob?.cancel()
        scope.launch { provider.down(); _state.value = ConnState.Disconnected; _traffic.value = Traffic() }
    }

    private fun startStats() {
        statsJob = scope.launch {
            var last = Traffic()
            while (isActive) {
                delay(1000)
                val c = provider.counters() ?: continue
                val t = Traffic(c.first, c.second, (c.first - last.rxBytes).coerceAtLeast(0), (c.second - last.txBytes).coerceAtLeast(0))
                _traffic.value = t; last = t
            }
        }
    }

    private fun friendly(e: Exception) = when (e) {
        is java.net.UnknownHostException -> "No internet connection or server not found."
        is java.net.SocketTimeoutException -> "The server took too long to respond."
        else -> e.message ?: "Connection failed."
    }
}
