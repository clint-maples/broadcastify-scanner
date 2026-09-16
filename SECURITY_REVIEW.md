# Android security review — Broadcastify Scanner 0.1.0

**Audience:** Clint (SVP/CISO)  
**Scope:** Android app only (`android/`, plus the shipped APK). Desktop / Windows code was not reviewed.  
**Review date:** 2026-09-16  
**Code revision:** `87323cc` on `main` (`https://github.com/clint-maples/broadcastify-scanner`)  
**Shipped artifact:** `releases/broadcastify-scanner-0.1.0.apk` (also GitHub Release `0.1.0`)  
**Method:** Source review of Kotlin, manifest, Gradle, and unit tests; static inspection of the shipped APK (v2 signing cert, merged manifest). No dynamic instrumented tests, no MITM lab, no Play Console review.

This is a findings report. No remediations were implemented: nothing in the Android tree was a small, unambiguously dangerous foot-gun that could be hardened without a signing-key / product decision.

---

## Executive summary

The Android app is a **local, unofficial Broadcastify listener**. It scrapes `popout.php` for a short-lived HLS path token, then plays that playlist with Media3/ExoPlayer. There is **no Broadcastify login, cookie jar, password, or API key**. That single design choice removes the usual account-takeover and credential-theft classes that would otherwise dominate this review.

**Overall risk for the current (personal, sideload, private-repo) deployment: Medium.**  
There is **no Critical remote code execution**, no exported attack surface that other apps can drive with attacker-controlled extras, and no WebView/JS bridge.

**Top risks**

1. **High — Release/sideload APK is signed with an Android Debug certificate.** Anyone who obtains the build machine’s `~/.android/debug.keystore` (password is the well-known `android`) can produce a same-package “update” that Android will install over 0.1.0. That is the identity of every copy already handed out via the GitHub release.
2. **Medium — Playback URL is whatever the popout HTML says.** `hlsUrl` is not checked against `*.broadcastify.com` before ExoPlayer fetches it. If the HTML is attacker-controlled (Broadcastify compromise, XSS on the popout, or TLS interception), the app will GET and play an arbitrary HTTPS stream (fake dispatch audio) and issue a client request to an arbitrary host.
3. **Low / residual — Auto Backup + plaintext prefs.** Feed IDs and names (which channels you listen to) sit in `SharedPreferences` with `allowBackup="true"`. No credentials, but it is operational metadata.

If this stays a private, personal sideload tool, item 1 is the only finding that should block a wider rollout. Items 2–3 are defense-in-depth.

---

## Threat model

### What the app does

| Step | Behavior | Evidence |
|---|---|---|
| Configure | User adds numeric Broadcastify feed IDs (defaults 14826 / 47365 / 47367). | `ScannerScreen.kt` filters input to digits; `DefaultFeeds` in `Feed.kt` |
| Resolve | `GET https://www.broadcastify.com/listen/feed/popout.php?feedId=<digits>` with a desktop Chrome User-Agent. Regex-parse `hlsUrl` / `feedName`. | `BroadcastifyClient.kt`, `BroadcastifyParser.kt` |
| Play | Media3 ExoPlayer loads the HLS URL with `Referer` / `Origin` / Chrome UA. On 401/403/parse errors, wait 4s and scrape again. | `FeedSession.kt` |
| Persist | Feed ID + display name, master volume, keep-awake flag. **Not** the HLS URL. | `FeedStore.kt` |
| Background | Non-exported `mediaPlayback` foreground service + optional `POST_NOTIFICATIONS`. | `PlaybackService.kt`, `AndroidManifest.xml` |

Premium / logged-in Broadcastify sessions are explicitly **not implemented** (root `README.md`).

### Assets

