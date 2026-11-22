# Milestone M4 / M4.2 Verification Report — Lyrics Engine, ArchiveTune Multi-Script Romaji, 20% Backdrop Blur & Optimization (`0.1.0-alpha`)

**Status:** Milestone M4 + M4.2 Verified on Physical Android TV (Ready for Signing, R8 Optimization & Beta CI/CD Phase)  
**Target Package:** `com.alunara.app` (`versionName = "0.1.0-alpha"`, Debug APK)  
**Audio Disk Cache (`FG-03`):** Disabled (`ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED = false`, `0` bytes)  
**Hardware Reference (`PRD §13.3`):** Physical Changhong `AI PONT` Android 11 TV (`192.168.100.124:5555`, `SDK 30`, `armeabi-v7a`, `MemTotal: 934,540 kB` / ~`912.6 MiB`)

---

## 1. Delivered M4 & M4.2 Scope

| Capability | PRD / Spec Target | Implemented Artifacts |
|---|---|---|
| **ArchiveTune-Aligned Multi-Script Romanization** | User M4.2 #1, `FR-19`, `rukamori/ArchiveTune` + `atilika/kuromoji` | Upgraded `LyricsRomanizer.kt`: runs `Kuromoji` IPADIC (`WeakReference` + GC reclaim) across all Japanese lines (Hiragana/Katakana/Kanji) with particle pronunciation (`は -> wa`, `へ -> e`, `を -> o`) plus POS-aware bound suffix & sokuon joining (`やさしい+て` -> `yasashikute`, `知ら+ない` -> `shiranai`, `思っ+た` -> `omotta`); ported ArchiveTune's 95-pair Hangul phonological assimilation rules with single onset emission (`없는` -> `eomneun`, `신라` -> `silla`); added offline Cyrillic transliteration (`Привет мир, я люблю тебя` -> `Privet mir, ya lyublyu tebya`) and Android ICU `"Any-Latin; Latin-ASCII"` reflection bridge for Hindi/other scripts |
| **Subtle ~20% Full-Screen Blurred Backdrop & Centered Cover** | User M4.2 #2 & #3, Android 9–11 TV (`API 28+`) | Updated `TvBackdropBlurTransformation` (`TvComponents.kt`) to `TARGET_MAX_PX = 360`, `BLUR_RADIUS = 3`, `PASSES = 2` for a soft ~20% blur behind `48%–72%` horizontal + `10%–68%` vertical scrims across both panes; vertically centered the crisp `220.dp` rounded-square cover card, track metadata, seekbar, and transport controls in the left pane parallel to the active lyrics (`NowPlayingScreen.kt`) |
| **Dual-Line Stacked Lyrics, Compact Sync & ArchiveTune Word Animation** | `FR-18`, User Request #10 & M4.1 | Auto-enabled `Romaji: AKTIF` dual-line display (large highlighted Romaji top line + `0.72f` dimmed original JP/KR bottom line), inline `-`, `0 ms`, `+`, `Reset` sync controls, `positionMsFlow` isolation, single `Animatable` per row, quantized smoothstep word-fill, zero `Modifier.blur()` |
| **Frontend & Backend Performance Optimizations** | User M4.2 #4, Zero Regression | Concurrent `backend.home()` + `backend.getChartsPage()` via `coroutineScope { async }` (`ArchiveTuneMusicAdapter.kt`), `lastSuccessfulClient` stream fast-path + bounded 4-entry TTL stream source cache, next-track audio stream pre-warm inside `scheduleNextTrackLyricsPrefetch()` (`PlaybackSessionManager.kt`), and isolated `MiniPlayerProgressStrip` in `DockedMiniPlayer` so browse screens skip 1 Hz mini-player recomposition (`TvComponents.kt`, `MainActivity.kt`) |
| **Smart Hedged Lyrics, Budget & Foreground Stability** | `FR-17`, `FR-20`, `FR-21` | `280 ms` hedged lyrics scheduling (`Semaphore <= 2`), `+60s` mismatch rejection, `CacheBudgetManager` Coil & `CachedLyricsRepository` caps, and unconditional `AlunaraPlaybackService.ensureForegroundStarted()` preventing Android 11 `RemoteServiceException` |

