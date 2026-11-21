# Milestone M4 Verification Report — Lyrics Engine, ArchiveTune Animation & Resource Optimization (`0.1.0-alpha`)

**Status:** Milestone M4 Verified on Physical Android TV (Ready for Signing, Optimization & Beta CI/CD Phase)  
**Target Package:** `com.alunara.app` (`versionName = "0.1.0-alpha"`, Debug APK)  
**Audio Disk Cache (`FG-03`):** Disabled (`ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED = false`, `0` bytes)  
**Hardware Reference (`PRD §13.3`):** Physical Changhong `AI PONT` Android 11 TV (`192.168.100.124:5555`, `SDK 30`, `armeabi-v7a`, `MemTotal: 934,540 kB` / ~`912.6 MiB`)

---

## 1. Delivered M4 & Artwork/Sync Polish Scope

| Capability | PRD / Spec Target | Implemented Artifacts |
|---|---|---|
| **Compact Inline Lyric Sync Controls** | User Request #10 | Replaced verbose `Sinkron Lirik` / hold-hint bar with inline `-`, `0 ms` readout, `+`, and `Reset` controls in `NowPlayingScreen.kt` (`CompactLyricSyncControls`) and cleaned ID/EN strings |
| **ArchiveTune TV-Optimized Lyrics Animation** | FR-18, `rukamori/ArchiveTune`, User Request #10 | Adapted smoothstep active-word fill + distance-graded opacity and GPU `.graphicsLayer` scale/lift (`CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`), without `Modifier.blur()` or font-size relayout |
| **Recomposition & UI-Thread Optimization** | PRD §7.3, 1 GB RAM TV Guard | Decoupled high-frequency `PlaybackSessionManager.positionMsFlow` (`80 ms` word / `250 ms` line tick) from `uiState`, `@Immutable StableSyncedLyricLine`, single `Animatable` per row, `ActiveWordSyncedLyricText` with quantized smoothstep (`quantizedSmoothStep`), uniform `FontWeight.Bold`, and zero CPU `Shadow` |
| **Top-Cut-Off Artwork & URL Upgrade Fix** | Crisp 10-foot Artwork Polish | Removed stale `?sqp=` query suffix on upgraded `i.ytimg.com` thumbnails in `ArtworkUrlResolver.kt`, plus `TvArtworkContentScale` in `TvComponents.kt` to crop baked-in 12.5% 4:3 letterbox black bars cleanly on Home cards & Now Playing hero art |
| **Smart Hedged Lyrics Ranking & Mismatch Filter** | FR-17, §§7.1–7.2 | Added `hedgeDelayMs = 280L` true hedged scheduling bounded by `Semaphore(concurrencyLimit <= 2)`, provider reliability score reordering, `scoreDocument()` preference (word-sync > line-sync > plain), and `+60s` duration mismatch rejection in `ArchiveTuneLyricsAdapter.kt` |
| **Offline JP/KR Romanization & Kanji Honesty** | FR-19, UI/UX Spec §§5.3–5.4 | Added offline `LyricsRomanizer` (`:core:domain`) for Hangul Revised Romanization (L/V/T + liaison) and Hiragana/Katakana Hepburn Romaji (`っ/ッ`, `ー`, `ん/ン`, digraphs), explicit `Romaji` / `Original` (`Asli`) toggle in `NowPlayingScreen.kt`, and honest `Kanji dipertahankan asli` notice |
| **Bounded Next-Track Lyrics Prefetch** | FR-20 | Added single next-track lyrics prefetch after `DEFAULT_PREFETCH_DELAY_MS = 3_000L` active playback, gated by `ActivityManager.MemoryInfo.lowMemory` runtime pressure and cancelled on queue mutations/backgrounding |
| **Runtime Smart Cache Budget Wiring** | FR-21, `PRD §13.3` | Wired `CacheBudgetManager.computeBudget()` in `AlunaraApplication.kt` & `AppContainer.kt` to scale Coil memory (`10 MiB` low-RAM / `12 MiB`) and disk caps (`8 MiB`–`32 MiB`) and clamp `CachedLyricsRepository` to `100` entries under `<= 32 MiB` budget |

---

## 2. Gradle Build, Lint & Unit Test Gate (`79 / 79 Passed`)

Executed after final artwork and animation optimization edits:

