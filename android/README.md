# Android app

Assembleable Gradle project. Application id `com.clintmaples.broadcastifyscanner`, display name **Somersett Fire Radio Scanner**, **versionName 0.3.0**, **versionCode 3**.

```bash
./gradlew assembleRelease
./gradlew test
```

On Windows: `gradlew.bat assembleRelease`.

Release signing is **not** the Android Debug key. Copy [`keystore.properties.example`](keystore.properties.example) to `keystore.properties` (gitignored) or set `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`. Generate the PKCS12 keystore off-repo — see the root README. Without those values, `assembleRelease` still compiles an unsigned APK.

Never commit `*.keystore`, `keystore.properties`, or APKs.

See the repo root README for APK install, HLS token scrape, and UX notes. Android remediations: [`../SECURITY_REVIEW.md`](../SECURITY_REVIEW.md).
