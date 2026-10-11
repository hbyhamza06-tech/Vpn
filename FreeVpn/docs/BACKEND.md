# Provisioning API contract (you implement this)

The app never receives a shared private key. It sends its own public key and gets back a tunnel address.

## GET /v1/servers
```json
[{"id":"de1","country":"Germany","countryCode":"DE","city":"Frankfurt","probeUrl":"https://de1.example.com/health"}]
```
## POST /v1/servers/{id}/peer
Request: `{"publicKey":"<base64 client public key>"}`
Response:
```json
{"address":"10.8.0.5/32","dns":"10.8.0.1","serverPublicKey":"<base64>","endpoint":"de1.example.com:51820"}
```
Implementation outline (per server): allocate the next free IP, then run
`wg set wg0 peer <publicKey> allowed-ips 10.8.0.5/32`, and persist it. Re-registering the same key must return the same IP.

## Server hardening
- IP forwarding + NAT (`iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o eth0 -j MASQUERADE`); add `ip6tables` equivalents if you hand out IPv6, otherwise IPv6 is blackholed (safe, no leak).
- Run a DNS resolver bound to the tunnel address (e.g. unbound) and point `dns` at it.
- Rate-limit `/peer`, cap peers per IP, expire idle peers, and consider Play Integrity attestation to stop abuse of the free tier.
- Serve the API over HTTPS only (the app blocks cleartext by default).
