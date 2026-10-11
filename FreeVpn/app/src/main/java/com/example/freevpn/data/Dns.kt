package com.example.freevpn.data

/** servers == null means "use the resolver provided by the VPN server". All queries still go through the tunnel. */
data class DnsOption(val id: String, val name: String, val servers: String?, val note: String)

val DNS_OPTIONS = listOf(
    DnsOption("server", "Server default (recommended)", null, "Resolver run by the VPN server"),
    DnsOption("cloudflare", "Cloudflare 1.1.1.1", "1.1.1.1, 1.0.0.1", "Fast, privacy-focused"),
    DnsOption("google", "Google Public DNS", "8.8.8.8, 8.8.4.4", "Highly available"),
    DnsOption("adguard", "AdGuard DNS", "94.140.14.14, 94.140.15.15", "Blocks ads and trackers"),
    DnsOption("quad9", "Quad9", "9.9.9.9, 149.112.112.112", "Blocks known malicious domains"),
)
