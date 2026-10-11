package com.example.freevpn.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.crypto.KeyPair

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var onboarded: Boolean get() = sp.getBoolean("onboarded", false); set(v) = sp.edit().putBoolean("onboarded", v).apply()
    var autoReconnect: Boolean get() = sp.getBoolean("autoReconnect", true); set(v) = sp.edit().putBoolean("autoReconnect", v).apply()
    var lastServerId: String? get() = sp.getString("lastServer", null); set(v) = sp.edit().putString("lastServer", v).apply()
    var dnsId: String get() = sp.getString("dns", "server")!!; set(v) = sp.edit().putString("dns", v).apply()
    var autoConnect: Boolean get() = sp.getBoolean("autoConnect", false); set(v) = sp.edit().putBoolean("autoConnect", v).apply()
    fun favorites(): Set<String> = sp.getStringSet("favs", emptySet())!!.toSet()
    fun toggleFavorite(id: String): Set<String> {
        val n = favorites().let { if (id in it) it - id else it + id }
        sp.edit().putStringSet("favs", n).apply(); return n
    }
    var theme: ThemeMode get() = ThemeMode.valueOf(sp.getString("theme", "System")!!); set(v) = sp.edit().putString("theme", v.name).apply()
}

/** The device generates its own WireGuard key pair; the private key never leaves the device. */
class KeyStorage(ctx: Context) {
    private val sp = EncryptedSharedPreferences.create(
        ctx, "keys",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    fun keyPair(): Pair<String, String> { // (private, public) base64
        val priv = sp.getString("priv", null)
        if (priv != null) return priv to sp.getString("pub", "")!!
        val kp = KeyPair()
        sp.edit().putString("priv", kp.privateKey.toBase64()).putString("pub", kp.publicKey.toBase64()).apply()
        return kp.privateKey.toBase64() to kp.publicKey.toBase64()
    }
}
