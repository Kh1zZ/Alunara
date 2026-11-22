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

---

## 5. Milestone M5.1 — Sidebar Black-Blink Fix, D-Pad Right Focus Bridges, 60 FPS Optimization & Smart QR Login Wizard (`0.1.0-beta.2`)

1. **Instant Sidebar Tab Switching & Auto-Switch (`MainActivity.kt`):**
   - Removed root `Crossfade` (`root_destination`) and per-tab `Crossfade` (`browse_tab`) so switching between `Beranda`, `Koleksi`, and `Pengaturan` transitions immediately on solid background (`#101813`) without black blinking.
   - Wrapped tab panes with `rememberSaveableStateHolder()` to preserve scroll state across tabs, replaced 4× `AnimatedVisibility(expandHorizontally)` in `CollapsibleNavigationRail` with clipped width layout + conditional expanded labels, and enabled auto-switching when navigating Up/Down inside the focused rail (`railAlreadyFocused`).

2. **D-Pad Right-Side Focus Bridges (`MainActivity.kt`, `NowPlayingScreen.kt`, `LibraryScreen.kt`, `TvComponents.kt`):**
   - **Now Playing:** Linked `SeekableProgressBar` (`up = lyricsTabRequester`), transport `Radio` button (`right = lyricsTabRequester`), top right tabs (`Lirik`, `Antrean`, `Romaji`), lyric sync controls (`-`, `+`, `Reset`, `Ganti sumber`), and `NowPlayingQueuePanel` refresh control so pressing D-pad Right/Up reaches right-side buttons directly and D-pad Left returns to transport controls.
   - **Library / Lists / Mini Player:** Added `topRightActionRequester` in `LibraryScreen`, explicit `right`/`left` focus bridges between track rows and secondary right-side buttons (`+ Antrean` / `Hapus`) in `TvSongRow`, and between `Play/Pause` ↔ `Sedang Diputar` in `DockedMiniPlayer`. Raised `AlunaraDimens.QueueActionButtonSize` from `34.dp` to `40.dp`.

3. **Compose 60 FPS Optimization for 1 GB RAM TV (`PlaybackSessionManager.kt`, `compose-stability.conf`, `TvArtwork`, `AlunaraApplication.kt`):**
   - Eliminated 1 Hz `mutableUiState` emissions in `PlaybackSessionManager.startTickerIfNeeded()` (progress updates flow exclusively via `positionMsFlow`, collected locally by `SeekableProgressBar` and `MiniPlayerProgressStrip`).
   - Added `app/compose-stability.conf` (`com.alunara.core.domain.model.**`, `policy.**`, `media.**`, `PlaybackSessionUiState`, collections) to make Compose screens and rows skippable.
   - Fixed `TvArtwork` decode sizing bug (`coerceAtLeast(tier.maxPx)` -> `coerceIn(64, tier.maxPx)`), disabled per-item list crossfade, enabled `allowRgb565(true)` in `AlunaraApplication`, and removed per-card `animateColorAsState` + translucent `alpha = 0.72f` backgrounds across cards, rows, buttons, `HomeScreen`, `LibraryScreen`, `SettingsScreen`, and `CatalogDetailScreen` (using theme tokens via `LocalAlunaraColors.current`).

4. **Smart QR Login Companion Wizard & InnerTube Clarification (`YouTubeWebViewLoginScreen.kt`, `LocalLoginCompanionServer.kt`, `strings.xml`):**
   - Audited `third_party/core/InnerTube.kt`: SmartTube's OAuth TV code flow (`TVHTML5`) cannot authenticate `https://music.youtube.com/youtubei/v1/` (`WEB_REMIX`), which strictly requires Google Web session cookies (`SAPISID` / `__Secure-3PAPISID`) + `SAPISIDHASH`.
   - Upgraded `LocalLoginCompanionServer` with `GET /status?t=<token>`, live 4-step phone progress wizard (`1. Email` -> `2. Sandi` -> `3. 2FA` -> `4. Masuk`), clear SmartTube distinction explanation, AJAX remote TV D-pad controls (`▲`, `▼`, `⏎`, `↻`), and an optional **Paste Cookie** accordion, paired with JS step detection and focusable D-pad helper controls (`Fokus Kolom`, `Klik Lanjut`, `Muat Ulang`) on the TV right pane.

5. **M5.1 Automated Build & Test Gate (`versionCode = 3`, `versionName = "0.1.0-beta.2"`):**
   - **Unit Tests:** **`82 / 82` passed across `19` suites** (`0` failed, `0` skipped), including 3 new JVM tests in `LocalLoginCompanionServerTest`.
   - **Android Lint:** Passed (`:app:lintDebug` `exit 0`; `0` lint issues on modified Kotlin/Gradle sources, `8` pre-existing `UnusedResources` and `4` `PrivateResource` warnings on `strings.xml` baseline).
   - **APKs & Signing (`apksigner verify` `exit 0`, Scheme v3 `CN=Alunara Android TV`, RSA 4096-bit):**
     - `app-debug.apk`: `93,469,004` bytes (`89.14 MiB`)
     - `app-release.apk` (R8 + Resource Shrunk + Production Signed): **`21,867,926` bytes (`20.85 MiB`)**

---

