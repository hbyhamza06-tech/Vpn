package com.example.freevpn

import com.example.freevpn.data.*
import org.junit.Assert.*
import org.junit.Test

class UtilsTest {
    private fun s(id: String, name: String, cc: String, ms: Long?) = Server(id, name, cc, "", "https://x", ms)

    @Test fun flagEmojiBuildsRegionalIndicators() = assertEquals("\uD83C\uDDE9\uD83C\uDDEA", flagEmoji("de"))
    @Test fun flagEmojiFallsBack() = assertEquals("🌐", flagEmoji("XYZ"))
    @Test fun bestServerPicksLowestLatencyAndIgnoresFailed() {
        val l = listOf(s("a", "A", "us", 90), s("b", "B", "de", 30), s("c", "C", "fr", null))
        assertEquals("b", bestServer(l)?.id)
    }
    @Test fun bestServerNullWhenAllFailed() = assertNull(bestServer(listOf(s("a", "A", "us", null))))
    @Test fun filterMatchesCountryAndCode() {
        val l = listOf(s("a", "Germany", "de", 1), s("b", "France", "fr", 1))
        assertEquals(listOf("a"), filterServers(l, "germ").map { it.id })
        assertEquals(listOf("b"), filterServers(l, "FR").map { it.id })
        assertEquals(2, filterServers(l, "").size)
    }
    @Test fun formatters() {
        assertEquals("01:01:01", formatDuration(3_661_000))
        assertEquals("1.0 KB", formatBytes(1024))
    }
}

class DnsOptionsTest {
    @Test fun idsUniqueAndServerDefaultFirst() {
        assertEquals(DNS_OPTIONS.size, DNS_OPTIONS.map { it.id }.toSet().size)
        assertEquals("server", DNS_OPTIONS.first().id); assertNull(DNS_OPTIONS.first().servers)
        assertTrue(DNS_OPTIONS.drop(1).all { !it.servers.isNullOrBlank() })
    }
}

class ExitCheckTest {
    @Test fun matchesWhenSeenIpIsServerIp() {
        assertTrue(exitMatches("203.0.113.7", setOf("203.0.113.7", "203.0.113.8")))
        assertFalse(exitMatches("198.51.100.1", setOf("203.0.113.7")))
    }
}