| Asset | Sensitivity | Where it lives |
|---|---|---|
| Short-lived HLS path token (`v1.<payload>.<sig>` or JWT-shaped) | Medium (lets anyone fetch that feed’s live audio until expiry) | Process memory / ExoPlayer only. Comment in `BroadcastifyParser.kt`: “Never persist these URLs.” |
| Feed ID list + names | Low–medium (which fire/scanner channels you watch) | `SharedPreferences` `broadcastify-scanner` / `feeds-v1` |
| Live audio | Low–medium (public scanner audio; still not yours to republish) | Decoded in-process; spectrum tap does not write audio to disk |
| Device / app identity | High once sideloaded | Signing cert of the installed APK |

### Trust boundaries

```
[User] --UI--> [MainActivity / ScannerController]
                    |
                    +--> FeedStore (app-private SharedPreferences, Auto Backup)
                    |
                    +--> BroadcastifyClient (OkHttp, system TLS trust)
                    |         |
                    |         v
                    |   broadcastify.com HTML  <== untrusted content
                    |
                    +--> ExoPlayer DefaultHttpDataSource
                              |
                              v
                        hlsUrl host (intended: *.broadcastify.com CDN)
                              == not allowlisted ==
```

| Boundary | Trust assumption | Breaks if… |
|---|---|---|
| Broadcastify / network | TLS to `www.broadcastify.com` and the CDN is honest; HTML `hlsUrl` is theirs | Site compromise, XSS on popout, rogue/user-installed CA, no pinning |
| Local storage | App sandbox; backups go to the user’s Google account / `adb backup` | Backup exfil, rooted device, other apps if backup is extracted |
| IPC | Only `MainActivity` is exported (LAUNCHER). `PlaybackService` is not. | New exported components, FileProvider, deep links (none today) |
| OS permissions | INTERNET + media FGS + optional notifications + WAKE_LOCK | Over-grant later (mic, location, storage) |
| Install identity | Next APK is signed by the same key as 0.1.0 | Debug keystore leak (see A-01) |

### Actors considered

- Network attacker on Wi-Fi / VPN (no device CA install)
- Attacker who can install a user CA (MDM, “trust this cert,” rooted debug)
- On-device malware in another UID
- Someone with GitHub repo / release access (repo is **private**)
- Someone with the debug keystore used to sign 0.1.0
- Broadcastify itself (ToS / HTML change / hostile `hlsUrl`)

### Out of scope

Windows/desktop Python server, browser UI, localhost proxy. Play Store policy. Full Broadcastify ToS legal review. Runtime MITM of live feeds.

---

## Findings

