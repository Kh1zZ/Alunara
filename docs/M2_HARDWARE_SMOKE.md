# Milestone M2 Verification & Hardware Smoke Report (`0.1.0-alpha`)

**Status:** Playable Alpha Verified on Physical Android TV (Pending M2 Human Review)  
**Target Package:** `com.alunara.app` (`versionName = "0.1.0-alpha"`, Debug APK)  
**Audio Disk Cache (`FG-03`):** Disabled (`ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED = false`, `0` bytes)  

---

## 1. Gradle Build, Lint & Unit Test Verification

Executed under JDK 21 (`C:\Program Files\Java\jdk-21.0.12.1`):

```powershell
.\gradlew.bat --console=plain :core:domain:test :core:music:test :core:lyrics:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

| Verification Item | Result | Count / Output |
|---|---|---|
| `:core:domain:test` | **PASS** | `18 / 18` passed across 6 suites (`LrcParserTest` 3, `LyricsSyncResolverTest` 1, `AudioFormatSelectorTest` 3, `ResourceBudgetPolicyTest` 1, `RetryPolicyTest` 4, `QueueReducerTest` 6) |
| `:core:music:test` | **PASS** | `8 / 8` passed (`ArchiveTuneMusicAdapterTest` 8, including anonymous `visitorData` & `VISIONOS` priority coverage) |
| `:core:lyrics:test` | **PASS** | `3 / 3` passed (`ArchiveTuneLyricsAdapterTest` 3) |
| `:core:data:testDebugUnitTest` | **PASS** | `5 / 5` passed across 2 suites (`CacheBudgetManagerTest` 3, `CachedLyricsRepositoryTest` 2) |
| `:app:testDebugUnitTest` | **PASS** | `3 / 3` passed across 2 suites (`AlunaraThemeTokensTest` 1, `PlaybackErrorClassifierTest` 2) |
| **Grand Total Unit Tests** | **PASS** | **`37 / 37` passed** (`0` failed, `0` errors, `0` skipped) across 12 test suites |
| `:app:lintDebug` | **PASS** | Exit code `0` |
| `:app:assembleDebug` | **PASS** | `app/build/outputs/apk/debug/app-debug.apk` = `45,396,710` bytes (`43.29 MiB`) |

---

## 2. Physical Hardware Profile (`Changhong AI PONT`)

Verified over ADB TCP/IP (`192.168.100.124:5555`):

| Property | Measured Value |
|---|---|
| Manufacturer / Brand / Model | `Changhong` / `Changhong` / `AI PONT` |
| Product / Device / Board / Hardware | `DVB92` / `redwood` / `redwood` / `mt5867` |
| Android Version / SDK / Build | Android `11` (`SDK 30`), `RTMA.250416.071` |
| CPU ABI / Cores | `armeabi-v7a` (32-bit ARMv7, `4` cores) |
| Display Size & Density | Physical `1280x720` @ `213 dpi` (logical `960x540 dp`; 5% overscan = `48x27 dp`) |
| RAM & Dalvik Config | `ro.config.low_ram=true`, `MemTotal: 934540 kB`, `heapgrowthlimit=192m`, `heapsize=512m` |
| Storage (`/data/user/0`) | `Size 4.0G`, `Used 1.8G`, `Avail 2.0G` (`Use% 48%`) |
| Hardware Media Codecs (`dumpsys media.player`) | `audio/flac`, `audio/mp4a-latm`, `audio/mp4a-latm-hw`, `audio/mpeg`, `audio/mpeg-L1`, `audio/mpeg-L2`, `audio/opus`, `audio/vorbis` |

---

## 3. Root-Cause Playback Findings Resolved During Hardware Testing

Live testing on Changhong TV and JVM HTTP probing identified and fixed two Guest playback blockers without modifying `third_party/`:

1. **Missing Anonymous `visitorData`:** Without a guest `visitorData` token, direct mobile/TV InnerTube player profiles (`VISIONOS`, `ANDROID_VR_1_65_10`) returned `LOGIN_REQUIRED` / `UNPLAYABLE`, causing fallback to `WEB_REMIX` + `NewPipeUtils` (Rhino JS), which triggered ~11–12 MB LOS GC collections and failed cipher deobfuscation. Fetching and caching `backend.visitorData()` via `visitorDataMutex` resolved direct `OK` streams without invoking Rhino.
2. **Media3 `OkHttpDataSource` HTTP 403 on `ANDROID_VR` Unbounded Requests:** `ANDROID_VR_1_65_10` direct URLs returned `HTTP 403` to bare `GET` and unbounded `Range: bytes=0-` requests used by `ProgressiveMediaSource` + `OkHttpDataSource` (only accepting bounded chunk ranges such as `Range: bytes=0-65535`). Live probing across 4 Guest Home tracks confirmed `VISIONOS` returns `HTTP 200` (bare `GET`) and `HTTP 206` (`Range: bytes=0-` and `bytes=1024-`). Prioritizing `YouTubeClient.VISIONOS` first in `ArchiveTuneMusicAdapter` resolved the `HTTP 403` playback error cleanly.

---

## 4. Changhong TV Smoke Test Results (Clean State)

Tested after `pm clear com.alunara.app`, clean install of `0.1.0-alpha` debug APK, and `logcat -c`:

| Check | Measured Outcome |
|---|---|
| Cold Launch (`am start -W`, clean Room DB) | `Status: ok`, `LaunchState: COLD`, `TotalTime: 6759 ms`, `WaitTime: 6817 ms` |
| Idle Memory (`dumpsys meminfo com.alunara.app`) | `TOTAL PSS: 98,180 KB`, `Java Heap: 11,308 KB`, `Native Heap: 7,828 KB`, `Code: 57,608 KB`, `Graphics: 2,428 KB`, `TOTAL RSS: 157,944 KB` |
| Guest Home Feed & Imagery (`m2_changhong_home` / live capture) | Real Guest sections loaded dynamically (`Pilihan cepat`: `Teh Hijau` - Tulus, `Negoro Angin` - Denny Caknan, `Dunia Yang Nanti` - Raim Laode, `Terbelah Jadi Dua` - Jeyma Monica) with Coil artwork, Dark Forest Green tokens, and `48x27 dp` overscan safe padding |
| Live Media3 Audio Playback (`Teh Hijau`, `VISIONOS`) | Visual status `Diputar`, advancing progress (`0:06 / 3:32`), synced lyrics active highlight (`Hari-hari berulang`), `dumpsys media.audio_flinger` confirmed active session `com.alunara.app` (`Standby: no`, `48000 Hz`, `3 Tracks of which 1 are active`) |
| Active Playback Memory (`dumpsys meminfo com.alunara.app`) | `TOTAL PSS: 91,584 KB`, `Java Heap: 12,824 KB`, `Native Heap: 7,628 KB`, `Code: 38,764 KB`, `Graphics: 3,988 KB`, `TOTAL RSS: 151,720 KB` |
| Stop-on-Exit (`KEYCODE_HOME` + 3s) | `dumpsys media.audio_flinger` dropped cleanly to `2 Tracks of which 0 are active` (`SinglePlayerLifecycleController.onAppBackgroundedOrStopped`) |
| Relaunch Recovery (`am start -W`) | `LaunchState: HOT`, `TotalTime: 192 ms`, `WaitTime: 197 ms`; restored `Teh Hijau` in `Dijeda` state at `0:08 / 3:32` with highlighted synced lyric line 2 (`Misteri lenyap senang`) and focused `Putar` button |
| D-Pad Navigation Tabs (`Search`, `Library`, `Settings`) | Verified via D-pad traversal and screenshot inspection across `Cari`, `Koleksi` (persisted queue + reorder/remove/clear + mini-player bar), and `Pengaturan` (`Autoplay: AKTIF`, cache/attribution block) |
| Crash / ANR / `AlunaraPlayback` Logcat Audit | `0` crashes, `0` ANRs, `0` `AlunaraPlayback` warnings/errors after final `VISIONOS` fix |

---

## 5. Manual TV Remote Checklist (Owner Verification)

Automated ADB input tests covered D-pad navigation, Home guest playback start, active audio output, synced lyrics, stop-on-exit, and relaunch restoration. Suggested quick manual sanity checks using the physical Changhong remote before M3:

1. **On-Screen TV Keyboard Search (`Cari`):** Type a song or artist query with the Changhong IME keyboard, verify song/album/artist sections render, and play or enqueue a search result.
2. **D-Pad Progress Seek (`Now Playing`):** Move focus UP from `Jeda`/`Putar` to the progress bar and press Left/Right (`±10s`) to confirm audio seeks cleanly and synced lyrics follow the new timestamp.
3. **Queue Reorder / Skip (`Koleksi` & `Now Playing`):** Use `Sblm` / `Brkt` in Now Playing and `Naik` / `Turun` / `Hapus` in `Koleksi`, then close and reopen the app to verify queue edits persist in Room.
4. **Audio Output Inspection:** Listen directly on TV speakers during `Teh Hijau` playback and verify immediate silence when pressing the remote Home button.

---

## 6. M2.1 Fullscreen Material 3 UI, Official Logos & Word-Sync Lyrics Verification

Updated and verified on Changhong `AI PONT` (`192.168.100.124:5555`) after UI, icon, logo, and lyrics-priority enhancements:

| Check | Measured M2.1 Outcome |
|---|---|
| **Gradle Unit Tests (5 modules, 12 suites)** | **`42 / 42` passed** (`0` failed, `0` errors, `0` skipped): `:core:domain` `21/21`, `:core:music` `8/8`, `:core:lyrics` `5/5`, `:core:data` `5/5`, `:app` `3/3` |
| **Lint & Debug APK Size** | `:app:lintDebug` passed (`0`); `app-debug.apk` = `77,577,137` bytes (`73.98 MiB`, includes unminified `material-icons-extended`; M4 Release R8 strips unused icons) |
| **Fullscreen Edge-to-Edge & Material 3 Icons** | Verified `WindowCompat.setDecorFitsSystemWindows(window, false)` + hidden system bars, edge-to-edge Dark Forest Green layout with zero outer border, `Icons.Rounded.*` across rail/cards/transport/timeline, uniform 2-line media card height, and sticky bottom Mini Player |
| **Branding Assets** | Wired `1:1` universal launcher icons (`mipmap-mdpi`..`xxxhdpi` `48..192` + `alunara_logo_square.png`) and `16:9` Leanback banners (`drawable-xhdpi/tv_banner.png` `320x180`, `xxhdpi` `480x270` + `alunara_logo_wide.png`) |
| **Word-Sync Priority (`Word -> Line -> Plain`) & Switcher** | `SimpMusic richSyncLyrics` prioritized first with Room ELRC lossless round-trip serialization. Verified live word-by-word highlight on `Teh Hijau` (`0:06 / 3:32`, `Sumber: SimpMusic · Sinkron kata`) and in-player `Ganti sumber` toggle (`SimpMusic -> LrcLib` on `Negoro Angin`, `0:20 / 5:12`) |
| **Changhong Memory, Audio & Stop-on-Exit** | Cold launch `9742 ms` (`WaitTime: 9818 ms` after `pm clear`); active playback `TOTAL PSS: 121,568 KB` (`Java Heap: 11,532 KB`, `Native Heap: 10,236 KB`), AudioFlinger `1` active track @ `48000 Hz` (`Standby: no`), `KEYCODE_HOME` -> `0` active tracks, `0` crashes, `0` ANRs, `0` `AlunaraPlayback` errors |

---

## 7. M2.2 Android TV 10-Foot Audit, Whole-Word Sync & UI/UX Consistency Polish

Refined and re-verified on physical Changhong `AI PONT` (`192.168.100.124:5555`, `1280x720`, 32-inch 10-foot viewing distance) after addressing five M2.1 polish items:

| Check | Measured M2.2 Outcome |
|---|---|
| **Gradle Unit Tests (5 modules, 12 suites)** | **`44 / 44` passed** (`0` failed, `0` errors, `0` skipped): `:core:domain` `22/22`, `:core:music` `8/8`, `:core:lyrics` `6/6`, `:core:data` `5/5`, `:app` `3/3` |
| **Lint & Debug APK Size** | `:app:lintDebug` passed (`0` errors; 1 minor non-blocking Kotlin deprecation warning on `Icons.Rounded.PlaylistAdd` in `SearchScreen.kt:212`); `app-debug.apk` = `78,070,377` bytes (`74.45 MiB`) |
| **Seamless Transparent Emblem (`ic_alunara_mark`)** | Extracted `256x256` RGBA transparent cutout (`29,203` bytes) from `.production/logo/logo alunara 1_1.png` (`1254x1254`); removed boxed `alunara_logo_square.png` / `alunara_logo_wide.png` while retaining `mipmap-*` & Leanback `tv_banner.png`. Visually verified seamless blend on rail header with zero box background |
| **UI/UX Consistency & Restrained TV Motion (`AlunaraDimens`)** | Unified layout tokens (`RailCollapsedWidth = 76.dp`, `RailExpandedWidth = 216.dp`, `RailItemHeight = 48.dp`, `ControlHeight = 44.dp`, `SongRowHeight = 60.dp`, `MiniPlayerHeight = 68.dp`, `CardWidth = 152.dp`, `CardHeight = 216.dp`, `CardArtworkSize = 132.dp`, `RadiusControl = 12.dp`, `RadiusCard = 14.dp`, `FocusBorderWidth = 2.dp`). Switched navigation rail to overlay (`220ms` width animation) over stationary `padding(start = 76.dp)` content, constant `2.dp` animated focus borders (`150ms`), zero-shift `SeekableProgressBar` (`alpha`-faded hint), and `Crossfade` navigation (`220ms` / `160ms`). Confirmed zero card jump between collapsed and expanded rail screenshots |
| **32-Inch TV Lyrics & Compact Source Switcher** | Replaced oversized text button with `32.dp` circular `SwapHoriz` button (`18.dp` icon) in Lyrics header. Scaled lyrics typography to `26.sp` active bold (`lineHeight = 36.sp`), `19.sp` inactive (`lineHeight = 28.sp`), and `20.sp` plain (`lineHeight = 30.sp`) with `16.dp` spacing |
| **Whole-Word Sync Reconstruction** | Added hyphen-continuation merge + `LrcParser.isSyllableFragmented` + `LrcParser.repairWordBoundaries` (greedy lookahead up to 6 syllables aligned with clean reference words such as `LrcLib`) inside `LrcParser` and `ArchiveTuneLyricsAdapter`. Verified live on `Teh Hijau` (`0:06 / 3:32`, `Sumber: SimpMusic · Sinkron kata`) displaying intact highlighted words (`Hari-hari berulang`, `Misteri lenyap senang`, `Ada saran kudengar`) |
| **Changhong Memory, Audio & Stop-on-Exit** | Cold launch after `pm clear`: `LaunchState: COLD`, `TotalTime: 9769 ms`, `WaitTime: 9793 ms`; playback `TOTAL PSS: 129,995 KB` (`Java Heap: 16,844 KB`, `Native Heap: 11,392 KB`, `Code: 67,376 KB`, `Stack: 1,900 KB`, `Graphics: 12,700 KB`, `TOTAL RSS: 178,456 KB`, `TOTAL SWAP PSS: 221 KB`), AudioFlinger `1` active track of `3` @ `48000 Hz` (`Standby: no`), `KEYCODE_HOME` + `3s` -> `0` active tracks of `2`, `0` crashes, `0` ANRs, `0` `AlunaraPlayback` errors |

### Android TV 10-Foot UI/UX Audit Summary (`19.0 / 20`)

Evaluated strictly for a **32-inch Android TV (`1280x720 @ 213 dpi`, D-pad navigation)** rather than phone/tablet/desktop targets:

1. **Accessibility & D-Pad Ergonomics (`3.5 / 4`):** Clear focus rings (`2.dp` `#9EDBB3`), high-contrast `#101813` surface + large `26.sp`/`19.sp` TV lyrics, `48.dp` rail items, `44.dp` buttons, and `60.dp` rows. The compact `32.dp` provider switcher button is intentionally sized as a secondary D-pad action to keep the Lyrics header uncluttered; additional `contentDescription` / semantics labels can be polished in M3.
2. **Performance & Stability (`4.0 / 4`):** Overlay rail eliminates horizontal relayout during focus transitions; constant focus borders prevent vertical jitter; zero crashes/ANRs and immediate AudioFlinger release on `KEYCODE_HOME`.
3. **Appearance & Theming (`4.0 / 4`):** Cohesive Dark Forest Green palette (fixed TV-first dark surface per PRD), transparent `ic_alunara_mark` emblem, full-bleed left artwork fade in Now Playing, uniform `12.dp`/`14.dp` radii.
4. **Platform Conformance (`3.5 / 4`):** Fullscreen edge-to-edge Leanback experience with Material 3 `Icons.Rounded.*` and D-pad seek support; 1 non-blocking Kotlin deprecation warning remains on `Icons.Rounded.PlaylistAdd` (`SearchScreen.kt:212`).
5. **Adaptivity & Spatial Consistency (`4.0 / 4`):** Fixed `76.dp` content anchor under `216.dp` overlay rail and locked `152.dp x 216.dp` 2-line media cards keep rows aligned across Home, Search, and Library.
