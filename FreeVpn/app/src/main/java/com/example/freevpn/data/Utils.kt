package com.example.freevpn.data

fun flagEmoji(countryCode: String): String =
    if (countryCode.length != 2) "🌐"
    else countryCode.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

/** Lowest measured latency wins; servers that failed the probe are ignored. */
fun bestServer(servers: List<Server>): Server? =
    servers.filter { it.latencyMs != null }.minByOrNull { it.latencyMs!! }

fun filterServers(servers: List<Server>, query: String): List<Server> {
    val q = query.trim().lowercase()
    return if (q.isEmpty()) servers
    else servers.filter { it.country.lowercase().contains(q) || it.city.lowercase().contains(q) || it.countryCode.lowercase() == q }
}

fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** True when the IP the API sees is one of the VPN server's addresses. */
fun exitMatches(seenIp: String, exitIps: Set<String>) = seenIp in exitIps
