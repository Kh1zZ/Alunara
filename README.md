# Alunara

**Alunara** (`com.alunara.app`) is an independent, audio-only YouTube Music client built specifically for entry-level **Android TV** devices running **Android 9 (API 28)+** with **1 GB RAM** (reference hardware: **Changhong L32G7N**).

> **Current Status:** `Milestone M3.3 — Personalized Account Experience & Companion Login (0.1.0-alpha)`  
> M3.3 builds on M3/M3.1/M3.2 with auth-reactive Personalized Home (`PlaylistItem` mixes, continuation shelves, and `ChartsPage`), QR Code Phone Companion WebView login (`QrCodeMatrix` + `LocalLoginCompanionServer` + direct D-pad DOM navigation), reordered 3-tab YouTube-synced `LibraryScreen` (`Playlist` > `Favorit` > `Riwayat` with `LM` & `FEmusic_history`), and interactive `NowPlayingScreen` `Lirik <-> Antrean` panel powered by YouTube `RDAMVM` radio recommendations & `QueueFilterChip` filters. Verified with `60 / 60` unit tests across `16` suites and live on physical Changhong `AI PONT` TV (`192.168.100.124:5555`); see [docs/M3_VERIFICATION_REPORT.md](docs/M3_VERIFICATION_REPORT.md).

---

## Module Architecture (Modular-Lite)

- `:app` — Android TV shell (`com.alunara.app`), Fixed Dark Forest Green Material 3 TV theme, collapsible D-pad rail, `SinglePlayerLifecycleController` (Media3 audio-only + stop-on-exit), ID/EN strings, and in-app open-source notices.
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

# Run Android lint & build Debug APK
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

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
- [Credits & License Notices](CREDITS.md)
- [Known Issues & Release Blockers](KNOWN_ISSUES.md)
- [GNU GPL v3.0 License](LICENSE)
