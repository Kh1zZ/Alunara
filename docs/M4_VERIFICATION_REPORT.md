# Milestone M4 & M4.1 Verification Report — Lyrics Engine, Full Kanji/KR Dual-Line Romaji, Blurred Full-Screen Backdrop & Resource Optimization (`0.1.0-alpha`)

**Status:** Milestone M4 + M4.1 Verified on Physical Android TV (Ready for Signing, R8 Optimization & Beta CI/CD Phase)  
**Target Package:** `com.alunara.app` (`versionName = "0.1.0-alpha"`, Debug APK)  
**Audio Disk Cache (`FG-03`):** Disabled (`ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED = false`, `0` bytes)  
**Hardware Reference (`PRD §13.3`):** Physical Changhong `AI PONT` Android 11 TV (`192.168.100.124:5555`, `SDK 30`, `armeabi-v7a`, `MemTotal: 934,540 kB` / ~`912.6 MiB`)

---

## 1. Delivered M4 & M4.1 Scope

| Capability | PRD / Spec Target | Implemented Artifacts |
|---|---|---|
| **Dual-Line Stacked Romaji + Original Script** | M4.1 User Spec, `FR-19`, `ArchiveTune` Parity | Auto-enabled when JP/KR lyrics load (`PlaybackSessionManager.fetchLyricsForTrack`); top large highlighted **Romaji** line (with word-level `SyncedLyricWord.romanizedText` smoothstep fill when word-synced) paired with smaller (`0.72f`) dimmed **Original JP/KR** script underneath (`SyncedLyricRowItem` & `Plain` lyrics in `NowPlayingScreen.kt`) plus `Romaji: AKTIF` / `Romaji` toggle |
| **Full Kanji + Kana + Hangul Romanization (`WeakReference` Memory Guard)** | `FR-19`, 1 GB TV RAM Guard | Integrated `com.atilika.kuromoji:kuromoji-ipadic:0.9.0` via `WeakReference<Tokenizer>` (`LyricsRomanizer.kt`) with particle pronunciation handling (`は -> wa`, `へ -> e`, `を -> o`), batch token reuse, and deterministic post-track release + `System.gc()`, reclaiming ~35 MiB IPADIC dictionary from `Dalvik Heap` right after romanizing |
| **Full-Screen Blurred + Darkened Artwork Backdrop & Crisp Left Card** | M4.1 User Spec, Android 9–11 TV (`API 28+`) | `TvBackdropBlurTransformation` (`TvComponents.kt`: downsampled 144px 3-pass box blur + 4:3 letterbox crop once per track, `0 ms` per-frame GPU blur cost, API 28+ safe) behind darkened gradients across the full 1280x720 Now Playing screen (both left pane & behind lyrics), paired with a sharp unblurred `212.dp` rounded-square cover card on the left pane using `TvArtworkContentScale` |
| **Compact Inline Lyric Sync & ArchiveTune TV Animation** | `FR-18`, User Request #10 | Inline `-`, `0 ms`, `+`, and `Reset` controls (`CompactLyricSyncControls`), `positionMsFlow` (`80 ms` word / `250 ms` line tick) recomposition isolation, `@Immutable StableSyncedLyricLine`, single `Animatable` per row, quantized smoothstep (`quantizedSmoothStep`), and zero `Modifier.blur()` |
| **Smart Hedged Lyrics Ranking, Prefetch & Budget** | `FR-17`, `FR-20`, `FR-21` | `hedgeDelayMs = 280L` true hedged fetch (`Semaphore <= 2`), `+60s` duration mismatch rejection (`ArchiveTuneLyricsAdapter.kt`), 1-track `3,000 ms` prefetch gated by `MemoryInfo.lowMemory`, and `CacheBudgetManager` runtime Coil & `CachedLyricsRepository` (`100` entries under `<= 32 MiB`) limits |
| **Foreground MediaService Stability Fix** | Android 8–11 `startForegroundService()` Contract | Added `ensureForegroundStarted()` (`CHANNEL_ID = "default_channel_id"`, `ID = 1001`) in `AlunaraPlaybackService.onCreate()` & `onStartCommand()`, eliminating `RemoteServiceException` during slow stream resolution or background resume |

