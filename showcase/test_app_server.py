"""Offline security regression tests for the loopback Steam news bridge.

Run with ``python3 -m unittest discover -s showcase -p test_app_server.py -v``.
No test contacts Steam: HTTP tests inject a fake upstream fetcher.
"""

import http.client
import io
import json
import socket
import threading
import time
import unittest
from urllib.parse import parse_qs, urlsplit
from unittest.mock import patch

import app_server


APPID = "570"


def news_document(items=None, appid=570):
    return {
        "appnews": {
            "appid": appid,
            "newsitems": [] if items is None else items,
        }
    }


def json_bytes(value):
    return json.dumps(value).encode("utf-8")


class SteamInputTests(unittest.TestCase):
    def test_canonical_appids_target_only_fixed_steam_endpoint(self):
        for appid in ("1", "570", "4294967295"):
            with self.subTest(appid=appid):
                url = urlsplit(app_server.build_steam_url(appid))
                self.assertEqual((url.scheme, url.netloc), ("https", "api.steampowered.com"))
                self.assertEqual(url.path, "/ISteamNews/GetNewsForApp/v2/")
                self.assertEqual(parse_qs(url.query)["appid"], [appid])
                self.assertFalse(url.fragment)

    def test_rejects_noncanonical_out_of_range_and_url_inputs(self):
        invalid = (
            "", "0", "00", "0570", "4294967296", "9" * 1000,
            "-1", "+570", " 570", "570 ", "570\n", "5.70", "5e2",
            "５７０", "٥٧٠", "https://example.com/", "//127.0.0.1/",
            "570&url=http://127.0.0.1/", "570#fragment", "570%26appid=1",
        )
        for appid in invalid:
            with self.subTest(appid=repr(appid)):
                with self.assertRaises(app_server.BridgeError) as caught:
                    app_server.build_steam_url(appid)
                self.assertEqual(caught.exception.status, 400)


class UpstreamValidationTests(unittest.TestCase):
    def decode(self, payload=None, status=200, content_type="application/json", body=None):
        return app_server.decode_upstream(
            json_bytes(news_document() if payload is None else payload) if body is None else body,
            status,
            content_type,
            APPID,
        )

    def assert_rejected(self, **kwargs):
        with self.assertRaises(app_server.BridgeError) as caught:
            self.decode(**kwargs)
        self.assertGreaterEqual(caught.exception.status, 400)
        self.assertIsInstance(caught.exception.code, str)
        self.assertTrue(caught.exception.code)

    def test_empty_news_is_a_valid_upstream_result(self):
        expected = news_document()
        self.assertEqual(self.decode(payload=expected), expected)

    def test_json_charset_and_fifty_items_are_accepted(self):
        expected = news_document([{"gid": str(i), "title": "News"} for i in range(50)])
        self.assertEqual(self.decode(payload=expected, content_type="application/json; charset=utf-8"), expected)

    def test_redirects_and_non_success_statuses_are_rejected(self):
        for status in (201, 204, 301, 302, 303, 307, 308, 400, 404, 429, 500, 503):
            with self.subTest(status=status):
                self.assert_rejected(status=status)

    def test_non_json_content_types_are_rejected_even_for_json_looking_body(self):
        for content_type in ("", "text/html", "text/plain", "application/octet-stream", "application/jsonp"):
            with self.subTest(content_type=content_type):
                self.assert_rejected(content_type=content_type)

    def test_malformed_json_and_invalid_utf8_are_rejected(self):
        for body in (b"", b"{", b"<html>error</html>", b'{"appnews":', b"\xff", b"{} trailing"):
            with self.subTest(body=body):
                self.assert_rejected(body=body)

    def test_response_size_boundary(self):
        body = json_bytes(news_document())
        at_limit = body + b" " * (262144 - len(body))
        self.assertEqual(self.decode(body=at_limit), news_document())
        self.assert_rejected(body=at_limit + b" ")

    def test_document_requires_matching_appid_and_bounded_news_array(self):
        invalid = (
            [], None, "news", {}, {"appnews": None}, {"appnews": []},
            {"appnews": {"appid": 571, "newsitems": []}},
            {"appnews": {"appid": "570", "newsitems": []}},
            {"appnews": {"appid": 570}},
            {"appnews": {"appid": 570, "newsitems": None}},
            {"appnews": {"appid": 570, "newsitems": {}}},
            news_document([{}] * 51),
        )
        for payload in invalid:
            with self.subTest(payload=payload):
                # Explicit bytes distinguish a JSON null from decode's default fixture.
                self.assert_rejected(body=json_bytes(payload))