## 6. Milestone M5.2 — 60 FPS Android TV Navigation, Fixed Non-Reflow Sidebar & Real-Time TV ↔ Phone Login Step Sync

Adopted key Compose, R8, Coil, and hoisted-callback optimizations inspired by `ca-nim ft gemini`, plus closed the SPA desync gap in Google TV WebView sign-in:

1. **R8 Full Mode & Compose Source-Trace Stripping (`gradle.properties`, `app/proguard-rules.pro`):**
   - Enabled `android.enableR8.fullMode=true`.
   - Added `-assumenosideeffects` for `androidx.compose.runtime.ComposerKt.sourceInformation*` and `-allowaccessmodification` in `app/proguard-rules.pro` so release composable calls do not allocate/format debug source-location strings during focus/scroll traversal.
   - Expanded `app/compose-stability.conf` (`com.alunara.core.domain.contract.**`, `PlaybackSessionManager`, `kotlinx.coroutines.flow.StateFlow`) and added `.allowHardware(true)` in `AlunaraApplication.kt`.

2. **Elimination of Main-Thread Text Re-Layout, Layout Thrashing & Unskipped Recompositions (`TvComponents.kt`, `MainActivity.kt`, Screens):**
   - **Fixed-Width Non-Reflow Left Rail (`FixedNavigationRail` in `MainActivity.kt`, `AlunaraDimens.RailCollapsedWidth = 104.dp`):** Replaced focus-expanding rail width transitions (`76.dp <-> 216.dp`) with a constant `104.dp` compact icon + label rail (`Beranda`, `Koleksi`, `Pengaturan` fitting without ellipsis) using `RailFocusTracker` updated via `hostView.post` (zero state writes on rail enter/exit) and `drawBehind` focus borders.
   - **Zero-Recomposition Card/Row Focus Drawing (`TvComponents.kt`, `HomeScreen.kt`):** Moved `focused`/`rowFocused` state reads strictly inside `Modifier.drawBehind` for `TvMediaCard`, `TvSongRow`, and `ContinueQueueCard`, kept `FontWeight` constant across focus states (eliminating CPU `StaticLayout` text re-shaping on D-pad key presses), hoisted shared `RoundedCornerShape` instances to file top level, and memoized `subtitle` / `durationText` / `.take(15)` slices.
   - **Hoisted Screen Callbacks & `contentType` Reuse (`MainActivity.kt`, `HomeScreen.kt`, `LibraryScreen.kt`, `SearchScreen.kt`, `CatalogDetailScreen.kt`, `NowPlayingScreen.kt`):** Hoisted all screen/player/rail callbacks into `remember(...)` blocks reading `sessionManager.uiState.value`, added `contentType` across `LazyColumn`/`LazyRow`/`LazyVerticalGrid` items, and disabled hero cover crossfade in `NowPlayingScreen`.

3. **Real-Time TV ↔ Phone Login Step Synchronization (`YouTubeWebViewLoginScreen.kt`, `LocalLoginCompanionServer.kt`):**
   - **Continuous SPA Step Watcher (`INSTALL_STEP_WATCHER_JS` + `WebViewClient.doUpdateVisitedHistory`):** Replaced one-shot `onPageFinished` detection with a persistent `MutationObserver` + `450ms` interval watcher paired with `doUpdateVisitedHistory` (`pushState` hook) and `inferStepFromUrl(url)` (`email` -> `password` -> `verify` Push/OTP -> `done`), plus `onMusicHomeReached` fallback extraction.
   - **AJAX Companion Forms Without Page Reload (`LocalLoginCompanionServer.kt`):** Included `"step"` and `"hint"` in `POST /submit` JSON responses, intercepted all `.wizardForm` submits via `fetch` (`ajax=1`), added `burstPoll()` + `750ms` `/status` polling, and synced card badges (`Aktif di TV` / `Selesai ✓`), smooth scroll, and input auto-focus to match the TV WebView stage in real time.

4. **M5.2 Automated Verification & Live Hardware Benchmark (`Changhong AI PONT`, `192.168.100.124:5555`):**
   - **Unit Tests & Lint:** **`83 / 83` passed across `19` suites** (`0` failed, `0` skipped, including `buildStatusJson_tracksStepTransitionsSynchronously`); `:app:lintDebug` exited `0`; `:app:assembleRelease` produced `app-release.apk` (`20.87 MB`).
   - **Physical TV (`Changhong AI PONT`, 1 GB RAM, Android 11):**
     - **Cold Start:** `TotalTime: 1,123 ms`, `WaitTime: 1,130 ms`.
     - **Home D-Pad Navigation (`dumpsys gfxinfo com.alunara.app`, `24` frames):** **`50th percentile: 9 ms`**, `90th percentile: 21 ms`, `Number Missed Vsync: 0`, `Number Slow bitmap uploads: 0`, `Number Slow issue draw commands: 0`, **`GPU 50th/90th/95th/99th: 13 / 15 / 15 / 16 ms`**.
     - **Now Playing (`dumpsys gfxinfo com.alunara.app`, `14` frames):** `50th percentile: 14 ms`, `90th percentile: 21 ms`, `99th percentile: 23 ms`.
     - **Memory (`dumpsys meminfo com.alunara.app`):** **`TOTAL PSS: 72,288 KB` (`~70.59 MiB`)**.