---

## 2. Gradle Build, Lint & Unit Test Gate (`79 / 79 Passed`)

Executed after the final `LyricsRomanizer.kt` `WeakReference` memory-reclaim & `AlunaraPlaybackService.kt` edits:

```powershell
.\gradlew :core:domain:test :core:music:test :core:lyrics:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

| Verification Item | Result | Count / Output |
|---|---|---|
| **Grand Unit Test Total** | **PASS** | **`79 / 79` passed** across `18` suites (`0` failed, `0` errors, `0` skipped) — verifies full Kanji+Kana Kuromoji conversion (`散々な思い出は悲しみを穿つほど` -> `sanzan na omoide wa kanashimi o ugatsu hodo`, `夜に駆ける` -> `yoru ni kakeru`), word-level `SyncedLyricWord.romanizedText`, auto-enabled `showRomanizedLyrics`, hedged provider ranking, and Hangul Revised Romanization |
| **`:app:lintDebug`** | **PASS** | **`0` issues** matching any touched M4 / M4.1 source file (`TOUCHED_LINT_MATCHES=0`) |
| **`:app:assembleDebug`** | **PASS** | `app/build/outputs/apk/debug/app-debug.apk` = **`93,467,337` bytes** (`89.14 MiB`) |

---

## 3. Physical Changhong `AI PONT` TV Live Verification (`192.168.100.124:5555`)

| Benchmark / Scenario | Measured M4 + M4.1 Result |
|---|---|
| **Device Identity & Constraints** | `Changhong AI PONT`, `Android 11` (`SDK 30`), `armeabi-v7a`, `MemTotal: 934,540 kB` (~`912.6 MiB`), `Ukuran teks = Normal`, `Kurangi animasi tampilan = NONAKTIF`, `Kualitas Suara = Otomatis` |
| **App Startup (`am start -W`)** | **Cold Start**: `TotalTime: 6,534 ms` (repeat launch) / `9,448 ms` (immediate post-install cold start with ART JIT); **Warm Start** (`LaunchState: HOT`): `TotalTime: 4,162 ms` (Debug APK `isMinifyEnabled = false`; R8 optimization next phase) |
| **Dual-Line Full Kanji+Kana & Blur (`unknown · ReoNa`, `KuGou · Sinkron baris · Opus · 135 kbps`)** | Verified live (`m4_1_reona_playing.png`): `Romaji: AKTIF` auto-enabled; active highlighted row (`hontou no boku wa dare mo shira nai` bright bold on top, `本当の僕は誰も知らない` dimmer underneath), smooth distance fade across neighbors, full-screen dark blurred artwork backdrop behind left panel & lyrics, and crisp unblurred `212.dp` rounded cover card on left |
| **Memory Optimization Impact (`dumpsys meminfo`)** | Reclaiming Kuromoji IPADIC via `WeakReference` + GC cut `Dalvik Heap` from `45,292 KB` (`TOTAL PSS: 152,617 KB`) down to **`Dalvik Heap: 8,800 KB`** and **`TOTAL PSS: 111,880 KB`** (~`109.26 MiB`, well under `140 MiB` budget) during active dual-line Japanese playback with full-screen blurred backdrop; **Background (`KEYCODE_HOME` + 4s)**: **`TOTAL PSS: 93,158 KB`** (~`90.97 MiB`, PID `11836` alive) |
| **Frame Smoothness (`dumpsys gfxinfo`)** | `8 Letters` word-synced (`SimpMusic`): `56` rendered frames/15s (down ~54.5% vs initial `123`), **GPU 50th pct: `7 ms`** (`99th: 9 ms`); `unknown · ReoNa` line-synced dual-line (`18s`): only `29` frames rendered across 18s (GPU 50th pct `19 ms`, `Graphics: 16,532 KB`) |
| **Compact Controls & Stability (`logcat`)** | Inline `-`, `0 ms`, `+` -> `+10 ms`, `Reset` -> `0 ms` verified; **`0` crashes / `0` errors** in `logcat -d -s AndroidRuntime:E AlunaraPlayback:E`; `0` changes under `third_party/`, `FG-03` audio disk cache `0` bytes |
