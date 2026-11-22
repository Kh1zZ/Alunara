# Alunara

**Alunara** (`com.alunara.app`) is an independent, audio-only YouTube Music client built specifically for entry-level **Android TV** devices running **Android 9 (API 28)+** with **1 GB RAM** (reference hardware: **Changhong L32G7N**).

> **Current Status:** `Milestone M5 — App Signing, R8 Release Optimization & Beta CI/CD (0.1.0-beta.1)`  
> M5 introduces externalized Android release signing (`keystore.properties` / `ALUNARA_KEYSTORE_*` + `scripts/generate-keystore.ps1`), R8 bytecode minification + resource shrinking + ARM ABI filtering (`armeabi-v7a`, `arm64-v8a`), shrinking the APK from **`89.07 MiB` (Debug)** to **`20.83 MiB` (Release)**, alongside automated GitHub Actions CI & Beta Pre-Release workflows (`.github/workflows/ci.yml`, `.github/workflows/release-beta.yml`). Verified with `79 / 79` unit tests across `18` suites, `0` touched lint issues, and live on physical Changhong `AI PONT` Android 11 TV (`192.168.100.124:5555`: R8 release cold start `1,784 ms`, idle PSS `64.15 MiB`, active dual-line JP Kuromoji + 20% blur PSS `90.43 MiB`, `0` logcat errors); see [docs/M5_RELEASE_REPORT.md](docs/M5_RELEASE_REPORT.md).

---

## Module Architecture (Modular-Lite)

- `:app` — Android TV shell (`com.alunara.app`), Fixed Dark Forest Green Material 3 TV theme, collapsible 3-item D-pad rail + Header Search Bar, `AlunaraPlaybackService` (`MediaSessionService`) + `SinglePlayerLifecycleController` (Media3 audio-only + audio focus), ID/EN strings, and in-app open-source notices.
- `:core:domain` — Pure Kotlin models, `QueueReducer`, `RetryPolicy` (bounded backoff + skip-loop guard), `LrcParser`, `ResourceBudgetPolicy`, and engine contracts.
- `:core:music` — Isolation adapter (`ArchiveTuneMusicAdapter`) over vendored `third_party/core/`, lazily unlocking `NetworkGatekeeperControl` and normalizing guest search results.
- `:core:lyrics` — Isolation adapter (`ArchiveTuneLyricsAdapter`) over vendored `third_party/lyrics/{lrclib,simpmusic,kugou}` with max-2 bounded concurrency.
- `:core:data` — Room database v1 (`AlunaraDatabase`, exported schemas in `core/data/schemas/`), `RoomQueueStore`, account-scoped favorites/history DAOs, and `CacheBudgetManager`.
- `:third_party:core` & `:third_party:lyrics:{lrclib,simpmusic,kugou}` — Pinned upstream GPL-3.0 base engines (see [third_party/VENDORING.md](third_party/VENDORING.md)).

---

## Build & Test (Windows)

Requires **JDK 21** (vendored base engines target `jvmToolchain(21)`) and Android SDK configured in `local.properties` (`sdk.dir=...`):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Run all unit tests
.\gradlew.bat :core:domain:test :core:music:test :core:lyrics:test :core:data:testDebugUnitTest :app:testDebugUnitTest

