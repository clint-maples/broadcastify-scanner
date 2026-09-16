# Android security review — Somersett Fire Radio Scanner

**Audience:** Clint (SVP/CISO)  
**Scope:** Android app only (`android/`). Desktop / Windows code was not reviewed.  
**Original review:** 2026-09-16 against `87323cc` / shipped `releases/broadcastify-scanner-0.1.0.apk` (debug-signed). Report-only: [PR #3](https://github.com/clint-maples/somersett-fire-radio-scanner/pull/3).  
**Remediation:** 0.3.0 security release. Repo: `https://github.com/clint-maples/somersett-fire-radio-scanner`  
**Method:** Source review of Kotlin, manifest, Gradle, and unit tests; static inspection of the 0.1.0 APK. No dynamic instrumented tests, no MITM lab, no Play Console review.

No Broadcastify login, cookies, passwords, or API keys — that remains a hard product constraint.

---

## Remediation status (0.3.0)

| ID | Severity | Status | What shipped |
|---|---|---|---|
| **A-01** | High | **Fixed** | Release `signingConfig` no longer uses the Android Debug cert. Signing is `android/keystore.properties` (gitignored) or `RELEASE_STORE_*` env. Keystore generation is documented off-repo. Missing credentials → **unsigned** APK, never debug-signed. |
| **A-02** | Medium | **Fixed** | `hlsUrl` must be `https` on `broadcastify.com` / `*.broadcastify.com` (port 443, no userinfo). Parser and player fail closed. |
| **A-03** | Medium | **Fixed** | OkHttp still follows same-origin HTTPS redirects, but a network interceptor re-validates every hop and `Location` before follow. Final popout URL is checked again before the body is parsed. |
| **A-04** | Low | **Fixed** | `android:allowBackup="false"` plus `dataExtractionRules` / `fullBackupContent` excludes. |
| **A-05** | Low | **Hardened (no pin)** | `networkSecurityConfig` disables cleartext and trusts the **system** store only (no user CAs). Pinning still omitted to avoid CDN-cert breakage. |
| **A-06** | Low | **Fixed** | R8 minify + resource shrink on for release, with Media3/OkHttp keep rules. |
| **A-07** | Low | **Fixed** | `!debug.keystore` and `!releases/*.apk` exceptions removed. Committed 0.1.0/0.2.0 APKs deleted from git. `*.keystore` / `keystore.properties` / `*.apk` stay ignored. |
| **A-08** | Low | **Fixed** | Player HTTP goes through the same OkHttp client + allowlist interceptor. HTTP / off-origin / cross-protocol hops are rejected (cleartext remains off). |
| **A-09** | Info | No action | AndroidX `ProfileInstallReceiver` + `DUMP`. |
| **A-10** | Info | Accepted | Chrome UA + HTML scrape; personal listener only. |
| **A-11** | Info | Deferred | Gradle `distributionSha256Sum` / Dependabot still optional. |
| **A-12** | Info | **Documented** | After the real key exists, publish cert SHA-256 next to the GitHub Release APK. 0.3.0 is a **fresh install** vs debug-signed 0.1.0/0.2.0. |
| **A-13** | Info | Residual | App code still avoids `Log.*`. R8 may reduce library log surface. Tokens stay out of exception messages. |

**Not in git:** keystores, `keystore.properties`, passwords, APKs, live `hlsUrl`s.

---

## Executive summary

The Android app is a **local, unofficial Broadcastify listener**. It scrapes `popout.php` for a short-lived HLS path token, then plays that playlist with Media3/ExoPlayer. There is **no Broadcastify login, cookie jar, password, or API key**.

**Overall residual risk after 0.3.0 remediations: Low** for a personal sideload — provided the release keystore stays off-repo and 0.3.0 is signed with it before anyone else installs.

The 0.1.0 review rated the then-current tree **Medium**, driven by debug-signed sideloads (A-01) and an unbounded playback URL (A-02). Those are closed in source. Existing 0.1.0/0.2.0 installs still carry the debug identity until they are uninstalled.

**Top residual risks**

1. **Key hygiene (was A-01).** Anyone with the *new* release keystore can still ship updates. Keep it offline.
2. **Broadcastify HTML / TLS (A-02 mitigated).** A compromise of Broadcastify itself can still serve an allowlisted `*.broadcastify.com` URL. Off-site hosts no longer play.
3. **No cert pinning (A-05).** System-store TLS + no user CAs. A rogue system CA is out of scope for a personal app.

---

## Threat model

### What the app does

| Step | Behavior | Evidence |
|---|---|---|
| Configure | User adds numeric Broadcastify feed IDs (defaults 14826 / 47365 / 47367). | `ScannerScreen.kt` filters input to digits; `DefaultFeeds` in `Feed.kt` |
| Resolve | `GET https://www.broadcastify.com/listen/feed/popout.php?feedId=<digits>` with a desktop Chrome User-Agent. Regex-parse `hlsUrl` / `feedName`. Redirects and the final URL must stay on allowlisted Broadcastify hosts. | `BroadcastifyClient.kt`, `BroadcastifyAllowlist.kt` |
| Play | Media3 ExoPlayer loads the HLS URL only after the same allowlist check, via OkHttp (no cross-protocol redirects). On 401/403/parse errors, wait 4s and scrape again. | `FeedSession.kt` |
| Persist | Feed ID + display name, master volume, keep-awake flag. **Not** the HLS URL. Auto Backup off. | `FeedStore.kt`, manifest |
| Background | Non-exported `mediaPlayback` foreground service + optional `POST_NOTIFICATIONS`. | `PlaybackService.kt`, `AndroidManifest.xml` |

Premium / logged-in Broadcastify sessions are explicitly **not implemented** (root `README.md`).

### Assets

| Asset | Sensitivity | Where it lives |
|---|---|---|
| Short-lived HLS path token (`v1.<payload>.<sig>` or JWT-shaped) | Medium (lets anyone fetch that feed’s live audio until expiry) | Process memory / ExoPlayer only. Comment in `BroadcastifyParser.kt`: “Never persist these URLs.” |
| Feed ID list + names | Low–medium (which fire/scanner channels you watch) | `SharedPreferences` `broadcastify-scanner` / `feeds-v1` (not backed up) |
| Live audio | Low–medium (public scanner audio; still not yours to republish) | Decoded in-process; spectrum tap does not write audio to disk |
| Device / app identity | High once sideloaded | Signing cert of the installed APK (must be the off-repo release key for 0.3.0) |

### Trust boundaries

```
[User] --UI--> [MainActivity / ScannerController]
                    |
                    +--> FeedStore (app-private SharedPreferences, backup disabled)
                    |
                    +--> BroadcastifyClient (OkHttp, system TLS, redirect allowlist)
                    |         |
                    |         v
                    |   broadcastify.com HTML  <== untrusted content
                    |         |
                    |         +-- hlsUrl must be https + *.broadcastify.com
                    |
                    +--> ExoPlayer OkHttpDataSource (same allowlist client)
                              |
                              v
                        allowlisted *.broadcastify.com HLS
```

| Boundary | Trust assumption | Breaks if… |
|---|---|---|
| Broadcastify / network | TLS to `www.broadcastify.com` and the CDN is honest; HTML `hlsUrl` is theirs **and** on `*.broadcastify.com` | Site compromise or XSS on popout that still points at a Broadcastify host; rogue **system** CA |
| Local storage | App sandbox; backups disabled | Rooted device, `run-as`, physical extraction |
| IPC | Only `MainActivity` is exported (LAUNCHER). `PlaybackService` is not. | New exported components, FileProvider, deep links (none today) |
| OS permissions | INTERNET + media FGS + optional notifications + WAKE_LOCK | Over-grant later (mic, location, storage) |
| Install identity | Next APK is signed by the off-repo release key | Release keystore leak; testers still on debug-signed 0.1.0/0.2.0 |

### Actors considered

- Network attacker on Wi-Fi / VPN (no device CA install)
- Attacker who can install a user CA (blocked by `networkSecurityConfig` system-only trust)
- On-device malware in another UID
- Someone with GitHub repo / release access
- Someone with the **release** keystore (or, for leftover 0.1.0/0.2.0 installs, the debug keystore)
- Broadcastify itself (ToS / HTML change / hostile but still-on-CDN `hlsUrl`)

### Out of scope

Windows/desktop Python server, browser UI, localhost proxy. Play Store policy. Full Broadcastify ToS legal review. Runtime MITM of live feeds.

---

## Findings (original 0.1.0 review)

The table below is the 0.1.0 report. Status after 0.3.0 is in [Remediation status](#remediation-status-030).

| ID | Severity | Component | Issue | Evidence | Impact | Recommendation |
|---|---|---|---|---|---|---|
| **A-01** | **High** | Build / shipped APK | Release builds are signed with the Android **debug** key. The 0.1.0 APK on disk and in GitHub Releases uses that identity. | Was: `signingConfig = signingConfigs.getByName("debug")`. Shipped 0.1.0 APK v2 cert: `CN=Android Debug, O=Android, C=US`, SHA-256 `8A:EA:3E:81:EB:26:FF:54:C9:FC:8E:72:7C:E6:A8:76:3C:71:72:67:E6:42:69:E2:49:4D:30:55:A5:8D:F0:52`. | Anyone with `~/.android/debug.keystore` can sign malware as an **update** to every installed 0.1.0/0.2.0. | Dedicated upload keystore **off-repo**; never debug-sign release; 0.3.0 is a reinstall. |
| **A-02** | **Medium** | Player | Parsed `hlsUrl` is passed to ExoPlayer with **no host/scheme allowlist**. | Was: `MediaItem.Builder().setUri(meta.hlsUrl)` with no check. | Attacker-controlled popout HTML could play any HTTPS URL (fake dispatch audio / client GET). | Require `https` + `broadcastify.com` / `*.broadcastify.com`. Fail closed. |
| **A-03** | **Medium** (Needs verification) | Network | OkHttp **follows redirects** then parses **whatever body** is returned. | Was: `followRedirects(true)` with no final-host check. | Open redirect → foreign HTML → malicious `hlsUrl`. | Re-validate each hop / final URL. |
| **A-04** | **Low** | Storage / backup | `allowBackup="true"` + unencrypted `SharedPreferences`. | Was: manifest default Auto Backup. Feed IDs/names only. | Backup can copy the feed list. No credentials. | `allowBackup="false"`. |
| **A-05** | **Low** | TLS | No certificate pinning; default system trust store. No `networkSecurityConfig`. | Was: no `res/xml/network_security_config.xml`. | User-installed CA could MITM the popout (feeds A-02). | Document system store; optional pin. 0.3.0: system-only anchors + no cleartext. |
| **A-06** | **Low** | Build | R8 / minify is **off** for release. | Was: `isMinifyEnabled = false`. | No embedded secrets; modest RE concern. | Turn minify on. |
| **A-07** | **Low** | Repo / supply chain | `.gitignore` **un-ignores** `debug.keystore`. Release APKs committed under `releases/`. | Was: `!debug.keystore` and `!releases/*.apk`. | Accidental keystore commit; APK swap in git. | Remove exceptions; GitHub Releases only. |
| **A-08** | **Low** | Player | ExoPlayer allows **cross-protocol** redirects. | Was: `setAllowCrossProtocolRedirects(true)`. | Residual HTTPS→HTTPS off-host if CDN redirected (overlaps A-02). | Disable; allowlist final host. |
| **A-09** | **Info** | Merged manifest | AndroidX `ProfileInstallReceiver` is `exported="true"` and permission-guarded with `DUMP`. | Library default. | `DUMP` is signature/privileged. | No action. |
| **A-10** | **Info** | Policy / ToS | Client impersonates Chrome 120 and scrapes HTML instead of a public API. | `BroadcastifyHttp.USER_AGENT`. | Breakage / ToS, not memory safety. | Personal listener only. |
| **A-11** | **Info** | Supply chain | Gradle wrapper HTTPS + `validateDistributionUrl=true`, no `distributionSha256Sum`. No CI. | `gradle-wrapper.properties`. | Residual wrapper class. | Optional checksum later. |
| **A-12** | **Info** | Updates | No in-app updater, no Play App Signing. | README install steps. | Trust of updates **is** the signing cert. | Document fingerprint + one download URL. |
| **A-13** | **Info** (Needs verification) | Logging | App code does not call `Log.*`. Libraries **may** print URLs to logcat. | No `android.util.Log` in app Kotlin. | USB `adb logcat` could capture a live token. | R8; keep tokens out of app exceptions. |

---

## Android surface checklist (0.3.0 source)

| Check | Result |
|---|---|
| Dangerous / runtime permissions | **`POST_NOTIFICATIONS` only** (API 33+), optional. No location, mic, contacts, SMS, storage, camera. |
| Other permissions | `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`. |
| Exported activity | `MainActivity` `exported="true"` with **MAIN/LAUNCHER only**. Does not read `intent.data` / extras. |
| Exported service | `PlaybackService` **`exported="false"`**. |
| Deep links / App Links / custom schemes | **None** |
| FileProvider / content providers (app-owned) | **None** |
| WebView / `addJavascriptInterface` | **None** |
| `allowBackup` | **false** (A-04) |
| `usesCleartextTraffic` | **false** + `networkSecurityConfig` |
| `android:debuggable` | unset on release (defaults false). Debug `applicationId` suffix `.debug`. |
| ProGuard / R8 | **Enabled** for release (A-06) |
| Network security config / pinning | Cleartext off, system anchors only. **No pin** (A-05 accepted). |
| HLS / popout hosts | Allowlisted (A-02, A-03, A-08) |
| Release signing | Off-repo keystore / env only (A-01) |
| Cookies / Credential Manager / AccountManager | **None**. OkHttp default is `CookieJar.NO_COOKIES`. |
| Broadcastify login | **None** |

---

## Secrets and credentials

| Class | Present? | Notes |
|---|---|---|
| API keys | No | No Broadcastify key, no Maps, no Firebase, no `google-services.json` |
| Passwords / account session | No | README: premium/logged-in sessions not implemented |
| Cookies | No | Not stored |
| HLS path tokens | Yes, **ephemeral** | Parsed each Play/Reconnect; not written in `FeedStore` |
| Hardcoded live `hlsUrl` | No | Tests use `PAYLOAD.SIG` placeholders |
| Signing material in git | **No** | `*.keystore`, `keystore.properties` gitignored; example file has empty passwords |
| CI secrets | N/A | No workflows |
| Committed APKs | **No** (removed in 0.3.0) | Historical 0.1.0 APK SHA-256 was `3f9118764d4065688c4cb6bb0dd4d246d8650063a10326db9d21b6933ba4f7f0` |

**Account takeover of a Broadcastify user session via this app: not applicable.** The HLS path token is a CDN capability URL, not an account cookie.

---

## Data at rest and privacy

| Data | Stored? | Encrypted at rest? | Backup? |
|---|---|---|---|
| Feed IDs + names | Yes, JSON in SharedPreferences | No (sandbox only) | **No** (`allowBackup=false`) |
| Master volume, keep-awake | Yes | No | **No** |
| HLS URL / token | No | — | — |
| Audio / recordings | No | Spectrum is in-memory PCM tap | — |
| Device location | No | Default feed **names** mention Tahoe / CAL FIRE geography; catalog text, not GPS | — |
| PII | None collected | Custom feed **names** are whatever the user types | Same as feed list |

No analytics SDK, crash reporter, or advertising ID usage was found.

---

## Supply chain

| Item | Status |
|---|---|
| Gradle | 8.11.1 from `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip`, `validateDistributionUrl=true`, no SHA-256 pin (A-11 deferred) |
| AGP / Kotlin | 8.7.3 / 2.0.21 |
| Repositories | `google()`, `mavenCentral()` only (`FAIL_ON_PROJECT_REPOS`) |
| Direct deps | AndroidX / Compose BOM 2024.12.01, Media3 1.8.0 (+ `media3-datasource-okhttp`), OkHttp 4.12.0, coroutines 1.9.0, JUnit 4.13.2 |
| CI | None |
| Shipped APK in git | **Removed.** Sign locally; attach to GitHub Releases. |
| Release signing | `keystore.properties` / env; debug key is not a release fallback |

---

## Abuse cases (Android)

| Scenario | Feasible after 0.3.0? | Notes |
|---|---|---|
| **Broadcastify account takeover** | **No** | No login, no cookie store, no password field. Do not add “paste your session cookie” later without EncryptedSharedPreferences + no backup. |
| **Audio exfil of a live token** | Limited | Token is memory-only. |
| **Malicious feed URL typed by the user** | **No** as a URL | UI keeps **digits only**. Residual is a Broadcastify-hosted `hlsUrl` (A-02 mitigated). |
| **Fake fire dispatch audio** | Only if Broadcastify (or an allowlisted host) serves it | Off-site hosts are rejected. |
| **Local malware reads stored creds** | N/A | No creds. |
| **Trojan “update” APK** | Only if the **release** keystore leaks, or the target is still on debug-signed 0.1.0/0.2.0 | Uninstall old debug-signed copies. |
| **Other apps start/stop playback** | **No** | Service not exported. |
| **Intent / deep-link injection** | **No** | Launcher activity ignores extras. |
| **Off-origin popout redirect** | **No** | Interceptor + final-URL check (A-03). |

---

## Positive controls (0.3.0)

- `usesCleartextTraffic="false"` and `networkSecurityConfig` (system trust, no cleartext).
- `targetSdk 35` / `minSdk 26`.
- `PlaybackService` not exported; notification tap uses `FLAG_IMMUTABLE`.
- No WebView, no JS bridge, no FileProvider, no custom URL schemes.
- Feed identifiers sanitized to digits before the popout URL is built.
- HLS tokens not persisted; allowlisted before play.
- OkHttp has no cookie jar and no custom `TrustManager`.
- Compose `Text` for feed names (not a WebView).
- Minimal permission set; notification permission is optional.
- Debug `applicationId` suffix so a debug install does not clobber the sideload package.
- Release builds are unsigned or release-key-signed — **never** debug-signed.
- Dependency repos locked down.
- Unit tests cover allowlist accepts/rejects and parser rejection of off-origin / HTTP / lookalike hosts.

---

## Suggested remaining work

1. **Clint, before anyone sideloads 0.3.0:** generate the release keystore offline, put `keystore.properties` on the build machine, assemble, publish the APK + cert SHA-256 on GitHub Releases, tell testers to uninstall 0.1.0/0.2.0 first. (A-01, A-12)
2. Optional later: `distributionSha256Sum`, Dependabot, pinning. (A-11, A-05)
3. **If you add Broadcastify login later:** that is a **new threat model**. Encrypted storage, no backup of session cookies, no logcat of `Cookie` headers, and a real ToS/API path.

---

## Reviewer notes

- Original measurements cited `releases/broadcastify-scanner-0.1.0.apk` (SHA-256 `3f9118764d4065688c4cb6bb0dd4d246d8650063a10326db9d21b6933ba4f7f0`). Those binaries were removed from git in the 0.3.0 remediation.
- “Needs verification” on A-03 meant Broadcastify’s live redirect behavior was not exercised; the code now fail-closes regardless.
- Desktop/Windows remains out of scope.
