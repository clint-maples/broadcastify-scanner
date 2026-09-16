#!/usr/bin/env python3
"""Stdlib tests for desktop/server.py (no network)."""

from __future__ import annotations

import unittest

import server


class RewriteTests(unittest.TestCase):
    def test_rewrite_playlist(self) -> None:
        body = (
            "#EXTM3U\n"
            "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n"
            "seg001.ts\n"
        )
        out = server.rewrite_m3u8(body, "https://hls-o2.broadcastify.com/feed/1/playlist.m3u8")
        text = out.decode("utf-8")
        self.assertIn("/proxy?url=", text)
        self.assertIn("seg001.ts", text)
        self.assertIn("key.bin", text)

    def test_proxy_host_allowlist(self) -> None:
        self.assertTrue(server.is_allowed_proxy_url("https://hls-o2.broadcastify.com/x"))
        self.assertTrue(server.is_allowed_proxy_url("https://www.broadcastify.com/x"))
        self.assertFalse(server.is_allowed_proxy_url("https://evil.example/x"))
        self.assertFalse(server.is_allowed_proxy_url("file:///etc/passwd"))

    def test_unescape(self) -> None:
        self.assertEqual(
            server.unescape_jsonish(r"https:\/\/hls-o2.broadcastify.com\/t\/x"),
            "https://hls-o2.broadcastify.com/t/x",
        )


if __name__ == "__main__":
    unittest.main()
