# Alunara — M0 Preflight & Feasibility Audit

**Milestone:** M0 — Preflight & Feasibility (P0)  
**Audit Date:** 2026-11-21  
**Planned `applicationId`:** `com.alunara.app`  
**Audit Scope:** Base engines (`rukamori/core`, `rukamori/lyrics`), Feasibility Gates (`FG-01`–`FG-05`), Windows build environment, and Android TV hardware readiness.

---

## 1. Baseline Scope & Locked Decisions

From the approved baseline specifications (`Alunara_PRD_v0.2.md` and `Alunara_UIUX_Design_Specification_v1.0.md`):

- **Platform & Hardware Target:** Android TV (`16:9`, 720p/1080p priority), `minSdk = 28` (Android 9), **1 GB RAM**, reference device **Changhong L32G7N**.
- **Product Scope:** Independent **audio-only** YouTube Music client (`com.alunara.app`). Video playback, offline downloads, and background playback after leaving the app are explicitly **out of scope**.
- **Playback Priority & Lifecycle:** Stable audio playback takes highest priority over search/prefetch/lyrics. Leaving the app (Home/exit) immediately stops playback and releases the single Media3/ExoPlayer instance while preserving queue, track index, and playback position in Room.
- **Cache & Concurrency Budgets:**
  - Dynamic metadata + artwork + lyrics disk cache: **128 MB max aggregate**.
  - Audio disk cache: **32 MB conditional budget, DISABLED by default** pending FG-03 verification.
  - Lyrics requests: selective provider activation, max **2 concurrent hedged requests**, max **1 next-track prefetch** (cancelled immediately on queue change).
- **UI/UX Decisions (Locked Tahap 1–6):**
  - Material Design 3 for TV, **Fixed Dark Forest Green** (`#101813` bg, `#1D2B22` surface, `#85BDA0` primary accent, `#E3EEE5` primary text), dynamic color prohibited.
  - Remote-first D-pad navigation with collapsible left rail (`Home`, `Search`, `Library`, `Settings`), crisp high-contrast focus outlines (no scale-only focus).
  - Now Playing: balanced artwork + **read-only** persistent lyrics panel with explicit **Original / Romanized** toggle; fallback to plain lyrics or hide empty lyrics panel cleanly.
  - Localization: Bahasa Indonesia & English UI; lyrics remain in original language; JP/KR romanization gated on curated fixtures (no fabricated readings).

---

## 2. Base Engine Audit — `rukamori/core` (ArchiveTune Core)

- **Repository:** <https://github.com/rukamori/core>
- **Default Branch:** `main`
- **Audited Pinned Commit:** `83f829007efac2bcd5be1c3e266a01fe617c6f57` (`2026-11-20T15:36:04Z` — *"feat(innertube): add songCounterpart function and PlaylistPanelVideoWrapperRenderer model"*)
- **License:** `GPL-3.0` (`LICENSE` present at repo root; individual Kotlin files carry GPL-3.0 §4/§5 header notices: *"Do not remove or alter this notice."*).
- **Source Footprint:** 107 files in tree; root Kotlin package `moe.rukamori.archivetune.innertube` (single Gradle JVM module).