| ID | Severity | Component | Issue | Evidence | Impact | Recommendation |
|---|---|---|---|---|---|---|
| **A-01** | **High** | Build / shipped APK | Release builds are signed with the Android **debug** key. The 0.1.0 APK on disk and in GitHub Releases uses that identity. | `android/app/build.gradle.kts` `signingConfig = signingConfigs.getByName("debug")` (comment: “Personal sideload”). Shipped APK v2 cert: `CN=Android Debug, O=Android, C=US`, SHA-256 `8A:EA:3E:81:EB:26:FF:54:C9:FC:8E:72:7C:E6:A8:76:3C:71:72:67:E6:42:69:E2:49:4D:30:55:A5:8D:F0:52`, notBefore 2026-09-16. JAR/v1 `META-INF/*.RSA` absent (v2-only; fine for `minSdk 26`). README still says `app-release-unsigned.apk`. | Android treats this cert as the app’s vendor identity. The debug keystore password is the well-known `android`. Anyone with `~/.android/debug.keystore` from the build machine can sign malware as an **update** to every installed 0.1.0. Play Protect / enterprise MDM often flags debug-signed packages. You cannot rotate this without uninstall (data loss) or a new `applicationId`. | Generate a dedicated upload/sideload keystore **off-repo**, sign release with it, publish the **cert SHA-256** next to the APK, and keep the keystore out of backups/chat. Do not commit `debug.keystore`. Rebuild 0.1.0 (or 0.1.1) and tell existing testers it is a **fresh install**, not an in-place upgrade. |
| **A-02** | **Medium** | Player | Parsed `hlsUrl` is passed to ExoPlayer with **no host/scheme allowlist**. | `FeedSession.startOrRefresh` → `MediaItem.Builder().setUri(meta.hlsUrl)`. Contrast: popout fetch URL is hardcoded and the feed ID is digits-only (`BroadcastifyClient.kt`). | If popout HTML is attacker-controlled, the app will request and play any `https://` URL ExoPlayer accepts. Impact is spoofed fire/dispatch audio and a client-side GET (SSRF-ish) to an arbitrary host, with the Chrome UA + Broadcastify `Referer`/`Origin`. User-entered IDs cannot point the popout request off-site. | Before `setUri`, require `https` and host `broadcastify.com` or `*.broadcastify.com`. Fail closed. Optionally pin the path to `/t/` + `/playlist.m3u8`. |
| **A-03** | **Medium** (Needs verification) | Network | OkHttp **follows redirects** (incl. SSL) for the popout GET, then parses **whatever body** is returned. | `OkHttpClient.Builder().followRedirects(true).followSslRedirects(true)` in `BroadcastifyClient.kt`. | If `popout.php` ever 302s off-origin (open redirect, geo bounce, future auth), the parser will treat foreign HTML as a Broadcastify popout. Combined with A-02 this becomes “redirect → malicious `hlsUrl` → play.” **Not confirmed** that Broadcastify currently redirects that URL off-site. | `followRedirects(false)`, or re-validate `response.request.url.host` is still `www.broadcastify.com` before reading the body. |
| **A-04** | **Low** | Storage / backup | `allowBackup="true"` (default Auto Backup) + unencrypted `SharedPreferences`. | `AndroidManifest.xml` `android:allowBackup="true"`. No `fullBackupContent` / `dataExtractionRules`. `FeedStore` uses `MODE_PRIVATE` prefs `broadcastify-scanner`: `feeds-v1`, `master-volume`, `keep-awake`. Confirmed in shipped APK merged manifest. | ADB backup, device-to-device transfer, or Google Auto Backup can copy the feed list. No passwords/tokens. Residual privacy: which channels you monitor (and custom names). | Set `allowBackup="false"` and add `dataExtractionRules` excluding prefs, **or** keep backup and accept the feed list as non-secret. EncryptedSharedPreferences is optional here (no credentials). |
| **A-05** | **Low** | TLS | No certificate pinning; default system trust store. No `networkSecurityConfig`. | No `res/xml/network_security_config.xml`. OkHttp constructed with defaults. Shipped APK `networkSecurityConfig=None`. | A user-installed / MDM CA can intercept the popout scrape and swap `hlsUrl` (feeds A-02). A network attacker **without** a trusted CA is stopped by system TLS + `usesCleartextTraffic="false"`. Pinning is optional for a personal app; it also means more breakages when Broadcastify rotates CDN certs. | If you want defense against device-local CAs: pin `www.broadcastify.com` and `*.broadcastify.com` with a backup pin. Otherwise document “trust the system store.” |
| **A-06** | **Low** | Build | R8 / minify is **off** for release. | `isMinifyEnabled = false` in `android/app/build.gradle.kts`. `proguard-rules.pro` is a placeholder. Shipped APK contains three DEX files + `DebugProbesKt.bin`. | Scrape logic and UA string are trivial to recover. There are **no embedded secrets** to hide. Reverse engineering of a personal app is a modest concern. | Turn minify on before any wider distribution. Keep the Media3/OkHttp keep rules already sketched. |
| **A-07** | **Low** | Repo / supply chain | `.gitignore` **un-ignores** `debug.keystore`. Release APKs are committed under `releases/`. | `.gitignore` lines `*.keystore` / `!debug.keystore` and `!releases/*.apk`. | A future accidental commit of the debug keystore would make A-01 trivial for anyone with repo access. A committed APK can be swapped in a later commit; consumers have no published checksum. Repo is currently **private**, which limits blast radius. | Remove the `!debug.keystore` exception. Publish SHA-256 of each APK in the README/release notes. Prefer GitHub Releases only (already used) over git-LFS binaries if the file will churn. |
| **A-08** | **Low** | Player | ExoPlayer allows **cross-protocol** redirects. | `DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)` in `FeedSession.kt`. | HTTPS→HTTP redirects would be blocked by `usesCleartextTraffic="false"` (and `ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED` is treated as a token failure). Residual risk is HTTPS→HTTPS off-host if the CDN redirects (overlaps A-02). | Disable cross-protocol redirects. Allowlist final host the same as A-02. |
| **A-09** | **Info** | Merged manifest | AndroidX `ProfileInstallReceiver` is `exported="true"` and permission-guarded with `DUMP`. | Shipped APK: `androidx.profileinstaller.ProfileInstallReceiver`, `exported=true`, `permission=android.permission.DUMP`. Not in the app’s hand-written manifest. | `DUMP` is a signature/privileged permission. Third-party apps cannot send this. Baseline AndroidX behavior. | No action unless you want to strip profileinstaller from release. |
| **A-10** | **Info** | Policy / ToS | Client impersonates Chrome 120 and scrapes HTML instead of a public API. | `BroadcastifyHttp.USER_AGENT` in `BroadcastifyClient.kt`; same headers on ExoPlayer. README documents the scrape. | Not a memory-safety bug. Broadcastify can break the parser, rate-limit, or treat this as ToS abuse. Free feeds also prepend preroll (documented). | Keep using it as a personal listener; do not ship as a product without a license. Prefer an official API if one appears. |
| **A-11** | **Info** | Supply chain | Gradle wrapper URL is HTTPS and `validateDistributionUrl=true`, but **no** `distributionSha256Sum`. No CI, no Dependabot/OSV. | `android/gradle/wrapper/gradle-wrapper.properties`. `gh` reports **zero** Actions workflows. Repos: `google()`, `mavenCentral()`, `FAIL_ON_PROJECT_REPOS`. | Wrapper-jar + unsigned distribution is a residual Gradle supply-chain class. Dependencies are mainstream AndroidX / Media3 1.8.0 / OkHttp 4.12.0. Spot-check: Snyk/Sonatype list **no direct CVEs** for OkHttp 4.12.0 (4.12.0 also pulled Okio 3.6.0, past CVE-2023-3635). This is **not** a full SCA. | Set `distributionSha256Sum` from Gradle’s official checksum. Add Dependabot or `osv-scanner` later if the repo grows. |
| **A-12** | **Info** | Updates | No in-app updater, no Play App Signing. Next version is “download another APK.” | README install steps; no `PackageInstaller` / Play core. | Trust of updates **is** A-01’s cert. There is no separate “download EXE and run” path on Android. | After a real release key exists, document the fingerprint and a single download URL. |
| **A-13** | **Info** (Needs verification) | Logging | App code does not call `Log.*`. Media3/OkHttp **may** still print request URLs (hence tokens) to logcat. | No `android.util.Log` in app Kotlin. HLS URLs contain the path token (`BroadcastifyParser.kt`). | Third-party apps cannot read logcat on modern unrooted devices. USB `adb logcat` / a corporate MDM logger could capture a live token. Tokens are short-lived. | Needs a logcat pass while a feed is playing. If URLs appear, raise Media3 log level in release or enable R8 (A-06). |