# Run Android lint & build Debug / R8 Release APK
.\gradlew.bat :app:lintDebug :app:assembleDebug :app:assembleRelease
```

### Release Signing & Beta CI/CD (M5)
1. **Generate a local release keystore (optional for local release testing; git-ignored):**
   ```powershell
   .\scripts\generate-keystore.ps1 -WriteProperties
   ```
   Or copy [keystore.properties.example](keystore.properties.example) to `keystore.properties` (never commit `.jks` or `keystore.properties`). Without a keystore configured, `:app:assembleRelease` falls back to the debug certificate for local ADB sideload testing while keeping full R8 + Resource Shrinking active.
2. **GitHub Actions CI/CD:**
   - [.github/workflows/ci.yml](.github/workflows/ci.yml) runs secret/integrity checks, all 18 unit test suites, `:app:lintDebug`, and `:app:assembleDebug :app:assembleRelease` on every push/PR to `main`.
   - [.github/workflows/release-beta.yml](.github/workflows/release-beta.yml) runs on tags `v*` or manual dispatch, signing the R8 APK using repository secrets (`ALUNARA_KEYSTORE_BASE64`, `ALUNARA_KEYSTORE_PASSWORD`, `ALUNARA_KEY_ALIAS`, `ALUNARA_KEY_PASSWORD`) — or an ephemeral CI keystore if secrets are not set — and publishing a GitHub Pre-Release with `SHA256SUMS.txt`.

---

## Architecture & Product Baseline

Alunara is built around one core rule: **stable audio playback takes priority over auxiliary work**.

### Locked Technical Baseline
- **Music Backend Base Engine:** [ArchiveTune Core](https://github.com/rukamori/core) (mandatory integration via thin adapter, pinned at M0 audit).
- **Lyrics Backend Base Engine:** [ArchiveTune Lyrics](https://github.com/rukamori/lyrics) (mandatory integration via thin adapter with selective provider activation and max 2 concurrent hedged requests).
- **Audio Playback:** AndroidX Media3 / ExoPlayer (`audio-only`, single active player instance per process), pending M0/M1 compatibility and compliance verification.
- **Lifecycle Policy:** **Stop immediately on exit** (Home / leaving the app) — no background playback; queue, track index, and playback position are persisted cleanly.
- **Storage & Resource Budget:** Dynamic metadata + artwork + lyrics disk cache capped at **128 MB aggregate**; **32 MB audio disk cache disabled by default** until policy compliance is verified.
- **Auth & Isolation:** Guest mode required; maximum **one active account** target for Beta, strictly isolated from guest data and credentials protected via Android Keystore (subject to FG-04 feasibility gate).

### Locked UI/UX Design Baseline (Android TV 16:9)
- **Design System:** Material Design 3 **for TV**, **Fixed Dark Mode Only** with **Forest Green** palette (no dynamic/wallpaper/artwork theming):
  - Background: `#101813` | Surface: `#1D2B22` | Primary Accent: `#85BDA0` | Primary Text: `#E3EEE5`
- **Navigation & Focus:** Remote-first (D-pad / OK / Back) with a collapsible left navigation rail (`Home`, `Search`, `Library`, `Settings`), high-contrast focus outlines (no scale-only focus), and predictable focus restoration.
- **Now Playing & Lyrics:** Balanced artwork + persistent **read-only** lyrics panel (not focusable/clickable), explicit **Original / Romanized** toggle when supported, plain-lyrics fallback, and responsive space reclamation when unavailable.
- **Localization & Accessibility:** Bahasa Indonesia and English UI (`lyrics` stay in source language), CJK font coverage (Latin + Japanese + Korean), high-contrast and text-size options, and reduced-motion support.

---

## Attribution & Independence

> Alunara is an independent YouTube Music client developed for Android TV. Its music integration is built on [ArchiveTune Core](https://github.com/rukamori/core) and its lyrics integration is built on [ArchiveTune Lyrics](https://github.com/rukamori/lyrics), open-source projects by [rukamori](https://github.com/rukamori). Alunara's application layer and Android TV frontend are independently developed. Alunara is not officially affiliated with or endorsed by ArchiveTune.

Alunara is licensed under the **GNU General Public License v3.0 (GPL-3.0)** in accordance with the licensing terms of its mandatory base engines.

---

## Project Documents

- [M0 Preflight & Feasibility Audit](docs/M0_PREFLIGHT_AUDIT.md)
- [M2 Hardware Smoke Report](docs/M2_HARDWARE_SMOKE.md)
- [M3 Account & Catalog Verification Report](docs/M3_VERIFICATION_REPORT.md)
- [M4 Lyrics, Romaji & TV Optimization Report](docs/M4_VERIFICATION_REPORT.md)
- [M5 Signing, R8 Release & CI/CD Report](docs/M5_RELEASE_REPORT.md)
- [Credits & License Notices](CREDITS.md)
- [Known Issues & Release Blockers](KNOWN_ISSUES.md)
- [GNU GPL v3.0 License](LICENSE)
