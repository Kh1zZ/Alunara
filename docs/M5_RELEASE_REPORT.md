# Milestone M5 Verification & Release Optimization Report

**Project:** Alunara (`com.alunara.app`)  
**Target:** Android TV 9+ (API 28+, ARMv7/ARM64, 1 GB RAM Reference: Changhong `AI PONT` / `redwood`)  
**Version Configured:** `versionName = "0.1.0-beta.1"` (`versionCode = 2`)  

---

## 1. Deliverables Summary

1. **Externalized Signing & Version Overrides (`app/build.gradle.kts`, `keystore.properties.example`, `scripts/generate-keystore.ps1`):**
   - Reads release signing credentials from git-ignored `keystore.properties` or CI/CD environment variables (`ALUNARA_KEYSTORE_FILE`, `ALUNARA_KEYSTORE_PASSWORD`, `ALUNARA_KEY_ALIAS`, `ALUNARA_KEY_PASSWORD`), with automatic fallback to `debug` signing config when no release keystore is provided locally.
   - Enables `enableV1Signing = true`, `enableV2Signing = true`, `enableV3Signing = true`, and supports `-PversionName` / `-PversionCode` Gradle overrides from Git tags.
   - Verified generating a temporary 4096-bit RSA JKS keystore (`CN=Alunara Android TV, OU=Release, O=Alunara, C=ID`), assembling `:app:assembleRelease -PversionName=0.1.0-beta.1 -PversionCode=2`, and verifying signature with `apksigner` (`exit 0`, Scheme v3 verified, RSA 4096 bits; temporary test keystore removed after check).

2. **R8 Minification, Resource Shrinking & ARM ABI Filtering (`app/proguard-rules.pro`, `app/src/main/res/raw/keep.xml`):**
   - Activated `isMinifyEnabled = true`, `isShrinkResources = true`, `isCrunchPngs = true`, and `ndk.abiFilters += listOf("armeabi-v7a", "arm64-v8a")` on `buildTypes.release` (dropping unused `x86` / `x86_64` `.so` libraries).
   - Preserved `@Serializable` companion/serializer classes for ArchiveTune InnerTube & Lyrics models (`lrclib`, `simpmusic`, `kugou`), Kuromoji IPADIC (`com.atilika.kuromoji.**` and all 8 `.bin` dictionary resources), Android ICU reflection bridge (`android.icu.text.Transliterator`), Ktor engines, NewPipe Extractor / Mozilla Rhino, Room entities/DAOs, and `AlunaraPlaybackService`.

3. **GitHub Actions CI & Beta Release Pipelines (`.github/workflows/ci.yml`, `.github/workflows/release-beta.yml`):**
   - **`ci.yml`:** Runs on push/PR to `main` — verifies zero tracked `.jks`/`.keystore`/`keystore.properties` leaks and `third_party/` presence, runs all 18 unit test suites + `:app:lintDebug` + `:app:assembleDebug` + `:app:assembleRelease`, and uploads APKs, `mapping.txt`, and reports.
   - **`release-beta.yml`:** Triggered by `v*` Git tags or `workflow_dispatch` — validates unit tests & lint, decodes `ALUNARA_KEYSTORE_BASE64` from GitHub Secrets (or generates a masked ephemeral runner JKS when secret is unset), builds version-stamped Release/Debug APKs, checks signature via `apksigner`, generates `SHA256SUMS.txt`, archives R8 `mapping.txt`, and publishes a GitHub Pre-Release with ADB instructions and GPL-3.0 / `rukamori` attribution.

---

## 2. Debug vs. R8 Release Comparison