---

## Android surface checklist

| Check | Result |
|---|---|
| Dangerous / runtime permissions | **`POST_NOTIFICATIONS` only** (API 33+), requested in `MainActivity`, treated as optional. No location, mic, contacts, SMS, storage, camera. |
| Other permissions | `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`. Merged: `ACCESS_NETWORK_STATE` (libraries). |
| Exported activity | `MainActivity` `exported="true"` with **MAIN/LAUNCHER only**. Does not read `intent.data` / extras. |
| Exported service | `PlaybackService` **`exported="false"`**. `ACTION_STOP` is same-app only. Notification `PendingIntent` uses `FLAG_IMMUTABLE`. |
| Deep links / App Links / custom schemes | **None** |
| FileProvider / content providers (app-owned) | **None**. Merged `androidx.startup.InitializationProvider` is `exported="false"`. |
| WebView / `addJavascriptInterface` / `javascript:` | **None** (Compose + `AndroidView` only for `SpectrumView`) |
| `allowBackup` | **true** (A-04) |
| `usesCleartextTraffic` | **false** (confirmed in shipped APK) |
| `android:debuggable` | **unset** on release APK (defaults false). Debug build uses `applicationId` suffix `.debug`. |
| ProGuard / R8 | **Disabled** (A-06) |
| Network security config / pinning | **None** (A-05) |
| Cookies / Credential Manager / AccountManager | **None**. OkHttp default is `CookieJar.NO_COOKIES`. |
| IPC between Android and desktop | **None** in the Android tree |

