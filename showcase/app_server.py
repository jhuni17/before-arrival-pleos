#!/usr/bin/env python3
"""Loopback-only UI and fixed official Steam news request; never a general proxy."""
import argparse
import json
import re
import select
import socket
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

MAX_BYTES = 262144
MAX_SECONDS = 8
HTML_PATH = Path(__file__).resolve().with_name("before-arrival-showcase.html")
CAPABILITY = b'<meta name="before-arrival-local-app" content="steam-news-v1">'


class BridgeError(Exception):
    def __init__(self, status, code):
        super().__init__(code)
        self.status, self.code = status, code


def build_steam_url(appid):
    if not isinstance(appid, str) or not re.fullmatch(r"[1-9][0-9]{0,9}", appid) or int(appid) > 4294967295:
        raise BridgeError(400, "invalid_appid")
    return ("https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=" + appid
            + "&count=5&maxlength=600&format=json&feeds=steam_community_announcements")


def decode_upstream(body, http_status, content_type, appid):
    build_steam_url(appid)
    if 300 <= http_status < 400:
        raise BridgeError(502, "upstream_redirect")
    if http_status != 200:
        raise BridgeError(502, "upstream_status")
    if content_type.split(";", 1)[0].strip().lower() != "application/json":
        raise BridgeError(502, "upstream_content_type")
    if len(body) > MAX_BYTES:
        raise BridgeError(502, "upstream_too_large")
    try:
        def no_constant(_):
            raise ValueError("Invalid JSON constant")
        data = json.loads(body.decode("utf-8"), parse_constant=no_constant)
        news = data["appnews"]
        if type(news["appid"]) is not int or news["appid"] != int(appid):
            raise ValueError("Wrong game")
        if not isinstance(news["newsitems"], list) or len(news["newsitems"]) > 50:
            raise ValueError("Invalid item list")
        return data
    except (ValueError, UnicodeError, KeyError, TypeError):
        raise BridgeError(502, "upstream_invalid_json") from None


def fetch_steam(appid, client_alive=lambda: True):
    url = build_steam_url(appid)
    # -q disables curlrc. No shell, redirects, caller headers, credentials or cookies.
    # Existing proxy/NO_PROXY/CA/TLS environment is inherited without modification.
    args = ["curl", "-q", "--silent", "--show-error", "--request", "GET",
            "--proto", "=https", "--max-time", "6", "--connect-timeout", "5",
            "--max-filesize", str(MAX_BYTES), "--max-redirs", "0",
            "--header", "Accept: application/json", "--header", "Cookie:", "--header", "Authorization:",
            "--write-out", "%{stderr}\nBEFORE_ARRIVAL_HTTP\t%{http_code}\t%{content_type}\t%{ssl_verify_result}\n", url]
    try:
        process = subprocess.Popen(args, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, close_fds=True)
    except OSError:
        raise BridgeError(502, "transport_unavailable") from None
    done, reason = threading.Event(), []
    deadline = time.monotonic() + MAX_SECONDS

    def terminate(code):
        reason.append(code)
        try:
            process.kill()
        except ProcessLookupError:
            pass

    def watch():
        while not done.wait(0.05):
            if time.monotonic() >= deadline:
                terminate("upstream_timeout")
                return
            if not client_alive():
                terminate("client_closed")
                return

    watcher = threading.Thread(target=watch, daemon=True)
    watcher.start()
    try:
        body = bytearray()
        while True:
            chunk = process.stdout.read(16384)
            if not chunk:
                break
            if len(body) + len(chunk) > MAX_BYTES:
                terminate("upstream_too_large")
                raise BridgeError(502, "upstream_too_large")
            body.extend(chunk)
        # stderr is bounded and never returned to the user or persisted as credentials.
        diagnostics = process.stderr.read(8192)
        process.wait(timeout=1)
        if reason:
            raise BridgeError(504 if reason[0] == "upstream_timeout" else 499, reason[0])
        if process.returncode == 28:
            raise BridgeError(504, "upstream_timeout")
        if process.returncode == 63:
            raise BridgeError(502, "upstream_too_large")
        if process.returncode != 0:
            raise BridgeError(502, "upstream_unavailable")
        metadata = next((line for line in diagnostics.decode("utf-8", "replace").splitlines()
                         if line.startswith("BEFORE_ARRIVAL_HTTP\t")), None)
        if not metadata:
            raise BridgeError(502, "upstream_unavailable")
        fields = metadata.split("\t")
        if len(fields) != 4 or fields[3] != "0":
            raise BridgeError(502, "upstream_tls")
        return decode_upstream(bytes(body), int(fields[1]), fields[2], appid)
    except (OSError, ValueError, subprocess.TimeoutExpired):
        raise BridgeError(502, "upstream_unavailable") from None
    finally:
        done.set()
        if process.poll() is None:
            try:
                process.kill()
                process.wait(timeout=1)
            except (OSError, subprocess.TimeoutExpired):
                pass
        process.stdout.close()
        process.stderr.close()
        watcher.join(timeout=0.1)


class LocalAppHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass  # No URLs, request headers or upstream diagnostics in ambient logs.

    def _client_alive(self):
        try:
            if not select.select([self.connection], [], [], 0)[0]:
                return True
            return self.connection.recv(1, socket.MSG_PEEK | getattr(socket, "MSG_DONTWAIT", 0)) != b""
        except (OSError, ValueError):
            return False

    def _respond(self, status, data, content_type="application/json; charset=utf-8"):
        if not isinstance(data, bytes):
            data = json.dumps(data, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass
        self.close_connection = True

    def _boundary(self):
        hosts = self.headers.get_all("Host", [])
        accepted = {"127.0.0.1:" + str(self.server.server_port), "localhost:" + str(self.server.server_port)}
        if len(hosts) != 1 or hosts[0] not in accepted:
            raise BridgeError(403, "invalid_host")
        origins = self.headers.get_all("Origin", [])
        if origins and (len(origins) != 1 or origins[0] != "http://" + hosts[0]):
            raise BridgeError(403, "invalid_origin")
        if self.headers.get("Sec-Fetch-Site") == "cross-site":
            raise BridgeError(403, "cross_site")
        if len(self.path) > 2048:
            raise BridgeError(400, "invalid_request")
        parsed = urlsplit(self.path)
        if parsed.scheme or parsed.netloc or parsed.fragment:
            raise BridgeError(400, "invalid_request")
        return parsed

    def do_GET(self):
        try:
            request = self._boundary()
            if request.path in ("/", "/before-arrival-showcase.html"):
                if request.query:
                    # UI scene/theme selectors are local only and never sent upstream.
                    if not re.fullmatch(r"(?:state=[a-z]+|theme=(?:day|night)|long=1)(?:&(?:state=[a-z]+|theme=(?:day|night)|long=1))*", request.query):
                        raise BridgeError(400, "invalid_request")
                html = HTML_PATH.read_bytes()
                if b"<head>" not in html:
                    raise BridgeError(500, "app_unavailable")
                self._respond(200, html.replace(b"<head>", b"<head>" + CAPABILITY, 1), "text/html; charset=utf-8")
                return
            if request.path != "/api/steam-news":
                raise BridgeError(404, "not_found")
            match = re.fullmatch(r"appid=([1-9][0-9]{0,9})", request.query)
            if not match:
                raise BridgeError(400, "invalid_appid")
            appid = match.group(1)
            build_steam_url(appid)
            result = self.server.fetcher(appid, self._client_alive)
            self._respond(200, result)
        except BridgeError as error:
            self._respond(error.status, {"error": {"code": error.code, "message": "소식을 가져오지 못했어요. 보관한 내용은 그대로예요."}})
        except (OSError, ValueError):
            self._respond(500, {"error": {"code": "app_unavailable", "message": "앱 실행 상태를 확인해 주세요."}})

    def do_POST(self):
        self._respond(405, {"error": {"code": "method_not_allowed"}})

    do_PUT = do_DELETE = do_PATCH = do_OPTIONS = do_HEAD = do_POST


def make_server(port=8765, fetcher=fetch_steam):
    if type(port) is not int or not 0 <= port <= 65535:
        raise ValueError("Port must be0..65535")
    server = ThreadingHTTPServer(("127.0.0.1", port), LocalAppHandler)
    server.daemon_threads = True
    server.fetcher = fetcher
    return server


def main():
    parser = argparse.ArgumentParser(description="도착 전에 · 로컬 앱 실행")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    server = make_server(args.port)
    print("도착 전에: http://127.0.0.1:" + str(server.server_port), flush=True)
    print("같은 주소를 계속 사용하면 이 주소의 브라우저 설정·저장을 유지합니다.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