| Metric | Debug APK (`:app:assembleDebug`) | R8 Release APK (`:app:assembleRelease`) | Delta |
|---|---:|---:|---|
| **APK Size** | `93,399,908` bytes (`89.07 MiB`) | `21,837,216` bytes (`20.83 MiB`) | **`-71.56 MiB` (`-76.6%`)** |
| **DEX Payload** | `25+` uncompressed `.dex` (`~75.5 MiB`) | `2` `.dex` (`7.24 MiB` total; `classes.dex` = `7.23 MiB`) | **`-90.4%`** DEX size |
| **Native `.so` ABIs** | 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, 8 files) | 2 ARM ABIs (`arm64-v8a`, `armeabi-v7a`, 4 files) | `x86`/`x86_64` removed |
| **Kuromoji IPADIC `.bin`** | 8 dictionary files (`~12.7 MiB` compressed) | 8 dictionary files preserved intact (`~12.7 MiB` compressed) | Intact under R8 |
| **R8 Mapping Output** | N/A | `61,069,870` bytes (`58.24 MiB`) | Generated in `app/build/outputs/mapping/release/mapping.txt` |

---

## 3. Automated Build, Test & Lint Results

Verified after the final build configuration update:
- **Unit Tests (`:core:domain:test`, `:core:music:test`, `:core:lyrics:test`, `:core:data:testDebugUnitTest`, `:app:testDebugUnitTest`):** **`79 / 79` passed across `18` suites** (`0` failed, `0` errors, `0` skipped).
- **Android Lint (`:app:lintDebug`):** Passed (`abortOnError = true`), **`0` issues** on touched files (`TOUCHED_LINT_MATCHES=0`).
- **Build & Packaging:** Both `:app:assembleDebug` and `:app:assembleRelease` succeeded cleanly with `exit 0`.

---

## 4. Physical Hardware Verification (`Changhong AI PONT` Android 11 TV, `192.168.100.124:5555`)

Installed `app-release.apk` (`21,837,216` bytes / `20.83 MiB`, `versionCode=2`, `versionName=0.1.0-beta.1`) on `Changhong/DVB92/redwood` (`armeabi-v7a`, `MemTotal: 934,540 kB` / `~912.6 MiB`, Android 11 API 30):

- **Cold Start (`am start -W -n com.alunara.app/.MainActivity`):**
  - R8 Release: **`TotalTime: 1,784 ms`**, **`WaitTime: 1,812 ms`** (vs. `6,759 ms` / `6,817 ms` measured in M2 Debug).
- **Idle Home Feed Memory (`dumpsys meminfo com.alunara.app`):**
  - R8 Release Idle PSS: **`65,685 KB` (`~64.15 MiB`)** (`Dalvik Heap: 8,288 KB`, `Native Heap: 10,790 KB`, `.apk mmap: 7,535 KB`, `Code: 27,324 KB`, `Graphics: 4,020 KB`) — down from `98,180 KB` in M2 Debug.
- **Active Audio Playback + Kuromoji Dual-Line Romaji + 20% Blur Backdrop (`虹の彼方に - Niji No Kanatani · ReoNa` via Search Bar, `KuGou · Sinkron baris · Opus · 143 kbps`):**
  - AudioFlinger active track confirmed at `48000 Hz` (`PID 14429`, `com.alunara.app`).
  - Dual-line Japanese Romaji rendered naturally (`Romaji: AKTIF`, e.g., `hitahita koboreru akai akai sabiiro` / `ひたひた零れる 赤い赤い錆色`, `watashi wa susuketa buriki no hito` / `わたしは煤けたブリキのひと`, `karappo no shintai ni` / `空っぽの身体に`), confirming R8 preserved all `@Serializable` models and Kuromoji `.bin` dictionaries.
  - Active Playback PSS: **`92,605 KB` (`~90.43 MiB`)** (`Dalvik Heap: 8,814 KB`, `Native Heap: 16,579 KB`, `.apk mmap: 6,123 KB`, `Code: 12,396 KB`, `Graphics: 24,084 KB`) — down from `130,406 KB` (`~127.35 MiB`) in M4.2 Debug.
  - Graphics (`dumpsys gfxinfo com.alunara.app` over 10s idle listening): `23` total frames rendered, `50th gpu percentile: 19ms`, `99th gpu percentile: 21ms`, `50th UI/total percentile: 109ms` (`0` errors in `logcat -d -s AndroidRuntime:E AlunaraPlayback:E`).
