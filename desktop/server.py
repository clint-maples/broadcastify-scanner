#!/usr/bin/env python3
"""Local Broadcastify multi-feed scanner — stdlib only (Python 3.10+)."""

from __future__ import annotations

import json
import re
import urllib.error
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, unquote, urljoin, urlparse

HOST = "127.0.0.1"
PORT = 3847
ROOT = Path(__file__).resolve().parent
PUBLIC = ROOT / "public"

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
REFERER = "https://www.broadcastify.com/"

DEFAULT_FEEDS = [
    {
        "feedId": "14826",
        "name": "East Placer and Nevada Counties CAL FIRE NEU - Kings Beach Area",
    },
    {"feedId": "47365", "name": "CAL FIRE NEU West"},
    {"feedId": "47367", "name": "Tahoe National Forest West"},
]

_HLS_RE = re.compile(r'hlsUrl:\s*"((?:\\.|[^"\\])*)"')
_NAME_RE = re.compile(r'feedName:\s*"((?:\\.|[^"\\])*)"')
_TITLE_RE = re.compile(r"<title>([^—<]+)")
_URI_ATTR_RE = re.compile(r'URI="([^"]+)"')


def unescape_jsonish(s: str) -> str:
    return s.replace("\\/", "/").replace('\\"', '"').replace("\\\\", "\\")


def fetch_feed_meta(feed_id: str) -> dict:
    fid = re.sub(r"\D", "", str(feed_id))
    if not fid:
        raise ValueError("Invalid feedId")

    url = f"https://www.broadcastify.com/listen/feed/popout.php?feedId={fid}"
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "text/html,application/xhtml+xml",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            html = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"Broadcastify returned {e.code}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"Failed to reach Broadcastify: {e.reason}") from e

    m = _HLS_RE.search(html)
    if not m:
        raise RuntimeError(
            "Could not find hlsUrl in popout page (feed may be offline or page changed)"
        )
    hls_url = unescape_jsonish(m.group(1))

    name = None
    nm = _NAME_RE.search(html)
    if nm:
        name = unescape_jsonish(nm.group(1))
    else:
        tm = _TITLE_RE.search(html)
        if tm:
            name = tm.group(1).strip()

    if not name:
        known = next((f for f in DEFAULT_FEEDS if f["feedId"] == fid), None)
        name = known["name"] if known else f"Feed {fid}"

    return {"feedId": fid, "name": name, "hlsUrl": hls_url}


def is_allowed_proxy_url(url: str) -> bool:
    try:
        u = urlparse(url)
    except Exception:
        return False
    if u.scheme not in ("http", "https"):
        return False
    host = (u.hostname or "").lower()
    return host == "broadcastify.com" or host.endswith(".broadcastify.com")


def local_proxy_url(target: str) -> str:
    return "/proxy?url=" + quote(target, safe="")


def rewrite_m3u8(body: str, base_url: str) -> bytes:
    out_lines: list[str] = []
    for line in body.splitlines():
        stripped = line.strip()
        if not stripped:
            out_lines.append(line)
            continue
        if stripped.startswith("#"):
            if "URI=" in stripped:

                def _repl(match: re.Match[str]) -> str:
                    abs_u = urljoin(base_url, match.group(1))
                    return f'URI="{local_proxy_url(abs_u)}"'

                out_lines.append(_URI_ATTR_RE.sub(_repl, line))
            else:
                out_lines.append(line)
            continue
        abs_u = urljoin(base_url, stripped)
        out_lines.append(local_proxy_url(abs_u))
    return ("\n".join(out_lines) + "\n").encode("utf-8")


def fetch_upstream(url: str) -> tuple[bytes, str]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Referer": REFERER,
            "Origin": "https://www.broadcastify.com",
            "Accept": "*/*",
        },
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read()
        ctype = resp.headers.get("Content-Type", "application/octet-stream")
        return data, ctype


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(PUBLIC), **kwargs)

    def log_message(self, fmt: str, *args) -> None:
        try:
            print(f"[{self.log_date_time_string()}] {args[1]} {args[0]}", flush=True)
        except Exception:
            pass

    def _send_json(self, status: int, obj: dict) -> None:
        body = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _send_bytes(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"

        if path == "/api/feeds":
            self._send_json(200, {"feeds": DEFAULT_FEEDS})
            return

        if path.startswith("/api/stream/"):
            feed_id = path[len("/api/stream/") :]
            try:
                meta = fetch_feed_meta(feed_id)
                self._send_json(200, meta)
            except ValueError as e:
                self._send_json(400, {"error": str(e)})
            except RuntimeError as e:
                self._send_json(502, {"error": str(e)})
            except Exception as e:  # noqa: BLE001
                self._send_json(500, {"error": str(e)})
            return

        if path == "/proxy":
            qs = parse_qs(parsed.query)
            raw = (qs.get("url") or [None])[0]
            if not raw:
                self._send_json(400, {"error": "Missing url parameter"})
                return
            target = unquote(raw)
            if not is_allowed_proxy_url(target):
                self._send_json(403, {"error": "Host not allowed"})
                return
            try:
                data, ctype = fetch_upstream(target)
            except urllib.error.HTTPError as e:
                self._send_json(502, {"error": f"Upstream HTTP {e.code}"})
                return
            except Exception as e:  # noqa: BLE001
                self._send_json(502, {"error": f"Proxy fetch failed: {e}"})
                return

            lower_ct = (ctype or "").lower()
            is_playlist = (
                "mpegurl" in lower_ct
                or "m3u8" in lower_ct
                or target.lower().split("?", 1)[0].endswith(".m3u8")
            )
            if is_playlist:
                try:
                    text = data.decode("utf-8", errors="replace")
                except Exception:
                    text = data.decode("latin-1", errors="replace")
                body = rewrite_m3u8(text, target)
                self._send_bytes(200, body, "application/vnd.apple.mpegurl")
                return

            # TS / AAC / binary segments
            out_ct = ctype or "application/octet-stream"
            self._send_bytes(200, data, out_ct)
            return

        # Static files from public/
        if path == "/":
            self.path = "/index.html"
        super().do_GET()


def main() -> None:
    if not PUBLIC.is_dir():
        raise SystemExit(f"Missing public/ directory at {PUBLIC}")

    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Somersett Fire Radio Scanner listening at http://{HOST}:{PORT}", flush=True)
    print(f"Default feeds: {', '.join(f['feedId'] for f in DEFAULT_FEEDS)}", flush=True)
    print("Press Ctrl+C to stop.", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.", flush=True)
        httpd.shutdown()


if __name__ == "__main__":
    main()
