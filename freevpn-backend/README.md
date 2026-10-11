# FreeVPN backend

Stdlib-only Python provisioning API + WireGuard node setup for the Android app.
Unit tests pass locally (`python3 -m unittest`). The shell script and `wg` calls were
NOT run against a real server in my environment: test on a throwaway VPS first.

## 1. Each exit node (VPS in each country)
Check your host's terms allow VPN/relay use, then:
    sudo ./deploy/setup_node.sh        # prints the server public key
Open UDP 51820. Serve HTTPS 200 at /health (nginx) for latency probes.

## 2. API host
    sudo useradd -r freevpn; sudo install -d /opt/freevpn /etc/freevpn
    sudo cp server.py /opt/freevpn/; sudo cp config.example.json /etc/freevpn/config.json   # edit it
    sudo cp deploy/freevpn-api.service /etc/systemd/system/; sudo systemctl enable --now freevpn-api
    # nginx: deploy/nginx.conf + certbot
- Same host as the node: leave `"ssh": null` and give the user `wg` rights (e.g. sudoers for `wg`, or run the unit as root and drop hardening).
- Different host: set `"ssh": "user@node"` with a key-only account allowed to run `wg set/show`.

## 3. App
Set `VPN_API_BASE=https://api.example.com` in the Android project's `gradle.properties`.

## Behavior
- GET /v1/ip returns the client IP the API sees (used by the app's connection check).
- Re-registering the same public key returns the same IP; only new keys count toward the per-IP limit (default 10/hour).
- Peers are re-applied on startup and removed after 14 days with no handshake.
- IPv4 only: clients route `::/0` into the tunnel but get no v6 address, so v6 is blackholed (no leak).

## Limits to know
- The app has no secret, so anyone can call `/peer`. Rate limits slow abuse, they don't stop it. Add Play Integrity attestation before a large public launch.
- Single API instance, sqlite: fine for thousands of peers, not millions.
