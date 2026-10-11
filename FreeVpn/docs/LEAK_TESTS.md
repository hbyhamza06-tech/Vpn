# Leak test checklist (run on a real device, connected)
1. IPv4: open a "what is my IP" page; it must show the server's IP.
2. DNS: run a DNS leak test (extended); resolvers must be yours, not the carrier's/Wi-Fi's.
3. IPv6: visit an IPv6 test page; it must show no IPv6 address or the server's, never your ISP's.
4. WebRTC: test in a browser; no local/ISP public IP exposed.
5. Network switch: toggle Wi-Fi to mobile data while connected; repeat steps 1-3 after it recovers.
6. Kill switch: enable system "Block connections without VPN", disconnect the server side, confirm traffic stops.
7. Disconnect: verify your real IP returns and the key icon is gone.
