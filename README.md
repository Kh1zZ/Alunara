# Alunara

**Alunara** (`com.alunara.app`) is an independent, audio-only YouTube Music client built specifically for entry-level **Android TV** devices running **Android 9 (API 28)+** with **1 GB RAM** (reference hardware: **Changhong L32G7N**).

> **Current Status:** `Milestone M0 — Preflight & Feasibility Audit`  
> The repository is currently at M0 baseline setup (audit, license, attribution, and feasibility review). No Android/Gradle modules or distributable APKs have been built yet pending M0 human checkpoint approval.

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
- [Credits & License Notices](CREDITS.md)
- [Known Issues & Release Blockers](KNOWN_ISSUES.md)
- [GNU GPL v3.0 License](LICENSE)
