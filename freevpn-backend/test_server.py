import base64, os, tempfile, unittest
from server import Service, Store, RateLimited, valid_key

K = lambda n: base64.b64encode(bytes([n]) * 32).decode()
CFG = {"db": ":memory:", "rate_limit_per_hour": 3, "peer_ttl_days": 1, "servers": [{
    "id": "t1", "country": "X", "countryCode": "XX", "city": "", "probeUrl": "https://x", "endpoint": "h:51820",
    "publicKey": K(9), "interface": "wg0", "subnet": "10.8.0.0/29", "dns": "10.8.0.1"}]}

def svc(): return Service(CFG, Store(":memory:"), dry_run=True)

class T(unittest.TestCase):
    def test_key_validation(self):
        self.assertTrue(valid_key(K(1))); self.assertFalse(valid_key("abc")); self.assertFalse(valid_key("A" * 44))
        self.assertFalse(valid_key("x; rm -rf /" + "A" * 33))
    def test_allocates_from_dot2_and_is_idempotent(self):
        s = svc(); a = s.register("t1", K(1), "c"); b = s.register("t1", K(2), "c")
        self.assertEqual(a["address"], "10.8.0.2/32"); self.assertEqual(b["address"], "10.8.0.3/32")
        self.assertEqual(s.register("t1", K(1), "c")["address"], "10.8.0.2/32")
        self.assertEqual(a["serverPublicKey"], K(9)); self.assertEqual(a["dns"], "10.8.0.1")
    def test_rate_limit_counts_new_keys_only(self):
        s = svc()
        for i in (1, 2, 3): s.register("t1", K(i), "c")
        s.register("t1", K(1), "c")                 # repeat: allowed
        with self.assertRaises(RateLimited): s.register("t1", K(4), "c")
        s.register("t1", K(4), "other-client")      # different client ok
    def test_reap_removes_stale_only(self):
        s = svc(); s.register("t1", K(1), "c"); s.register("t1", K(2), "d")
        s.store.db.execute("UPDATE peers SET created=1"); s.store.db.commit()
        s.reap(now=10 ** 9)
        self.assertEqual(len(s.store.all("t1")), 0)
    def test_public_list_hides_internals(self):
        self.assertNotIn("publicKey", svc().public_servers()[0])

class IpEndpoint(unittest.TestCase):
    def test_ip_endpoint_honors_proxy_header(self):
        import json, threading, urllib.request
        from http.server import ThreadingHTTPServer
        from server import make_handler
        cfg = dict(CFG, trust_proxy=True)
        s = Service(cfg, Store(":memory:"), dry_run=True)
        h = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(s)); threading.Thread(target=h.serve_forever, daemon=True).start()
        r = urllib.request.Request(f"http://127.0.0.1:{h.server_address[1]}/v1/ip", headers={"X-Real-IP": "203.0.113.7"})
        self.assertEqual(json.loads(urllib.request.urlopen(r).read())["ip"], "203.0.113.7")
        h.shutdown()

if __name__ == "__main__": unittest.main()
