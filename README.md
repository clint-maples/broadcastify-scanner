# Broadcastify Scanner

**Current version:** 0.1.0  
**Download:** [Broadcastify Scanner 0.1.0 APK](https://github.com/clint-maples/broadcastify-scanner/releases/download/0.1.0/broadcastify-scanner-0.1.0.apk)

Local multi-feed Broadcastify radio / fire scanner. Clint used a Python + web build for the Floriston, CA fire. This repo ships both:

1. **Android app** (primary) — simultaneous feeds, per-feed controls, green→yellow spectrum
2. **Windows / desktop** Python + web reference under [`desktop/`](desktop/)

No Broadcastify page chrome or ads in the UI. Private / unlicensed, same as Clint's other personal apps — there is no `LICENSE` file.

## Default feeds

Configurable by Broadcastify **feed ID**. Defaults:

| ID | Name |
|----|------|
| **14826** | East Placer / Nevada CAL FIRE NEU (Kings Beach / Truckee) |
| **47365** | CAL FIRE NEU West |
| **47367** | Tahoe National Forest West |

Add or remove feeds anytime. IDs are the numbers in `https://www.broadcastify.com/listen/feed/<id>`.

---

## Android

Kotlin + Jetpack Compose + Media3 / ExoPlayer. Each feed is its own player so they can run at the same time.

### Install the APK

1. Copy [broadcastify-scanner-0.1.0.apk](https://github.com/clint-maples/broadcastify-scanner/releases/download/0.1.0/broadcastify-scanner-0.1.0.apk) to the phone (Drive, USB, Messages, etc.).
2. On the phone: **Settings → Security** (or **Apps**) → allow **Install unknown apps** for the app you use to open the file.
3. Open the APK and install.
4. Launch **Broadcastify Scanner**, grant notifications if you want the “Listening” pill while it runs in the background, then tap **Play all**.

The app scrapes a **fresh HLS token** on every play / reconnect. It does not embed JWTs.

### What you can do

- **Play all / Stop all**, plus per-feed Play, Stop, Mute, volume, Reconnect, Remove
- **Master** volume
- **＋ Feed** — add another feed by numeric ID (name optional; the popout title is used when omitted)
- **Awake** — keep the screen on (useful during an active fire)
- Per-feed **green → yellow spectrum** — still moves when that card is muted, so you can see which channel is talking

A small notification keeps playback alive when you leave the app. Stop all feeds to dismiss it.

### Build from source

Needs JDK 17+ and Android SDK 35.

```bat
cd android
gradlew.bat assembleRelease
```

```bash
cd android
./gradlew assembleRelease
```

APK output: `android/app/build/outputs/apk/release/app-release-unsigned.apk`  
A copy of the built artifact is also kept in `releases/` when CI/local packaging is run.

Debug builds use application id `com.clintmaples.broadcastifyscanner.debug`.

---

## Desktop (Windows / any Python 3.10+)

Stdlib only — no `pip install`. Server binds to **127.0.0.1:3847**.

```bat
cd desktop
python server.py
```

If `python` is missing:

```bat
py server.py
```

Or double-click [`desktop/start.bat`](desktop/start.bat).

Then open **http://127.0.0.1:3847** and click **Play all** (a click is required so the browser unlocks audio).

### Portable zip folder

1. Zip the `desktop/` folder (or copy it) to the PC — Downloads, USB, etc.
2. Install [Python 3.10+](https://www.python.org/downloads/) if needed (the `py` launcher is enough).
3. Unzip, run `start.bat` or `python server.py` / `py server.py`.
4. Browse to http://127.0.0.1:3847 → **Play all**.

No installer and no Node. A later PyInstaller one-file `.exe` is optional; not required for 0.1.0.

Desktop UI details (autoplay, Reconnect, ＋ add feed) live in [`desktop/README.md`](desktop/README.md).

---

## How HLS / JWT refresh works

Broadcastify does **not** expose a public stream URL you can hardcode. Direct `audio.broadcastify.com/<id>.mp3` returns 501. Live audio is **HLS**, and the playlist URL contains a short-lived path token.

Both clients do the same scrape as `desktop/server.py`:

1. `GET https://www.broadcastify.com/listen/feed/popout.php?feedId=<id>` with a desktop Chrome User-Agent.
2. Parse `hlsUrl` / `feedName` from `ListenPlayer.init(...)`.
3. Play that URL as HLS.

Current URLs look like:

`https://hls-o2.broadcastify.com/t/v1.<payload>.<sig>/feed/<id>/playlist.m3u8`

The `v1.<payload>.<sig>` segment is JWT-shaped. The payload’s `t` field is **issued-at**, not a standard `exp`. Tokens still expire. **Never commit or hardcode a live `hlsUrl`.**

| | Android | Desktop |
|---|---|---|
| Fresh token | Popout scrape on each Play / Reconnect | `GET /api/stream/<feedId>` |
| Expiry | Player 401/403 / manifest errors → wait 4s → scrape again | hls.js fatal error → Reconnect / auto-retry 4s |
| CDN headers | ExoPlayer `User-Agent`, `Referer`, `Origin` | Local `/proxy` rewrites playlists and adds the same headers (avoids browser CORS) |

---

## Preroll caveat

Free Broadcastify feeds often play a **15–30 second CDN preroll** before the scanner. This app cannot strip it. Premium / logged-in Broadcastify sessions are not implemented.

---

## Scanner awareness

Fire and radio dispatch channels spend most of their time **squelched / silent**. If a card says **playing** or **muted** and the spectrum is flat, that is normal — wait for the next transmission. The meter is there so you can watch several feeds without turning them all up.

Android: mute a card and leave the spectrum running. Desktop: same (Web Audio analyser stays live; mute is a gain node).

---

## Repo layout

```
android/     Kotlin + Compose + Media3 app (assemble here)
desktop/     Python stdlib server + public/ UI (hls.js)
releases/    Installable APK for 0.1.0
```

---

## Requirements

- **Android:** phone/tablet on Android 8.0+ (API 26). Network access to `broadcastify.com` and `*.broadcastify.com`.
- **Desktop:** Python 3.10+, modern browser (Chrome / Edge / Firefox with MediaSource, or Safari native HLS). Bound to localhost only.

Not affiliated with Broadcastify / RadioReference. Personal listener for feeds you already have the IDs for.
