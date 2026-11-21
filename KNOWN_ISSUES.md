# Known Issues & Release Blockers

**Project Stage:** Milestone M1 — Foundation Complete (Pending M1 Review)  
**Release Eligibility:** Neither Alpha (`0.1.0-alpha`) nor Beta (`0.1.0-beta`) is claimed yet.

---

## Active Blockers & Open Gates

| ID | Severity | Gate | Summary | Impact & Next Action |
|---|---|---|---|---|
| **KI-01** | **Blocker** | FG-02 | **YouTube Music InnerTube & audio-only access compliance** | `rukamori/core` relies on InnerTube endpoints and optional NewPipe Extractor cipher deobfuscation (`NewPipe.kt`) rather than official YouTube Data API v3 playback streams. Google/YouTube API Terms restrict separating audio/video components and unapproved caching. Kept scoped to Guest Mode internal evaluation for M1/M2. |
| **KI-02** | **Blocker for Beta** | FG-04 | **Account authentication mismatch (`PlaybackAuthState` vs Google TV Device OAuth)** | Audited `rukamori/core` (`PlaybackAuthState.kt`) authenticates account requests via YouTube web session cookies (`cookie`, `visitorData`, `dataSyncId`) and `poToken` fields. Standard Google TV Limited-Input Device OAuth does not grant private YouTube Music InnerTube API access, and PRD §10.1 forbids insecure cookie copy-paste or password collection. Until a compliant, verified auth flow is proven end-to-end, maximum achievable release is **Alpha internal** (Guest Mode). |
| **KI-03** | **Design Gate** | FG-03 | **32 MB audio disk cache disabled by default** | PRD §9.1 defines a 32 MB conditional audio cache budget, but FG-03 forbids enabling audio-to-disk caching unless content-provider terms explicitly allow it. `ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED` is locked to `false` (`0` bytes). |
| **KI-04** | **Partially Mitigated (M1)** | M1 / API 28 | **`jvmToolchain(21)` build verified; runtime API 28 & 1 GB RAM profiling pending** | Clean M1 build, Room KSP schema generation, lint, and debug APK packaging (`minSdk = 28`, `compileSdk = 35`, AGP `8.7.3`, Kotlin `2.1.21`, `okhttp = 5.3.2`, `desugar_jdk_libs:2.1.5`) **PASSED** (`app-debug.apk` = 44,035,122 bytes / 42.00 MB). Runtime execution on Android 9 (API 28) and PSS memory profiling of `NewPipeExtractor` + Rhino JS + debug APK footprint on 1 GB RAM remain pending M2 emulator/hardware tests. |
| **KI-05** | **Unverified** | Env / Hardware | **Changhong L32G7N hardware & ADB connectivity not verified** | Host preflight shows `adb devices` empty (0 attached devices), `scrcpy` not found on `PATH`, and `ANDROID_HOME` / `ANDROID_SDK_ROOT` unset (SDK configured via gitignored `local.properties` at `C:\Users\KH1ZZ\AppData\Local\Android\Sdk`). Network ADB (`adb tcpip 5555` / `adb connect`) on Changhong L32G7N firmware remains unverified; beta gates cannot pass without physical hardware evidence. |

---

## Status Rules (per PRD §15)

- **Alpha internal (`0.1.0-alpha`)** is the maximum allowable label if guest playback is viable while `KI-02` (account login) remains unresolved.
- **Beta (`0.1.0-beta`)** requires all P1/P2 requirements and Feasibility Gates (`FG-01`–`FG-05`) plus real Changhong L32G7N benchmarks (`KI-05`) to pass with recorded evidence.