```powershell
.\gradlew :core:domain:test :core:music:test :core:lyrics:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

| Verification Item | Result | Count / Output |
|---|---|---|
| **Grand Unit Test Total** | **PASS** | **`79 / 79` passed** across `18` suites (`0` failed, `0` errors, `0` skipped) — includes +5 `LyricsRomanizerTest`, +3 `ArchiveTuneLyricsAdapterTest` (hedging & duration mismatch), +1 `PlaybackSessionManagerTest` (bounded prefetch + memory pressure + clock cleanup), and updated `ArtworkUrlResolverTest` |
| **`:app:lintDebug`** | **PASS** | **`0` issues** matching any touched M4/artwork/playback source file (`TOUCHED_LINT_MATCHES=0`) |
| **`:app:assembleDebug`** | **PASS** | `app/build/outputs/apk/debug/app-debug.apk` = **`81,348,305` bytes** (`77.58 MiB`) |

---

## 3. JP/KR Romanization & Compact Control Verification

| Fixture / Live Scenario | Result | Observed Output |
|---|---|---|
| **Korean Hangul (`LyricsRomanizerTest` + Live Contract)** | **PASS** | Algorithmic Revised Romanization decomposes Jamo `(L, V, T)` with onset liaison cleanly; unit tested across batchim & vowel transitions |
| **Japanese Hiragana/Katakana + Mixed Kanji (`ビバリウム - Vivarium · Ado` live on TV & unit tests)** | **PASS** | `KuGou · Sinkron baris`: Kana romanized accurately (`あんたらわかっちゃないだろ` -> `antarawakatchanaidaro`, `そりゃあ愛ある罰だ` -> `soryaa愛aru罰da`), Kanji preserved honestly without fabricated readings, caption shows `Sumber: KuGou · Sinkron baris · Kanji dipertahankan asli`, toggle cleanly switches `Romaji` <-> `Asli` |
| **Inline Compact Lyric Sync (`8 Letters · Why Don't We` live on TV)** | **PASS** | Header displays `Sumber: SimpMusic · Sinkron kata` alongside inline `-`, `0 ms`, `+`, `Reset`, SwapHoriz; tapping `+` updated readout to `+10 ms`, tapping `Reset` restored `0 ms` |

---

## 4. Physical Changhong `AI PONT` TV PRD §13.3 Hardware Benchmark (`192.168.100.124:5555`)

| Benchmark Dimension | Measured M4 Result |
|---|---|
| **Device Identity & Constraints** | `Changhong AI PONT`, `Android 11` (`SDK 30`), `armeabi-v7a`, `MemTotal: 934,540 kB` (~`912.6 MiB`), `Ukuran teks = Normal`, `Kurangi animasi tampilan = NONAKTIF`, `Kualitas Suara = Otomatis` |
| **App Startup (`am start -W`)** | **Cold Start** (`am force-stop` steady state): `TotalTime: 8,594 ms` (`WaitTime: 8,601 ms`, `Displayed +8s594ms`; initial post-install cold start `10,714 ms`); **Warm Start** (`LaunchState: HOT`): `TotalTime: 4,162 ms` (`WaitTime: 4,183 ms`) — Debug build (`isMinifyEnabled = false`), R8 release optimization scheduled next phase |
| **UI Frame & GPU Profile (`15s dumpsys gfxinfo` during `SimpMusic · Sinkron kata`)** | `Total frames rendered` reduced **123 -> 56 frames (~54.5% fewer UI redraws/15s)** after `positionMsFlow` isolation & single-Animatable row; **GPU 50th percentile: `7 ms`**, `90th: 8 ms`, `99th: 9 ms` (`50/56` frames GPU `<= 7 ms`); unminified Compose debug UI-thread frames peak at `50th pct: 113 ms` |
| **Memory Profile (`dumpsys meminfo`)** | Active animated word-synced lyrics: **`TOTAL PSS: 131,578 KB`** (~`128.5 MiB` < `140 MiB` budget; `Native Heap: 15,560 KB`, `Dalvik Heap: 9,612 KB`, `Graphics: 21,728 KB`, `TOTAL RSS: 178,040 KB`); Background `KEYCODE_HOME` + 3s: **`TOTAL PSS: 104,088 KB`** (~`101.6 MiB`) |
| **Background `MediaSession` & Logcat Stability** | Active `Media button session` registered for `com.alunara.app/androidx.media3.session.id.`; paused cleanly via `KEYCODE_MEDIA_PAUSE`; `logcat -d -s AndroidRuntime:E AlunaraPlayback:E`: **`0` errors / `0` crashes** |
| **Zero `third_party/` & Cache Compliance** | `0` modifications under `third_party/`, `FG-03` audio disk cache `0` bytes |
