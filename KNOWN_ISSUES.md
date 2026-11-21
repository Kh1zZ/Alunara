# Known Issues & Release Blockers

**Project Stage:** Milestone M2 — Playable Alpha (`0.1.0-alpha`, Pending M2 Review)  
**Release Eligibility:** Internal Alpha (`0.1.0-alpha`) verified in Guest Mode on physical Changhong TV (`192.168.100.124:5555`); Beta (`0.1.0-beta`) remains blocked by `KI-02` (`FG-04`).

---

## Active Blockers & Open Gates

| ID | Severity | Gate | Summary | Impact & Next Action |
|---|---|---|---|---|
| **KI-01** | **Blocker** | FG-02 | **YouTube Music InnerTube & audio-only access compliance** | `rukamori/core` relies on InnerTube endpoints and optional NewPipe Extractor cipher deobfuscation (`NewPipe.kt`) rather than official YouTube Data API v3 playback streams. Google/YouTube API Terms restrict separating audio/video components and unapproved caching. Kept scoped to Guest Mode internal evaluation for M1/M2. |
| **KI-02** | **Blocker for Beta** | FG-04 | **Account authentication mismatch (`PlaybackAuthState` vs Google TV Device OAuth)** | Audited `rukamori/core` (`PlaybackAuthState.kt`) authenticates account requests via YouTube web session cookies (`cookie`, `visitorData`, `dataSyncId`) and `poToken` fields. Standard Google TV Limited-Input Device OAuth does not grant private YouTube Music InnerTube API access, and PRD §10.1 forbids insecure cookie copy-paste or password collection. Until a compliant, verified auth flow is proven end-to-end, maximum achievable release is **Alpha internal** (Guest Mode). |
| **KI-03** | **Design Gate** | FG-03 | **32 MB audio disk cache disabled by default** | PRD §9.1 defines a 32 MB conditional audio cache budget, but FG-03 forbids enabling audio-to-disk caching unless content-provider terms explicitly allow it. `ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED` is locked to `false` (`0` bytes). |
| **KI-04** | **Partially Mitigated (M2)** | M2 / API 28 | **Physical 1 GB ARMv7 TV debug smoke verified; API 28 runtime & M4 release profiling pending** | M2 verification passed clean on physical **Changhong `AI PONT`** (Android 11 / API 30, `armeabi-v7a`, `low_ram=true`, `MemTotal: 934540 kB`, `app-debug.apk` = `45,396,710` bytes / `43.29 MiB`): clean cold start `TotalTime: 6759 ms` (`WaitTime: 6817 ms`), idle `TOTAL PSS: 98,180 KB` (`Java Heap: 11,308 KB`), active `VISIONOS` Media3 audio playback `TOTAL PSS: 91,584 KB` (`Java Heap: 12,824 KB`, `Native Heap: 7,628 KB`, `Code: 38,764 KB`, `Graphics: 3,988 KB`), and HOT relaunch `TotalTime: 192 ms` (`WaitTime: 197 ms`) with zero Rhino JS invocations for guest direct streams (`visitorData` + `VISIONOS`). Android 9 (API 28) target compatibility testing and M4 release profiling (R8 + ABI splits to shrink debug APK `.apk mmap` / cold start) remain pending. |
| **KI-05** | **Resolved (M2)** | Env / Hardware | **Physical Changhong `AI PONT` TV hardware & ADB TCP/IP verification completed** | Verified over ADB TCP/IP (`192.168.100.124:5555`) against physical `Changhong/DVB92/redwood` (`mt5867`, `armeabi-v7a`, `1280x720 @ 213 dpi`, Android 11 API 30, `low_ram=true`, hardware `audio/flac`, `audio/mp4a-latm-hw`, `audio/opus`, `audio/vorbis`, `audio/mpeg`). Guest Home feed, live `VISIONOS` audio playback via Media3 (`48000 Hz`, 1 active AudioFlinger track), synced lyrics highlight, `KEYCODE_HOME` stop-on-exit (`0` active tracks), Room queue persistence across relaunch, and D-pad tab navigation verified with 0 crashes and 0 ANRs (see `docs/M2_HARDWARE_SMOKE.md`). |

---

## Status Rules (per PRD §15)

- **Alpha internal (`0.1.0-alpha`)** is the maximum allowable label if guest playback is viable while `KI-02` (account login) remains unresolved.
- **Beta (`0.1.0-beta`)** requires all P1/P2 requirements and Feasibility Gates (`FG-01`–`FG-05`) plus real Changhong L32G7N benchmarks (`KI-05`) to pass with recorded evidence.