### Build Configuration & Dependencies
- **Plugins:** `kotlin-jvm`, `kotlin-serialization`.
- **Toolchain:** `kotlin { jvmToolchain(21) }`.
- **Declared Dependencies** (`build.gradle.kts`, resolved via parent version catalog — verified against `rukamori/ArchiveTune` `gradle/libs.versions.toml`):
  - `io.ktor:ktor-client-core`, `ktor-client-okhttp`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-client-encoding` (`3.6.1` in ArchiveTune)
  - `com.squareup.okhttp3:okhttp-dnsoverhttps` (`5.5.1` in ArchiveTune)
  - `org.brotli:dec` (`0.1.2`)
  - `com.github.TeamNewPipe:NewPipeExtractor` (`0.27.1` in ArchiveTune)
  - `com.google.re2j:re2j` (`1.8`)
  - `junit:junit` (`4.13.2`, test)

### Architectural Findings & Risks for Alunara
1. **Network Fail-Closed Default (`NetworkGatekeeper.kt`):** `NetworkGatekeeper` initializes `connectionBlocked = AtomicBoolean(true)`. All OkHttp requests are blocked until `NetworkGatekeeperControl.allowConnections()` is explicitly invoked during app startup / adapter initialization.
2. **Auth Model (`PlaybackAuthState.kt`):** `PlaybackAuthState` tracks `cookie`, `visitorData`, `dataSyncId`, `accountIndex`, `poToken`, `poTokenGvs`, `poTokenPlayer`, `poTokenSubs`, and `webClientPoTokenEnabled`. Login detection checks `hasYouTubeLoginCookie(cookie)`. This is a **web cookie / PO token model**, NOT an official Google TV OAuth bearer-token model.
3. **Stream Deobfuscation Footprint (`NewPipe.kt`):** Uses `org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager` (backed by Mozilla Rhino JS execution) for signature/throttling deobfuscation. On **1 GB RAM / Android 9**, JS player extraction can introduce startup/memory spikes and cache failures (note built-in `withJavaScriptPlayerCacheRecovery`). Needs strict lazy initialization and early PSS memory profiling.
4. **Android 9 (API 28) Compatibility:** `jvmToolchain(21)` compiles bytecode targeting JVM 21 unless overridden or configured with AGP `compileOptions` / `jvmTarget` alignment and core library desugaring. Must be verified in M1 clean build + API 28 emulator smoke test.
5. **Recommended M1 Integration Strategy:** Pin `rukamori/core` at commit `83f829007efac2bcd5be1c3e266a01fe617c6f57` via **Git submodule** (or version-controlled mirror/vendoring if toolchain overrides cannot be supplied from root Gradle without patching `build.gradle.kts`), wrapped behind a thin Alunara adapter (`:core-music` or internal adapter package) exposing `MusicBackend`-aligned domain contracts.

---

## 3. Base Engine Audit — `rukamori/lyrics` (ArchiveTune Lyrics)

- **Repository:** <https://github.com/rukamori/lyrics>
- **Default Branch:** `main`
- **Audited Pinned Commit:** `aa6fcd688ae464a56b044989f3bd7632afb8cadf` (`2026-11-11T11:41:22Z` — *"Merge pull request #5 from adam-adrian/feat/musicmatch-reimp"*)
- **License:** `GPL-3.0` (`LICENSE` at root).
- **Source Footprint:** 85 files across **11 independent JVM Gradle submodules**:
  - `:kugou`, `:lrclib`, `:simpmusic`, `:paxsenix`, `:betterlyrics`, `:unison`, `:youlyplus`, `:lrcred`, `:binimum`, `:megalobiz`, `:musixmatch`.

### Build Configuration & Dependencies
- **Catalog (`gradle/libs.versions.toml`):** Kotlin `2.1.0`, Ktor `3.1.0`, JUnit `4.13.2`.
- **Toolchain:** Every module specifies `kotlin { jvmToolchain(21) }`.
- **HTTP Engine Split:** Modules vary in Ktor engine dependency:
  - `lrclib` & `paxsenix` pull **both** `ktor-client-okhttp` and `ktor-client-cio`.
  - `simpmusic` uses `ktor-client-cio`.
  - `betterlyrics`, `unison`, `youlyplus`, `lrcred`, `binimum`, `megalobiz`, `musixmatch` use `ktor-client-okhttp`.
  - `musixmatch` test suite references `:betterlyrics` via `project(project.path.substringBeforeLast(':') + ":betterlyrics")`.

### Architectural Findings & Provider Notes
1. **Decoupled Provider Objects:** Each module exposes a standalone singleton (`getLyrics(...) -> Result<String>` or `Result<UnisonLyrics>`, plus optional `getAllLyrics(..., callback)`). Some clients are initialized eagerly (`val client`) while others use `by lazy`, so **including unused modules or touching unused singletons wastes RAM** on 1 GB TVs.
2. **Selective Module Inclusion Required:** Per PRD §7.1, Alunara must NOT enable all 11 providers at once. Inclusion should happen selectively in `settings.gradle.kts` to avoid pulling redundant Ktor CIO/OkHttp engines and unused classes into the APK.
3. **Provider-Specific Attributions & Risks:**
   - `lrcred` (`lrc.red`): README mandates crediting data with a visible link to <https://lrc.red> wherever shown and setting `LrcRed.setUserAgent(appName, versionName)`.
   - `unison` (`unison.betterlyrics.org`): Returns `submitterName` (`submitter.displayName`) for attribution.
   - `betterlyrics`: Includes `TTMLParser` handling word-by-word timing, CJK characters, and transliteration (useful baseline reference for line-synced down-conversion per PRD §7.1, though `TTMLParser` package path in docs shows `moe.rukamori.rukamori.betterlyrics` — verify actual Kotlin file package when integrating).
   - `paxsenix` / proxy-based providers: Depend on third-party proxy uptime and external upstream scraping; lower reliability/compliance confidence than direct open databases like `lrclib`.
4. **Recommended M1 Initial Provider Shortlist (Subject to FG-05 Approval):**
   - Primary Tier: **`:lrclib`** (synced + plain LRC, direct open API, good fallback) and **`:simpmusic`** (videoId-indexed YouTube Music match) or **`:betterlyrics`** (TTML + CJK parser, check terms).
   - Defer `paxsenix`, `musixmatch`, `megalobiz`, `lrcred`, `binimum`, `youlyplus`, `kugou`, `unison` until each provider's terms/rate-limits/attribution UI impact are cleared under FG-05.

---

## 4. Feasibility & Compliance Gates (`FG-01` – `FG-05`)

| Gate | Requirement | M0 Audit Finding | Status | Decision / Mitigation Needed |
|---|---|---|---|---|
| **FG-01** | Open-source license compliance (`GPL-3.0`) | Both `rukamori/core` and `rukamori/lyrics` are GPL-3.0. `LICENSE`, `CREDITS.md`, and `README.md` attribution initialized at M0. | **PASS (M0)** / Ongoing | Preserve upstream GPL-3.0 file headers; maintain full source availability; add in-app license/notices UI and M1 transitive dependency audit. |
| **FG-02** | YouTube / YouTube Music access & audio-only playback compliance | `rukamori/core` accesses unofficial InnerTube APIs and uses `NewPipeExtractor` for stream extraction/deobfuscation. YouTube API Services Developer Policies prohibit unauthorized stream scraping, separating audio/video without consent, and unapproved caching. | **NEEDS DECISION (Blocker)** | Project owner must explicitly decide whether to proceed on the basis of `rukamori/core` for internal/experimental/GPL open-source distribution or constrain scope. Do not bypass DRM or access controls. |
| **FG-03** | Audio disk cache (`32 MB` conditional budget) | Policy risk on disk caching audio streams without explicit provider permission. | **DISABLED BY DEFAULT** | Keep audio disk caching completely off (`0 MB`) in M1+; only keep metadata/artwork/lyrics dynamic cache (max 128 MB aggregate). |
| **FG-04** | Account login & private API feasibility | `rukamori/core` expects web login cookies (`PlaybackAuthState.cookie` + `poToken`). Google TV Limited-Input Device OAuth alone does not authorize InnerTube private endpoints. PRD §10.1 forbids password prompts or insecure manual cookie pasting. | **BLOCKER FOR BETA** | Target **M2 Alpha Internal (Guest Mode only)** first. Before M3, verify whether a compliant Android TV-safe authentication flow exists that populates `PlaybackAuthState` without violating security/policy rules; otherwise Beta remains blocked. |
| **FG-05** | Third-party lyrics provider compliance & attribution | `lrcred` mandates visible link to `lrc.red` + custom User-Agent; `unison` exposes `submitterName`; proxy/commercial providers (`musixmatch`, `paxsenix`, `kugou`, etc.) carry varying terms/rate-limit risks. | **NEEDS DECISION** | Approve an explicit M1 shortlist (e.g., `lrclib` + `simpmusic` / `betterlyrics`) and exclude unverified providers until reviewed. |

---

## 5. Windows Development Environment & Hardware Preflight

Verified locally on Windows 10/11 (`2026-11-21`):

| Component | Detected State | Action Needed for M1 / M2 |
|---|---|---|
| **JDK / `JAVA_HOME`** | `JAVA_HOME` points to `C:\Users\KH1ZZ\.jdks\ms-17.0.20.1` (JDK 17). JDK 21 IS installed at `C:\Program Files\Java\jdk-21.0.12.1` and `C:\Users\KH1ZZ\.jdks\jbr-21.0.11`; JDK 25 at `C:\Program Files\Java\jdk-25.0.4.1`. | Align `JAVA_HOME` / Gradle JDK to JDK 21 (since `rukamori/core` & `rukamori/lyrics` use `jvmToolchain(21)`) and test AGP bytecode compatibility targeting Android 9 (API 28). |
| **Android SDK** | Installed at `C:\Users\KH1ZZ\AppData\Local\Android\Sdk` (platforms `android-34`, `android-35`, `android-37.0`; build-tools `34.0.0`, `35.0.0`), but `ANDROID_HOME` and `ANDROID_SDK_ROOT` env vars are currently **unset**. | Configure `sdk.dir=C\:\\Users\\KH1ZZ\\AppData\\Local\\Android\\Sdk` in `local.properties` (gitignored) or set `ANDROID_HOME` in M1; verify whether `android-28` emulator image is needed for API 28 smoke tests. |
| **Gradle Cache** | Cached Gradle distributions detected: `8.9`, `8.10.2`, `9.6.0`. | Confirm AGP/Kotlin/Gradle matrix in M1 before committing `gradle-wrapper.properties`. |
| **ADB & Changhong L32G7N** | `adb.exe` (`1.0.41` / `37.0.1`) present at `platform-tools\adb.exe`. `adb devices` currently returns **0 devices**. `scrcpy` is not on `PATH`. | Hardware connection remains **UNRESOLVED**. Enable Developer Options / ADB on Changhong L32G7N (USB / `adb tcpip 5555` or network ADB) and verify `adb connect` + `dumpsys meminfo` + ABI (`armeabi-v7a` vs `arm64-v8a`) before M2 hardware validation. |
| **Git & GitHub CLI** | `git` configured (`user.name = Falakhis`, `user.email = falakhis456@gmail.com`). `gh` CLI installed but **not logged in**. | Authenticate `gh auth login` (or configure git remote manually) when ready to push repository and run GitHub Actions CI. |

---

## 6. M0 Human Checkpoint — Decisions Required Before Milestone M1

Per PRD §14 (*Kebijakan otonomi Antigravity*), implementation does **not** advance to Milestone M1 until the project owner reviews and confirms:

1. **FG-02 Compliance Direction:** Confirm proceeding with `rukamori/core` (`83f829007efac2bcd5be1c3e266a01fe617c6f57`) for Guest Mode internal Alpha testing while documenting platform policy risks (`KI-01`).
2. **FG-04 Scope Confirmation:** Acknowledge `KI-02` as an active Beta blocker and confirm focusing M1/M2 strictly on **Guest Mode** first.
3. **FG-05 Initial Lyrics Provider Shortlist:** Approve which `rukamori/lyrics` submodules (`aa6fcd688ae464a56b044989f3bd7632afb8cadf`) to enable initially in M1 (recommended minimal set: `lrclib` + `simpmusic`, keeping `lrcred` and proxy modules disabled initially).
4. **Base Engine Integration Mechanism:** Confirm integrating `rukamori/core` and `rukamori/lyrics` as **pinned Git submodules** (recommended for clean upstream GPL-3.0 commit pinning and attribution) vs vendored source copies, noting that `jvmToolchain(21)` compatibility on API 28 may require Gradle toolchain configuration.
5. **Hardware & Emulator Availability:** Confirm whether Changhong L32G7N can be reached via USB/LAN ADB for M2 hardware benchmarks, or if an Android TV API 28 emulator image should be provisioned first for M1/M2 smoke tests.

---

## M0 Checkpoint Sign-Off (Approved — 2026-11-21)

Owner decisions recorded before entering Milestone M1:

- **Application ID / Package Root:** `com.alunara.app`.
- **Integration Mechanism:** Source vendoring inside `third_party/` (see [third_party/VENDORING.md](../third_party/VENDORING.md)), keeping upstream GPL-3.0 `LICENSE` and file headers intact.
- **FG-05 Lyrics Shortlist Enabled in M1:** `:third_party:lyrics:lrclib`, `:third_party:lyrics:simpmusic`, and `:third_party:lyrics:kugou` (8 other upstream modules excluded).
- **FG-02 / FG-03 / FG-04 Compliance Posture:** M1/M2 target **Guest Mode** first (`KI-02` stays an active Beta blocker), audio disk cache strictly **disabled (`0 MB`)**, `minSdk = 28` / `compileSdk = 35` / `targetSdk = 35`.
- **M1 Toolchain Finding:** `third_party/core` (`NewPipe.kt:83`) requires OkHttp 5's non-null `Response.body`, while `okhttp-android:5.5.1` demands `compileSdk >= 37`. Pinning **`okhttp = "5.3.2"`** in `gradle/libs.versions.toml` satisfies `NewPipe.kt` and builds cleanly against `compileSdk = 35` + AGP `8.7.3` + `desugar_jdk_libs:2.1.5` under JDK 21 without modifying any vendored upstream files.
