package com.example.freevpn.data

import com.example.freevpn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Talks only to YOUR provisioning API. No third-party or scraped endpoints. */
class ApiClient(private val base: String = BuildConfig.API_BASE) {

    suspend fun servers(): List<Server> = withContext(Dispatchers.IO) {
        val arr = JSONArray(request("GET", "$base/v1/servers", null))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Server(o.getString("id"), o.getString("country"), o.getString("countryCode"),
                o.optString("city"), o.getString("probeUrl"))
        }
    }

    suspend fun registerPeer(serverId: String, publicKey: String): PeerConfig = withContext(Dispatchers.IO) {
        val body = JSONObject().put("publicKey", publicKey).toString()
        val o = JSONObject(request("POST", "$base/v1/servers/$serverId/peer", body))
        PeerConfig(o.getString("address"), o.getString("dns"), o.getString("serverPublicKey"), o.getString("endpoint"))
    }

    /** The public IP your provisioning API sees for this device (own endpoint, no third-party IP service). */
    suspend fun myIp(): String = withContext(Dispatchers.IO) { JSONObject(request("GET", "$base/v1/ip", null)).getString("ip") }

    /** HTTPS round-trip time to the server's probe URL; null if unreachable. */
    suspend fun probe(server: Server): Server = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        val ms = try {
            val c = URL(server.probeUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 3000; c.readTimeout = 3000; c.requestMethod = "HEAD"
            c.responseCode; c.disconnect()
            (System.nanoTime() - start) / 1_000_000
        } catch (e: Exception) { null }
        server.copy(latencyMs = ms)
    }

    private fun request(method: String, url: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/json")
        if (body != null) {
            c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        if (c.responseCode !in 200..299) throw java.io.IOException("Server error ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
