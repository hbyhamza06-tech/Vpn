#!/usr/bin/env python3
"""FreeVPN provisioning API (stdlib only). See README.md.
GET  /v1/servers
POST /v1/servers/<id>/peer   {"publicKey": "<base64>"}
GET  /v1/ip                  (the client IP this API sees; used by the app's connection check)
GET  /health
"""
import base64, ipaddress, json, re, sqlite3, subprocess, sys, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

KEY_RE = re.compile(r"^[A-Za-z0-9+/]{43}=$")


def valid_key(k) -> bool:
    if not isinstance(k, str) or not KEY_RE.match(k):
        return False
    try:
        return len(base64.b64decode(k, validate=True)) == 32
    except Exception:
        return False


class Runner:
    """Runs `wg` locally or over SSH. Arguments are validated before use (keys/IPs/ifnames only)."""
    def __init__(self, ssh=None, dry_run=False):
        self.ssh, self.dry_run = ssh, dry_run

    def wg(self, *args) -> str:
        if self.dry_run:
            return ""
        cmd = ["wg", *args]
        if self.ssh:
            cmd = ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=5", self.ssh, *cmd]
        return subprocess.run(cmd, check=True, capture_output=True, text=True, timeout=10).stdout


class Store:
    def __init__(self, path):
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.lock = threading.Lock()
        self.db.execute("""CREATE TABLE IF NOT EXISTS peers(
            server_id TEXT, public_key TEXT, ip TEXT, created INTEGER,
            PRIMARY KEY(server_id, public_key), UNIQUE(server_id, ip))""")
        self.db.commit()

    def get(self, sid, key):
        r = self.db.execute("SELECT ip FROM peers WHERE server_id=? AND public_key=?", (sid, key)).fetchone()
        return r[0] if r else None

    def used(self, sid):
        return {r[0] for r in self.db.execute("SELECT ip FROM peers WHERE server_id=?", (sid,))}

    def add(self, sid, key, ip):
        self.db.execute("INSERT INTO peers VALUES(?,?,?,?)", (sid, key, ip, int(time.time())))
        self.db.commit()

    def remove(self, sid, key):
        self.db.execute("DELETE FROM peers WHERE server_id=? AND public_key=?", (sid, key))
        self.db.commit()

    def all(self, sid):
        return self.db.execute("SELECT public_key, ip, created FROM peers WHERE server_id=?", (sid,)).fetchall()


class RateLimited(Exception): ...
class Full(Exception): ...


class Service:
    def __init__(self, cfg, store=None, dry_run=False):
        self.cfg = cfg
        self.servers = {s["id"]: s for s in cfg["servers"]}
        self.runners = {s["id"]: Runner(s.get("ssh"), dry_run) for s in cfg["servers"]}
        self.store = store or Store(cfg["db"])
        self.hits = {}
        self.limit = cfg.get("rate_limit_per_hour", 10)
        self.lock = threading.Lock()

    def public_servers(self):
        keys = ("id", "country", "countryCode", "city", "probeUrl")
        return [{k: s.get(k, "") for k in keys} for s in self.cfg["servers"]]

    def _rate_ok(self, client):
        now = time.time()
        h = [t for t in self.hits.get(client, []) if now - t < 3600]
        if len(h) >= self.limit:
            self.hits[client] = h
            return False
        h.append(now); self.hits[client] = h
        return True

    def register(self, sid, key, client):
        s = self.servers[sid]
        with self.lock:
            ip = self.store.get(sid, key)
            if ip is None:                      # only NEW registrations count against the limit
                if not self._rate_ok(client):
                    raise RateLimited()
                net = ipaddress.ip_network(s["subnet"])
                used = self.store.used(sid)
                ip = next((str(h) for h in list(net.hosts())[1:] if str(h) not in used), None)  # .1 is the server
                if ip is None:
                    raise Full()
                self.runners[sid].wg("set", s.get("interface", "wg0"), "peer", key, "allowed-ips", f"{ip}/32")
                self.store.add(sid, key, ip)
        return {"address": f"{ip}/32", "dns": s["dns"], "serverPublicKey": s["publicKey"], "endpoint": s["endpoint"]}

    def sync_all(self):
        """Re-apply stored peers (wg runtime state is lost on reboot)."""
        for sid, s in self.servers.items():
            for key, ip, _ in self.store.all(sid):
                try:
                    self.runners[sid].wg("set", s.get("interface", "wg0"), "peer", key, "allowed-ips", f"{ip}/32")
                except Exception as e:
                    print(f"sync {sid} failed: {e}", file=sys.stderr)

    def reap(self, now=None):
        """Remove peers older than the TTL that have not handshaken within the TTL."""
        now = now or time.time(); ttl = self.cfg.get("peer_ttl_days", 14) * 86400
        for sid, s in self.servers.items():
            try:
                out = self.runners[sid].wg("show", s.get("interface", "wg0"), "latest-handshakes")
            except Exception as e:
                print(f"reap {sid} failed: {e}", file=sys.stderr); continue
            hs = {}
            for line in out.splitlines():
                p = line.split()
                if len(p) == 2: hs[p[0]] = int(p[1])
            for key, _, created in self.store.all(sid):
                if now - created > ttl and now - hs.get(key, 0) > ttl:
                    with self.lock:
                        try:
                            self.runners[sid].wg("set", s.get("interface", "wg0"), "peer", key, "remove")
                        except Exception:
                            continue
                        self.store.remove(sid, key)


def make_handler(svc: Service):
    class H(BaseHTTPRequestHandler):
        def log_message(self, *a): pass

        def _json(self, code, obj):
            b = json.dumps(obj).encode()
            self.send_response(code); self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)

        def _client(self):
            if svc.cfg.get("trust_proxy"):
                return self.headers.get("X-Real-IP") or self.client_address[0]
            return self.client_address[0]

        def do_GET(self):
            if self.path == "/health": return self._json(200, {"ok": True})
            if self.path == "/v1/servers": return self._json(200, svc.public_servers())
            if self.path == "/v1/ip": return self._json(200, {"ip": self._client()})
            self._json(404, {"error": "not found"})

        def do_POST(self):
            m = re.fullmatch(r"/v1/servers/([A-Za-z0-9_-]{1,32})/peer", self.path)
            if not m or m.group(1) not in svc.servers: return self._json(404, {"error": "unknown server"})
            n = int(self.headers.get("Content-Length") or 0)
            if n <= 0 or n > 1024: return self._json(400, {"error": "bad body"})
            try:
                key = json.loads(self.rfile.read(n)).get("publicKey")
            except Exception:
                return self._json(400, {"error": "bad json"})
            if not valid_key(key): return self._json(400, {"error": "invalid publicKey"})
            try:
                self._json(200, svc.register(m.group(1), key, self._client()))
            except RateLimited: self._json(429, {"error": "rate limited"})
            except Full: self._json(503, {"error": "server full"})
            except Exception as e:
                print("register error:", e, file=sys.stderr); self._json(502, {"error": "provisioning failed"})
    return H


def main(path):
    cfg = json.load(open(path))
    svc = Service(cfg); svc.sync_all()
    threading.Thread(target=lambda: [(time.sleep(3600), svc.reap()) for _ in iter(int, 1)], daemon=True).start()
    host, port = cfg.get("listen", "127.0.0.1:8080").rsplit(":", 1)
    print(f"listening on {host}:{port}")
    ThreadingHTTPServer((host, int(port)), make_handler(svc)).serve_forever()


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "config.json")