---

## Secrets and credentials

| Class | Present? | Notes |
|---|---|---|
| API keys | No | No Broadcastify key, no Maps, no Firebase, no `google-services.json` |
| Passwords / account session | No | README: premium/logged-in sessions not implemented |
| Cookies | No | Not stored, not sent (beyond what Broadcastify might set on a response that OkHttp then drops) |
| HLS path tokens | Yes, **ephemeral** | Parsed each Play/Reconnect; not written in `FeedStore.saveFeeds` (only `feedId` + `name`) |
| Hardcoded live `hlsUrl` | No | Tests use `PAYLOAD.SIG` placeholders (`BroadcastifyParserTest.kt`). README forbids committing live URLs. |
| Signing material in git | No keystore file today | Exception in `.gitignore` is the risk (A-07) |
| CI secrets | N/A | No workflows |

**Account takeover of a Broadcastify user session via this app: not applicable** in 0.1.0. The token in the HLS path is a **CDN capability URL**, not an account cookie. Stealing it lets someone listen to that feed until it expires; it does not reset a password or take over RadioReference.

---

## Data at rest and privacy

| Data | Stored? | Encrypted at rest? | Backup? |
|---|---|---|---|
| Feed IDs + names | Yes, JSON in SharedPreferences | No (sandbox only) | Yes if Auto Backup / `adb backup` (A-04) |
| Master volume, keep-awake | Yes | No | Yes |
| HLS URL / token | No | — | — |
| Audio / recordings | No | Spectrum is in-memory PCM tap (`SpectrumAudioProcessor.kt`) | — |
| Device location | No | Default feed **names** mention Tahoe / CAL FIRE geography; that is catalog text, not GPS | — |
| PII | None collected | Custom feed **names** are whatever the user types | Same as feed list |

No analytics SDK, crash reporter, or advertising ID usage was found.

---

## Supply chain

| Item | Status |
|---|---|
| Gradle | 8.11.1 from `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip`, `validateDistributionUrl=true`, no SHA-256 pin (A-11) |
| AGP / Kotlin | 8.7.3 / 2.0.21 |
| Repositories | `google()`, `mavenCentral()` only (`FAIL_ON_PROJECT_REPOS`) |
| Direct deps | AndroidX / Compose BOM 2024.12.01, Media3 1.8.0, OkHttp 4.12.0, coroutines 1.9.0, JUnit 4.13.2 |
| Known CVE on OkHttp 4.12.0 | None listed by Snyk/Sonatype at review time (not a substitute for continuous SCA) |
| CI | None — no Actions secrets to leak; also no automated signing or reproducibility |
| Shipped APK | Debug-signed (A-01), in git + private GitHub Release |
| Wrapper JAR | Standard committed `gradle-wrapper.jar` (trust-on-first-clone, as with every Gradle project) |

---

## Abuse cases (Android)