---

## 2. Gradle Build, Lint & Unit Test Gate (`79 / 79 Passed`)

Executed after final M4.2 edits:

```powershell
.\gradlew :core:domain:test :core:music:test :core:lyrics:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

| Verification Item | Result | Count / Output |
|---|---|---|
| **Grand Unit Test Total** | **PASS** | **`79 / 79` passed** across `18` suites (`0` failed, `0` errors, `0` skipped) — covers Japanese Kuromoji suffix joining (`yasashikute`, `shiranai`, `omotta kokoro ni futa o shita`, `iitai koto o gaman shite`), Korean Hangul assimilation (`eomneun`, `silla`, `saranghae isseo`), Russian Cyrillic (`Privet mir, ya lyublyu tebya`), word-level `SyncedLyricWord.romanizedText`, auto-enabled Romaji, hedged lyrics, and stream selection |
| **`:app:lintDebug`** | **PASS** | **`0` issues** matching any touched source file (`TOUCHED_LINT_MATCHES=0`) |
| **`:app:assembleDebug`** | **PASS** | `app/build/outputs/apk/debug/app-debug.apk` = **`93,658,221` bytes** (`89.32 MiB`) |

---

## 3. Physical Changhong `AI PONT` TV Live Verification (`192.168.100.124:5555`)

| Benchmark / Scenario | Measured M4.2 Result |
|---|---|
| **Device Identity & Constraints** | `Changhong AI PONT`, `Android 11` (`SDK 30`), `armeabi-v7a`, `MemTotal: 934,540 kB` (~`912.6 MiB`), `Ukuran teks = Normal`, `Kurangi animasi tampilan = NONAKTIF`, `Kualitas Suara = Otomatis` |
| **Word-Synced Layout & Subtle Blur (`Photograph · Ed Sheeran`, `SimpMusic · Sinkron kata · Opus · 133 kbps`)** | Verified live (`m4_2_photograph.png`): crisp `220.dp` `X` cover centered vertically over metadata & controls; soft ~20% full-screen blurred artwork spans behind both left pane & right lyrics; `50` rendered frames across 12s (GPU 50th pct `18 ms`), **`TOTAL PSS: 126,871 KB`** (~`123.9 MiB`, `Dalvik Heap: 11,036 KB`, `Graphics: 9,564 KB`) |
| **Natural Japanese Kuromoji Dual-Line (`虹の彼方に - Niji No Kanatani · ReoNa`, `KuGou · Sinkron baris · Opus · 143 kbps`)** | Verified live (`m4_2_reona_playing.png`): `Romaji: AKTIF` auto-enabled; natural Kuromoji output without broken suffix splits (`hitahita koboreru akai akai sabiiro` / `ひたひた零れる 赤い赤い錆色`, `watashi wa susuketa buriki no hito` / `わたしは煤けたブリキのひと`, `karappo no shintai ni` / `空っぽの身体に`, `tokun tokun myakuutsu` / `トクン トクン 脈打つ`, `anata ga warui mahou o`); subtle ~20% portrait backdrop behind centered left cover & lyrics |
| **Memory Profile (`dumpsys meminfo`)** | Active Japanese Kanji + 20% blur + Opus 143 kbps playback: **`TOTAL PSS: 130,406 KB`** (~`127.35 MiB` < `140 MiB` budget, `Dalvik Heap: 12,220 KB`, `Native Heap: 20,748 KB`, `Graphics: 12,332 KB`); **Background (`KEYCODE_HOME` + 3s)**: **`TOTAL PSS: 115,642 KB`** (~`112.9 MiB`, PID `12757` alive) |
| **Stability & Compliance** | Paused via `KEYCODE_MEDIA_PAUSE`; **`0` crashes / `0` errors** in `logcat -d -s AndroidRuntime:E AlunaraPlayback:E`; `0` changes under `third_party/`, `FG-03` audio disk cache `0` bytes |
