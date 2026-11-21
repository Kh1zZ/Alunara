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