| Scenario | Feasible? | Notes |
|---|---|---|
| **Broadcastify account takeover** | **No** (current code) | No login, no cookie store, no password field. Do not add “paste your session cookie” later without EncryptedSharedPreferences + no backup. |
| **Audio exfil of a live token** | Limited | Token is memory-only. Another app cannot read it without root, debugger, or (Needs verification) logcat. A network MITM without a trusted CA should fail TLS. On-device malware can scrape Broadcastify **itself** the same way this app does; it does not need this APK. |
| **Malicious feed URL typed by the user** | **No** as a URL | UI and `addFeed` keep **digits only** (`take(12)` in the UI). User cannot paste `https://evil/...`. Residual is A-02 (HTML-supplied URL). |
| **Fake fire dispatch audio** | **Yes**, if A-02’s HTML/TLS assumption breaks | User hears attacker-controlled “traffic.” Treat as the integrity risk for a fire-ground listener. |
| **Local malware reads stored creds** | N/A | No creds. Malware with backup/`run-as`/root can read feed IDs. |
| **Trojan “update” APK** | **Yes**, if debug keystore leaks (A-01) | Highest practical impact for anyone who already installed 0.1.0. |
| **Other apps start/stop playback** | **No** | Service not exported; no exported broadcast for `ACTION_STOP`. |
| **Intent / deep-link injection** | **No** | Launcher activity ignores extras. |

---

## Positive controls already in place

- `usesCleartextTraffic="false"` in source and in the shipped APK.
- `targetSdk 35` / `minSdk 26` — modern permission and export defaults.
- `PlaybackService` not exported; notification tap uses `FLAG_IMMUTABLE`.
- No WebView, no JS bridge, no FileProvider, no custom URL schemes.
- Feed identifiers sanitized to digits before the popout URL is built (`filter { it.isDigit() }` in client, parser, store, and controller).
- HLS tokens intentionally **not** persisted; parser and UI copy say so.
- OkHttp default client has **no cookie jar** and no custom `TrustManager` / hostname verifier (no “trust all certs” anti-pattern).
- Compose `Text` for feed names (not a WebView), so scraped titles are not an HTML XSS sink.
- Minimal permission set; notification permission is optional.
- Debug `applicationId` suffix so a debug install does not clobber the sideload package (the **release** key is still debug — A-01).
- Dependency repos locked down; no random GitHub JitPack/flatDir.
- Private GitHub repository; no Actions, so no CI secret sprawl.
- Unit tests use fake tokens, not live CDN URLs.

---

## Suggested fix priority

1. **Do before anyone else sideloads another build:** create a real release keystore (offline), sign with it, publish the cert fingerprint, drop the debug `signingConfig` for `release`. Treat 0.1.0 → next as a reinstall. (A-01, A-07, A-12)
2. **Cheap defense-in-depth (half a day):** allowlist `hlsUrl` host + `https`; stop or re-check OkHttp redirects; turn off cross-protocol redirects. (A-02, A-03, A-08)
3. **Privacy nits:** `allowBackup="false"` (or an exclusion rule) if you do not want the feed list in Google backup. (A-04)
4. **Before any non-personal distribution:** enable R8; add `distributionSha256Sum`; Dependabot; decide on pinning. (A-06, A-11, A-05)
5. **If you add Broadcastify login later:** that is a **new threat model**. Encrypted storage, no backup of session cookies, no logcat of `Cookie` headers, and a real ToS/API path. Do not scrape authenticated pages with a stored password.

---

## Reviewer notes

- Findings are cited to files or to measurements on `releases/broadcastify-scanner-0.1.0.apk` (SHA-256 `3f9118764d4065688c4cb6bb0dd4d246d8650063a10326db9d21b6933ba4f7f0`).
- “Needs verification” means the code pattern is real but the exploit depends on Broadcastify redirect behavior (A-03) or library logcat (A-13), which were not exercised live in this pass.
- Desktop/Windows was excluded per scope change and is not covered here.
