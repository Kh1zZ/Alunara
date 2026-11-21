# Known Issues & Release Blockers

**Project Stage:** Milestone M0 (Preflight & Feasibility Audit)  
**Release Eligibility:** Neither Alpha (`0.1.0-alpha`) nor Beta (`0.1.0-beta`) is claimed yet.

---

## Active Blockers & Open Gates

| ID | Severity | Gate | Summary | Impact & Next Action |
|---|---|---|---|---|
| **KI-01** | **Blocker** | FG-02 | **YouTube Music InnerTube & audio-only access compliance** | `rukamori/core` relies on InnerTube endpoints and optional NewPipe Extractor cipher deobfuscation (`NewPipe.kt`) rather than official YouTube Data API v3 playback streams. Google/YouTube API Terms restrict separating audio/video components and unapproved caching. Requires explicit owner compliance decision at M0 checkpoint before implementation proceeds. |
| **KI-02** | **Blocker for Beta** | FG-04 | **Account authentication mismatch (`PlaybackAuthState` vs Google TV Device OAuth)** | Audited `rukamori/core` (`PlaybackAuthState.kt`) authenticates account requests via YouTube web session cookies (`cookie`, `visitorData`, `dataSyncId`) and `poToken` fields. Standard Google TV Limited-Input Device OAuth does not grant private YouTube Music InnerTube API access, and PRD §10.1 forbids insecure cookie copy-paste or password collection. Until a compliant, verified auth flow is proven end-to-end, maximum achievable release is **Alpha internal** (Guest Mode). |
| **KI-03** | **Design Gate** | FG-03 | **32 MB audio disk cache disabled by default** | PRD §9.1 defines a 32 MB conditional audio cache budget, but FG-03 forbids enabling audio-to-disk caching unless content-provider terms explicitly allow it. Audio disk caching MUST remain disabled by default in M1+. |
| **KI-04** | **Pending M1** | M1 / API 28 | **`jvmToolchain(21)` on Android 9 (API 28) & NewPipe/Rhino RAM footprint** | Both `rukamori/core` and all `rukamori/lyrics` modules declare `kotlin { jvmToolchain(21) }`. Android 9 (API 28) lacks native Java 21 APIs and requires AGP bytecode compatibility / core library desugaring validation. Additionally, `NewPipeExtractor` + Mozilla Rhino JS execution in `NewPipe.kt` may cause significant heap spikes on a **1 GB RAM** TV and must be benchmarked early. |
| **KI-05** | **Unverified** | Env / Hardware | **Changhong L32G7N hardware & ADB connectivity not verified** | Host preflight shows `adb devices` empty (0 attached devices), `scrcpy` not found on `PATH`, and `ANDROID_HOME` / `ANDROID_SDK_ROOT` unset (SDK present at `C:\Users\KH1ZZ\AppData\Local\Android\Sdk`). Network ADB (`adb tcpip 5555` / `adb connect`) on Changhong L32G7N firmware remains unverified; beta gates cannot pass without physical hardware evidence. |

---

## Status Rules (per PRD §15)

- **Alpha internal (`0.1.0-alpha`)** is the maximum allowable label if guest playback is viable while `KI-02` (account login) remains unresolved.
- **Beta (`0.1.0-beta`)** requires all P1/P2 requirements and Feasibility Gates (`FG-01`–`FG-05`) plus real Changhong L32G7N benchmarks (`KI-05`) to pass with recorded evidence.
