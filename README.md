# Somersett Fire Radio Scanner

**Current version:** 0.3.0  
**Download:** [Somersett Fire Radio Scanner 0.3.0 APK](https://github.com/clint-maples/somersett-fire-radio-scanner/releases/download/0.3.0/somersett-fire-radio-scanner-0.3.0.apk)

**Repo:** [github.com/clint-maples/somersett-fire-radio-scanner](https://github.com/clint-maples/somersett-fire-radio-scanner)

Local multi-feed Broadcastify radio / fire scanner. Clint used a Python + web build for the Floriston, CA fire. This repo ships both:

1. **Android app** (primary) — simultaneous feeds, per-feed controls, green→yellow spectrum
2. **Windows / desktop** Python + web reference under [`desktop/`](desktop/)

No Broadcastify page chrome or ads in the UI. **No Broadcastify login.**

0.1.0 and 0.2.0 sideload APKs were signed with the Android **debug** certificate. **0.3.0 is already published as a release-signed APK** and requires a fresh install — debug-signed copies cannot be updated in place. Uninstall those older APKs first.

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

Kotlin + Jetpack Compose + Media3 / ExoPlayer. Each feed is its own player so they can run at the same time. Display name: **Somersett Fire Radio Scanner**.

### Install a signed APK

The published **0.3.0** APK on [GitHub Releases](https://github.com/clint-maples/somersett-fire-radio-scanner/releases) is already release-signed. This git tree does **not** contain APKs or keystores.

1. Download the signed `0.3.0` APK from the [top of this README](#somersett-fire-radio-scanner) or from Releases — or build one locally (below).
2. On the phone: **Settings → Security** (or **Apps**) → allow **Install unknown apps** for the app you use to open the file.
3. Uninstall any 0.1.0 / 0.2.0 debug-signed copy first.
4. Open the APK and install.
5. Launch **Somersett Fire Radio Scanner**, grant notifications if you want the “Listening” pill while it runs in the background, then tap **Play all**.

Published 0.3.0 fingerprints:

- Signing-cert SHA-256: `bac9180d6c87df96ed6eb246e552f8f83f4e690ee6048bd239108174f52d73bb`
- APK SHA-256: `b9c3b307cc214c0dbd9eaeb6934ebfb44fc97fb8147f142d75d8505adaba9a29`

The app scrapes a **fresh HLS token** on every play / reconnect. It does not embed JWTs. Playback URLs are allowlisted to `https` + `broadcastify.com` / `*.broadcastify.com`.

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

Without a release keystore, Gradle produces an **unsigned** `app-release-unsigned.apk`. That is for compile checks only — do not sideload it as a “release.”

Debug builds use application id `com.clintmaples.broadcastifyscanner.debug`.

### Release signing (off-repo keystore)

Release builds never use the Android Debug certificate. Sign with a keystore that is **not** in this repository.

1. Generate a PKCS12 keystore on a machine you control (not in the repo, not in chat, not in backups you share):

```bash
mkdir -p "$HOME/keys"
keytool -genkeypair -v \
  -keystore "$HOME/keys/somersett-fire-radio-scanner-release.keystore" \
  -alias somersett \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12
```

2. Copy [`android/keystore.properties.example`](android/keystore.properties.example) to `android/keystore.properties` (gitignored) and fill in `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`.  
   Or export `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.

3. `./gradlew assembleRelease` then writes `android/app/build/outputs/apk/release/app-release.apk`.

4. Publish the APK on GitHub Releases and record the **signing-cert SHA-256** and APK SHA-256 in the release notes.

Never commit `*.keystore`, `*.jks`, `keystore.properties`, or APKs.

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

No installer and no Node. A later PyInstaller one-file `.exe` is optional; not required for 0.3.0.

Desktop UI details (autoplay, Reconnect, ＋ add feed) live in [`desktop/README.md`](desktop/README.md).

---

## How HLS / JWT refresh works

Broadcastify does **not** expose a public stream URL you can hardcode. Direct `audio.broadcastify.com/<id>.mp3` returns 501. Live audio is **HLS**, and the playlist URL contains a short-lived path token.

Both clients do the same scrape as `desktop/server.py`:

1. `GET https://www.broadcastify.com/listen/feed/popout.php?feedId=<id>` with a desktop Chrome User-Agent.
2. Parse `hlsUrl` / `feedName` from `ListenPlayer.init(...)`.
3. Android rejects the URL unless it is `https` on `broadcastify.com` / `*.broadcastify.com`. Off-origin redirects are not followed.
4. Play that URL as HLS.

Current URLs look like:

`https://hls-o2.broadcastify.com/t/v1.<payload>.<sig>/feed/<id>/playlist.m3u8`

The `v1.<payload>.<sig>` segment is JWT-shaped. The payload’s `t` field is **issued-at**, not a standard `exp`. Tokens still expire. **Never commit or hardcode a live `hlsUrl`.**

| | Android | Desktop |
|---|---|---|
| Fresh token | Popout scrape on each Play / Reconnect | `GET /api/stream/<feedId>` |
| Expiry | Player 401/403 / manifest errors → wait 4s → scrape again | hls.js fatal error → Reconnect / auto-retry 4s |
| CDN headers | ExoPlayer `User-Agent`, `Referer`, `Origin` | Local `/proxy` rewrites playlists and adds the same headers (avoids browser CORS) |
| Host allowlist | `https` + `*.broadcastify.com` (fail closed) | Desktop proxy is localhost-only (not covered by the Android review) |

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
releases/    Placeholder only — APKs are not committed
```

Security notes for Android: [`SECURITY_REVIEW.md`](SECURITY_REVIEW.md).

---

## Requirements

- **Android:** phone/tablet on Android 8.0+ (API 26). Network access to `broadcastify.com` and `*.broadcastify.com`.
- **Desktop:** Python 3.10+, modern browser (Chrome / Edge / Firefox with MediaSource, or Safari native HLS). Bound to localhost only.

Not affiliated with Broadcastify / RadioReference. Personal listener for feeds you already have the IDs for.

---

## License

This project is licensed under the [MIT License](LICENSE). Copyright © 2026 Clint Maples.