class FakeProcess:
    """Subprocess seam that never launches curl or accesses the network."""

    def __init__(self, body=None, metadata=None, returncode=0, block=False):
        self.killed = threading.Event()
        self.block = block
        self.returncode = None if block else returncode
        self.stdout = self if block else io.BytesIO(json_bytes(news_document()) if body is None else body)
        self.stderr = io.BytesIO(
            b"\nBEFORE_ARRIVAL_HTTP\t200\tapplication/json\t0\n" if metadata is None else metadata
        )

    def read(self, _size):
        if not self.killed.wait(2):
            raise AssertionError("transport did not cancel the blocked upstream")
        return b""

    def kill(self):
        self.returncode = -9
        self.killed.set()

    def wait(self, timeout=None):
        return self.returncode

    def poll(self):
        return self.returncode

    def close(self):
        pass


class CurlTransportTests(unittest.TestCase):
    def fetch(self, process, client_alive=lambda: True):
        with patch.object(app_server.subprocess, "Popen", return_value=process) as popen:
            result = app_server.fetch_steam(APPID, client_alive)
        return result, popen

    def test_curl_is_fixed_https_without_shell_redirects_or_tls_bypass(self):
        result, popen = self.fetch(FakeProcess())
        self.assertEqual(result, news_document())
        args, kwargs = popen.call_args
        command = args[0]
        self.assertEqual(command[:2], ["curl", "-q"])
        self.assertEqual(command[-1], app_server.build_steam_url(APPID))
        self.assertEqual(command[command.index("--proto") + 1], "=https")
        self.assertEqual(command[command.index("--max-redirs") + 1], "0")
        self.assertLessEqual(float(command[command.index("--max-time") + 1]), 8)
        self.assertLessEqual(int(command[command.index("--max-filesize") + 1]), 262144)
        self.assertFalse(kwargs.get("shell", False))
        self.assertNotIn("env", kwargs)
        for forbidden in ("--location", "-L", "--location-trusted", "--insecure", "-k"):
            self.assertNotIn(forbidden, command)
        self.assertIn("Cookie:", command)
        self.assertIn("Authorization:", command)

    def test_transport_rejects_redirect_and_failed_tls_metadata(self):
        for metadata in (
            b"BEFORE_ARRIVAL_HTTP\t302\tapplication/json\t0\n",
            b"BEFORE_ARRIVAL_HTTP\t200\tapplication/json\t60\n",
            b"no metadata\n",
            b"BEFORE_ARRIVAL_HTTP\tbogus\tapplication/json\t0\n",
        ):
            with self.subTest(metadata=metadata):
                with self.assertRaises(app_server.BridgeError):
                    self.fetch(FakeProcess(metadata=metadata))

    def test_streaming_byte_cap_kills_upstream(self):
        process = FakeProcess(body=b" " * 262145)
        with self.assertRaises(app_server.BridgeError) as caught:
            self.fetch(process)
        self.assertEqual(caught.exception.code, "upstream_too_large")
        self.assertTrue(process.killed.is_set())

    def test_deadline_kills_blocked_upstream(self):
        process = FakeProcess(block=True)
        with patch.object(app_server, "MAX_SECONDS", 0.01):
            with self.assertRaises(app_server.BridgeError) as caught:
                self.fetch(process)
        self.assertEqual(caught.exception.status, 504)
        self.assertEqual(caught.exception.code, "upstream_timeout")
        self.assertTrue(process.killed.is_set())

    def test_client_disconnect_kills_blocked_upstream(self):
        process = FakeProcess(block=True)
        with self.assertRaises(app_server.BridgeError) as caught:
            self.fetch(process, client_alive=lambda: False)
        self.assertEqual(caught.exception.code, "client_closed")
        self.assertTrue(process.killed.is_set())

    def test_curl_timeout_and_size_errors_are_reported(self):
        for returncode, status, code in ((28, 504, "upstream_timeout"), (63, 502, "upstream_too_large")):
            with self.subTest(returncode=returncode):
                with self.assertRaises(app_server.BridgeError) as caught:
                    self.fetch(FakeProcess(returncode=returncode))
                self.assertEqual((caught.exception.status, caught.exception.code), (status, code))

    def test_unavailable_curl_has_a_safe_bridge_error(self):
        with patch.object(app_server.subprocess, "Popen", side_effect=OSError("private executable path")):
            with self.assertRaises(app_server.BridgeError) as caught:
                app_server.fetch_steam(APPID)
        self.assertEqual(caught.exception.status, 502)
        self.assertNotIn("private", str(caught.exception))


class LocalServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.calls = []
        cls.upstream_result = news_document()
        cls.upstream_error = None

        def fake_fetcher(appid, client_alive=lambda: True):
            cls.calls.append(appid)
            if cls.upstream_error is not None:
                raise cls.upstream_error
            return cls.upstream_result

        cls.server = app_server.make_server(port=0, fetcher=fake_fetcher)
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)
        if cls.thread.is_alive():
            raise AssertionError("local server did not stop")

    def setUp(self):
        type(self).calls.clear()
        type(self).upstream_result = news_document()
        type(self).upstream_error = None

    def request(self, path="/api/steam-news?appid=570", method="GET", headers=None, host=None, omit_host=False):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        try:
            conn.putrequest(method, path, skip_host=True, skip_accept_encoding=True)
            if not omit_host:
                conn.putheader("Host", host if host is not None else f"127.0.0.1:{self.port}")
            for key, value in (headers or {}).items():
                conn.putheader(key, value)
            conn.endheaders()
            response = conn.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            conn.close()

    def assert_security_headers(self, headers):
        headers = {key.lower(): value for key, value in headers.items()}
        self.assertEqual(headers.get("x-content-type-options"), "nosniff")
        self.assertIn("no-store", headers.get("cache-control", ""))
        self.assertNotIn("access-control-allow-origin", headers)

    def assert_denied_without_fetch(self, expected_status, **request_kwargs):
        status, headers, _ = self.request(**request_kwargs)
        self.assertEqual(status, expected_status)
        self.assertEqual(self.calls, [])
        self.assert_security_headers(headers)

    def test_binds_only_ipv4_loopback(self):
        self.assertEqual(self.server.server_address[0], "127.0.0.1")

    def test_api_uses_only_appid_and_returns_valid_empty_news(self):
        status, headers, body = self.request()
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body), news_document())
        self.assertEqual(self.calls, [APPID])
        self.assertTrue(headers.get("Content-Type", "").startswith("application/json"))
        self.assert_security_headers(headers)

    def test_both_document_routes_have_local_app_marker(self):
        for path in ("/", "/before-arrival-showcase.html"):
            with self.subTest(path=path):
                status, headers, body = self.request(path=path)
                self.assertEqual(status, 200)
                self.assertTrue(headers.get("Content-Type", "").startswith("text/html"))
                self.assertRegex(body.decode("utf-8"), r'<meta\b[^>]*name=[\"\x27]before-arrival-local-app[\"\x27][^>]*content=[\"\x27]steam-news-v1[\"\x27]')
                self.assert_security_headers(headers)
        self.assertEqual(self.calls, [])

    def test_invalid_queries_never_reach_upstream(self):
        paths = (
            "/api/steam-news", "/api/steam-news?", "/api/steam-news?appid=",
            "/api/steam-news?appid=0", "/api/steam-news?appid=0570",
            "/api/steam-news?appid=-1", "/api/steam-news?appid=4294967296",
            "/api/steam-news?appid=%2B570", "/api/steam-news?appid=570%20",
            "/api/steam-news?appid=570&appid=570", "/api/steam-news?appid=570&appid=730",
            "/api/steam-news?appid=570&url=https%3A%2F%2Fexample.com",
            "/api/steam-news?url=http%3A%2F%2F127.0.0.1%2F",
            "/api/steam-news?appid=570&unused=1", "/api/steam-news?appid=570&unused=",
        )
        for path in paths:
            with self.subTest(path=path):
                self.assert_denied_without_fetch(400, path=path)

    def test_arbitrary_paths_and_files_are_unavailable(self):
        for path in ("/api/other?appid=570", "/etc/passwd", "/../AGENTS.md", "/%2e%2e/AGENTS.md", "/app_server.py"):
            with self.subTest(path=path):
                self.assert_denied_without_fetch(404, path=path)

    def test_post_is_not_an_upstream_proxy(self):
        self.assert_denied_without_fetch(405, method="POST")

    def test_host_must_be_loopback_and_actual_bound_port(self):
        invalid = (
            "example.com", f"example.com:{self.port}", "127.0.0.1", "localhost",
            f"127.0.0.1:{self.port + 1}", f"localhost:{self.port + 1}",
            f"127.0.0.1.example.com:{self.port}", f"localhost.evil:{self.port}",
            f"user@127.0.0.1:{self.port}", f"127.1:{self.port}",
        )
        for host in invalid:
            with self.subTest(host=host):
                self.assert_denied_without_fetch(403, host=host)
        self.assert_denied_without_fetch(403, omit_host=True)

    def test_localhost_host_is_accepted(self):
        status, _, _ = self.request(host=f"localhost:{self.port}")
        self.assertEqual(status, 200)
        self.assertEqual(self.calls, [APPID])

    def test_only_exact_loopback_origins_on_this_port_are_accepted(self):
        for origin in (f"http://127.0.0.1:{self.port}", f"http://localhost:{self.port}"):
            with self.subTest(origin=origin):
                self.calls.clear()
                status, headers, _ = self.request(headers={"Origin": origin}, host=urlsplit(origin).netloc)
                self.assertEqual(status, 200)
                self.assertEqual(self.calls, [APPID])
                self.assert_security_headers(headers)

    def test_foreign_null_and_malformed_origins_never_reach_upstream(self):
        origins = (
            "null", "https://example.com", "http://127.0.0.1", "http://localhost",
            f"https://127.0.0.1:{self.port}", f"http://127.0.0.1:{self.port + 1}",
            f"http://127.0.0.1:{self.port}/", f"http://localhost.evil:{self.port}",
            f"http://user@127.0.0.1:{self.port}",
        )
        for origin in origins:
            with self.subTest(origin=origin):
                self.assert_denied_without_fetch(403, headers={"Origin": origin})

    def test_cross_site_fetch_is_rejected_even_without_origin(self):
        self.assert_denied_without_fetch(403, headers={"Sec-Fetch-Site": "cross-site"})

    def test_same_origin_and_navigation_fetches_are_accepted(self):
        for fetch_site in ("same-origin", "none"):
            with self.subTest(fetch_site=fetch_site):
                status, _, _ = self.request(headers={"Sec-Fetch-Site": fetch_site})
                self.assertEqual(status, 200)

    def test_socket_disconnect_is_visible_to_fetcher(self):
        started = threading.Event()
        disconnected = threading.Event()

        def waiting_fetcher(appid, client_alive):
            started.set()
            deadline = time.monotonic() + 2
            while time.monotonic() < deadline:
                if not client_alive():
                    disconnected.set()
                    raise app_server.BridgeError(499, "client_closed")
                disconnected.wait(0.01)
            return news_document()

        server = app_server.make_server(port=0, fetcher=waiting_fetcher)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        conn = socket.create_connection(server.server_address, timeout=2)
        try:
            port = server.server_address[1]
            conn.sendall((f"GET /api/steam-news?appid=570 HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n\r\n").encode("ascii"))
            self.assertTrue(started.wait(1), "request did not reach the fake fetcher")
            conn.shutdown(socket.SHUT_RDWR)
            conn.close()
            self.assertTrue(disconnected.wait(1), "fetcher did not observe the client disconnect")
        finally:
            conn.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=3)

    def test_upstream_errors_have_json_and_no_cache_or_cors_grant(self):
        type(self).upstream_error = app_server.BridgeError(502, "upstream_unavailable")
        status, headers, body = self.request()
        self.assertEqual(status, 502)
        self.assertIsInstance(json.loads(body), dict)
        self.assertIn("upstream_unavailable", body.decode("utf-8"))
        self.assertTrue(headers.get("Content-Type", "").startswith("application/json"))
        self.assertEqual(self.calls, [APPID])
        self.assert_security_headers(headers)


if __name__ == "__main__":
    unittest.main()
