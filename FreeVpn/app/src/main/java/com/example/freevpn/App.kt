package com.example.freevpn

import android.app.Application
import com.example.freevpn.data.ApiClient
import com.example.freevpn.data.DNS_OPTIONS
import com.example.freevpn.data.KeyStorage
import com.example.freevpn.data.Prefs
import com.example.freevpn.vpn.VpnController
import com.example.freevpn.vpn.WireGuardProvider

class App : Application() {
    lateinit var prefs: Prefs; lateinit var api: ApiClient; lateinit var controller: VpnController
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this); api = ApiClient()
        controller = VpnController(this, WireGuardProvider(this, api, KeyStorage(this)) { DNS_OPTIONS.firstOrNull { it.id == prefs.dnsId }?.servers }, prefs)
    }
}
