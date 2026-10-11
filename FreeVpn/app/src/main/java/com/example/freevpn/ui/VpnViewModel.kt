package com.example.freevpn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.freevpn.App
import com.example.freevpn.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CheckUi {
    data object Idle : CheckUi
    data object Running : CheckUi
    data class Done(val ok: Boolean, val seenIp: String) : CheckUi
    data class Failed(val message: String) : CheckUi
}

data class ServersUi(val loading: Boolean = true, val error: String? = null, val servers: List<Server> = emptyList())

class VpnViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as App
    val prefs = a.prefs
    val state = a.controller.state
    val traffic = a.controller.traffic

    private val _servers = MutableStateFlow(ServersUi())
    val servers: StateFlow<ServersUi> = _servers
    private val _selected = MutableStateFlow<Server?>(null)   // null = automatic
    val selected: StateFlow<Server?> = _selected
    val theme = MutableStateFlow(prefs.theme)
    val favorites = MutableStateFlow(prefs.favorites())
    val dnsId = MutableStateFlow(prefs.dnsId)
    var autoConnectTried = false
    val check = MutableStateFlow<CheckUi>(CheckUi.Idle)

    /** Asks our own API which IP it sees and compares it with the VPN server's address. IPv4 exit check only. */
    fun runCheck() = viewModelScope.launch {
        if (state.value !is ConnState.Connected) { check.value = CheckUi.Failed("Connect first, then run the check."); return@launch }
        check.value = CheckUi.Running
        try {
            val seen = a.api.myIp()
            val host = a.controller.exitHost() ?: throw IllegalStateException("No active tunnel")
            val ips = withContext(Dispatchers.IO) { java.net.InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.toSet() }
            check.value = CheckUi.Done(exitMatches(seen, ips), seen)
        } catch (e: Exception) {
            check.value = CheckUi.Failed("Check failed: ${e.message ?: "network error"}")
        }
    }

    init { refreshServers() }

    fun refreshServers() = viewModelScope.launch {
        _servers.value = ServersUi(loading = true)
        try {
            val probed = a.api.servers().map { s -> async { a.api.probe(s) } }.awaitAll().sortedBy { it.latencyMs ?: Long.MAX_VALUE }
            _servers.value = ServersUi(false, null, probed)
            _selected.value = probed.firstOrNull { it.id == prefs.lastServerId }
        } catch (e: Exception) {
            _servers.value = ServersUi(false, "Couldn't load servers. Check your connection and try again.")
        }
    }

    fun select(s: Server?) { _selected.value = s }

    /** Called after the system VPN permission dialog has been accepted. */
    fun connect() {
        val target = _selected.value ?: bestServer(_servers.value.servers) ?: return
        a.controller.connect(target)
    }
    fun disconnect() = a.controller.disconnect()
    fun canConnect() = _selected.value != null || bestServer(_servers.value.servers) != null

    fun toggleFavorite(id: String) { favorites.value = prefs.toggleFavorite(id) }
    fun setDns(id: String) { prefs.dnsId = id; dnsId.value = id }   // applies on next connect

    fun setTheme(t: ThemeMode) { prefs.theme = t; theme.value = t }
}
