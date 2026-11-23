<div align="center">

  <img src="app/src/main/res/drawable-nodpi/ic_alunara_mark.png" width="144" height="144" alt="Alunara Logo" style="border-radius: 22%" />

  <h1>Alunara</h1>

  <p align="center">
    <strong>Let the Music Flow.</strong> Lightweight, 60 FPS YouTube Music Built Natively for Android TV.
    <br />
    <em>Audio-only, privacy-first, D-pad native, and engineered to stay silky smooth even on 1 GB RAM smart TVs.</em>
  </p>

  <p align="center">
    <a href="#philosophy"><b>Philosophy</b></a> •
    <a href="#features"><b>Features</b></a> •
    <a href="#screenshots"><b>Showcase</b></a> •
    <a href="#download-now"><b>Download</b></a> •
    <a href="#development"><b>Development</b></a> •
    <a href="CHANGELOG.md"><b>Changelog</b></a> •
    <a href="https://github.com/Kh1zZ/Alunara/issues"><b>Issues</b></a>
  </p>

  <div align="center">
    <a href="https://github.com/Kh1zZ/Alunara/releases/latest">
      <img src="https://img.shields.io/github/v/release/Kh1zZ/Alunara?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Latest Release" />
    </a>
    <a href="https://github.com/Kh1zZ/Alunara/releases">
      <img src="https://img.shields.io/github/downloads/Kh1zZ/Alunara/total?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Total Downloads" />
    </a>
    <a href="https://github.com/Kh1zZ/Alunara/stargazers">
      <img src="https://img.shields.io/github/stars/Kh1zZ/Alunara?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="GitHub Stars" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/github/license/Kh1zZ/Alunara?style=for-the-badge&color=6366f1&labelColor=1e1e2e" alt="GPL-3.0 License" />
    </a>
    <a href="https://github.com/Kh1zZ/Alunara/actions/workflows/ci.yml">
      <img src="https://img.shields.io/github/actions/workflow/status/Kh1zZ/Alunara/ci.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=CI&color=22c55e&labelColor=1e1e2e" alt="CI Status" />
    </a>
    <img src="https://img.shields.io/badge/Android%20TV-9.0%2B%20(API%2028%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=1e1e2e" alt="Android TV 9.0+ (API 28+)" />
  </div>

  <br />

  <div align="center">
    <a href="https://kotlinlang.org" target="_blank">
      <img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/built-with/kotlin/cozy.svg" alt="Built with Kotlin" />
    </a>
  </div>

</div>

<hr />

> [!CAUTION]
> **OFFICIAL RELEASES ONLY — ALUNARA IS NOT ON GOOGLE PLAY**
>
> **Alunara has no official Google Play Store listing.** Official builds are distributed **exclusively** through [GitHub Releases](https://github.com/Kh1zZ/Alunara/releases/latest).
>
> Any third-party websites or Play Store uploads reusing the **Alunara** name or icon are **unofficial, unverified, and unsupported**. Always download signed APKs directly from this repository.

**Alunara** (`com.alunara.app`) isn’t a generic mobile wrapper stretched onto a television screen. It is an independent, audio-only YouTube Music client built from the ground up with **Kotlin** and **Jetpack Compose for TV**, combining **ArchiveTune Core** and **ArchiveTune Lyrics** with a strict low-memory architecture designed specifically for 10-foot D-pad living room control on Android TV 9+ devices — including entry-level **1 GB RAM** smart TVs.

---

> [!IMPORTANT]
> **Geographic Availability:** If YouTube Music is not supported in your region, a VPN or proxy set to a supported region may be required for initial catalog fetching.

---

<a id="philosophy"></a>

## 🌿 Philosophy & Logo Identity

**Alunara** combines the Indonesian words ***"Alunan Suara"*** (*a gentle, continuous flow of sound*), embodying three core values — **Flow**, **Harmony**, and **Tranquility** — under the tagline ***"Let the Music Flow."***

> *"Alunara is not simply about playing music — it is about allowing every melody to find its own space."*

- **Letter "A"** — Rounded geometric construction representing identity, stability, and modern simplicity.
- **Flowing Wave** — Smooth organic sound wave crossing the "A", symbolizing rhythm and continuous melody.
- **Musical Note** — Minimal note woven seamlessly into the wave to express Alunara's musical identity without clutter.
- **Fixed Forest Green Palette** — Calm flat-vector **Dark Forest Green** (tranquility & living-room eye comfort), **Soft Mint Green** (harmony & clarity), and **Fresh Green** (musical energy & creativity), kept consistent across the 1:1 app icon and 16:9 Android TV banner.

---

## 📸 Showcase

<div align="center" id="screenshots">

<table>
  <tr>
    <td width="50%" align="center">
      <!-- Replace with docs/screenshots/01-home.png -->
      <img src="https://placehold.co/1280x720/12121A/A5B4FC/png?text=Alunara+TV+Showcase%0A01+%E2%80%A2+Home+%26+Pure+Icon+Rail+(Coming+Soon)" alt="Home & Pure Icon Rail Placeholder" width="100%" />
      <br />
      <sub><b>Home &amp; Pure Icon Rail</b> — Coming soon (<code>docs/screenshots/01-home.png</code>)</sub>
    </td>
    <td width="50%" align="center">
      <!-- Replace with docs/screenshots/02-now-playing.png -->
      <img src="https://placehold.co/1280x720/12121A/A5B4FC/png?text=Alunara+TV+Showcase%0A02+%E2%80%A2+Now+Playing+%26+Karaoke+Lyrics+(Coming+Soon)" alt="Now Playing & Karaoke Lyrics Placeholder" width="100%" />
      <br />
      <sub><b>Now Playing &amp; Karaoke Lyrics</b> — Coming soon (<code>docs/screenshots/02-now-playing.png</code>)</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <!-- Replace with docs/screenshots/03-library.png -->
      <img src="https://placehold.co/1280x720/12121A/A5B4FC/png?text=Alunara+TV+Showcase%0A03+%E2%80%A2+Library+%26+Playlists+(Coming+Soon)" alt="Library & Playlists Placeholder" width="100%" />
      <br />
      <sub><b>Library &amp; Playlists</b> — Coming soon (<code>docs/screenshots/03-library.png</code>)</sub>
    </td>
    <td width="50%" align="center">
      <!-- Replace with docs/screenshots/04-qr-login.png -->
      <img src="https://placehold.co/1280x720/12121A/A5B4FC/png?text=Alunara+TV+Showcase%0A04+%E2%80%A2+Smart+Phone+QR+Login+Wizard+(Coming+Soon)" alt="Smart Phone QR Login Wizard Placeholder" width="100%" />
      <br />
      <sub><b>Smart Phone QR Login Wizard</b> — Coming soon (<code>docs/screenshots/04-qr-login.png</code>)</sub>
    </td>
  </tr>
</table>

</div>

> [!NOTE]
> The images above are **16:9 showcase placeholders** ready for final Android TV captures under `docs/screenshots/`.

---

## ✨ Features

<div align="center" id="features">

<table>
  <tr>
    <td width="50%" valign="top">
      <div align="left">
        <h3>Playback &amp; Background Audio</h3>
        <ul>
          <li>Audio-only YouTube Music streaming (zero video decode overhead)</li>
          <li>Background playback via Media3 <code>AlunaraPlaybackService</code> &amp; native Android TV Now Playing integration</li>
          <li>Up Next queue management, Shuffle, and Repeat controls</li>
          <li>Persisted queue, track index, and playback position across sessions</li>
          <li>Instant guest mode playback with zero mandatory login</li>
        </ul>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="left">
        <h3>Audio Engine (AAC-First)</h3>
        <ul>
          <li>Strict <b>100% AAC priority</b> across all quality presets (<code>Low</code>, <code>Medium</code>, <code>Auto</code>, <code>High</code>) for hardware DSP decoding on TV SoCs</li>
          <li><b>Opus strictly fallback-only</b> when YouTube serves no AAC stream (<code>High</code> selects the highest bitrate available)</li>
          <li>Memory-tuned Media3 <code>DefaultLoadControl</code> (15s min / 30s max buffer, 2 MB target)</li>
          <li>Single active ExoPlayer lifecycle controller per process</li>
        </ul>
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <div align="left">
        <h3>Lyrics &amp; Romanization</h3>
        <ul>
          <li>Precision time-synced &amp; word-by-word karaoke lyrics (<code>[offset:±ms]</code> &amp; trailing <code>endTimeMs</code>)</li>
          <li>Bounded multi-provider lyrics engine (<code>SimpMusic</code>, <code>LRCLIB</code>, <code>KuGou</code>) with adaptive reliability scoring</li>
          <li>Automatic offline <b>Japanese Romaji (Kuromoji)</b>, Korean Hangul, and Cyrillic romanization</li>
          <li>Jitter-free <code>100 ms</code> active-line sync decoupled from browse UI recomposition</li>
        </ul>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="left">
        <h3>Account &amp; Smart QR Login</h3>
        <ul>
          <li><b>Smart Phone QR Login Wizard</b>: local LAN HTTP server on TV with real-time TV ↔ phone step synchronization — no typing on a remote</li>
          <li>Your YouTube Music playlists, liked songs, library, and history synced after sign-in</li>
          <li>Hardware-backed Android Keystore (<code>AES-256-GCM</code>) encrypted credential storage</li>
          <li>Strict guest vs. signed-in data isolation (<code>accountScope</code>)</li>
        </ul>
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <div align="left">
        <h3>Android TV Interface (60 FPS)</h3>
        <ul>
          <li>Native Jetpack Compose for TV (10-foot UI) engineered for fast D-pad focus routing</li>
          <li>Sleek <b>pure icon-only left navigation rail</b> (<code>72.dp</code>) maximizing content viewport</li>
          <li>Zero-relayout GPU <code>drawBehind</code> mini-player progress bar</li>
          <li>Coil downscaled <code>RGB_565</code> artwork caching &amp; R8 full-mode optimization (~72 MB PSS on 1 GB RAM TV)</li>
        </ul>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="left">
        <h3>Privacy &amp; Lightweight</h3>
        <ul>
          <li>100% Free and Open Source (<code>GNU GPL v3.0</code>)</li>
          <li>Completely ad-free with zero telemetry or tracking SDKs</li>
          <li>Lightweight Room offline cache for instant Home &amp; Library browsing</li>
          <li>Split ABI release builds (<code>armv7a</code> &amp; <code>armv8a</code>) for compact APK downloads</li>
        </ul>
      </div>
    </td>
  </tr>
</table>

</div>

---

## 📥 Download Now

<div align="center" id="download-now">

<h2>Stable Release</h2>

<a href="https://github.com/Kh1zZ/Alunara/releases/latest">
  <img src="https://img.shields.io/badge/Download%20Latest%20APK-GitHub%20Releases-6366f1?style=for-the-badge&logo=github&logoColor=white&labelColor=1e1e2e" alt="Download Alunara on GitHub Releases" />
</a>

<br /><br />

<table>
  <thead>
    <tr>
      <th align="center">APK File</th>
      <th align="center">Architecture</th>
      <th align="center">Recommended Devices</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><code>alunara-&lt;version&gt;.armv7a.apk</code></td>
      <td align="center">32-bit ARM (<code>armeabi-v7a</code>)</td>
      <td align="left">Most 1 GB / 2 GB RAM Android TVs, Mi TV Stick, Changhong &amp; entry-level smart TVs</td>
    </tr>
    <tr>
      <td align="center"><code>alunara-&lt;version&gt;.armv8a.apk</code></td>
      <td align="center">64-bit ARM (<code>arm64-v8a</code>)</td>
      <td align="left">64-bit Android TVs, NVIDIA Shield TV &amp; modern Google TV boxes</td>
    </tr>
  </tbody>
</table>

</div>

<details>
<summary><b>How to Install on Android TV (ADB / USB / Downloader)</b></summary>

1. **Via ADB (Recommended):**
   ```powershell
   adb connect <YOUR_TV_IP>:5555
   adb install -r alunara-0.1.0.armv7a.apk
   ```
   *(Use `alunara-0.1.0.armv8a.apk` if your TV runs a 64-bit `arm64-v8a` ABI.)*
2. **Via USB Drive / Downloader App:**
   - Download the matching APK from [GitHub Releases](https://github.com/Kh1zZ/Alunara/releases/latest), transfer it via USB or Downloader, and allow **Install unknown apps** when prompted.

</details>

> [!WARNING]
> **Official downloads only:** Always verify that your APK comes directly from [Kh1zZ/Alunara Releases](https://github.com/Kh1zZ/Alunara/releases). Third-party mirrors and re-uploads are not maintained or verified by this project.

---

## 🌍 Globalization

Alunara currently ships with built-in UI localization for **English** (`values`) and **Bahasa Indonesia** (`values-in`), alongside offline multi-script lyrics romanization (**Japanese**, **Korean Hangul**, and **Cyrillic**).

Translations and localization improvements are always welcome via Pull Requests!

### ✨ Project Contributors

<a href="https://github.com/Kh1zZ/Alunara/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Kh1zZ/Alunara&columns=6" alt="Project Contributors" />
</a>

<br /><br />

<img src="https://img.shields.io/badge/AI%20Contributor-Gemini%204-6366f1?style=for-the-badge&logo=googlegemini&logoColor=white&labelColor=1e1e2e" alt="AI Contributor: Gemini 4" />

- **Gemini 4** — AI Engineering Contributor (architecture, performance optimization, lyric sync, and documentation).

### 🛠️ Development & Engineering

<a id="development"></a>

Alunara is structured as a modular-lite Gradle Android TV project targeting **Android 9+ (`minSdk 28`, `compileSdk 35`, `targetSdk 35`)** and **JDK 21**:

- **Modules:** `:app`, `:core:domain`, `:core:music`, `:core:lyrics`, `:core:data`, `:third_party:core`, `:third_party:lyrics:{lrclib,simpmusic,kugou}`
- **Verification:** Backed by **86 passing JVM unit tests across 19 test suites**, Android Lint checks, and automated R8 signed split APK builds via [`.github/workflows/ci.yml`](.github/workflows/ci.yml) & [`.github/workflows/release.yml`](.github/workflows/release.yml).

```bash
# Run all module unit tests
./gradlew :core:domain:test :core:music:test :core:lyrics:test :core:data:testDebugUnitTest :app:testDebugUnitTest

# Run Android Lint & assemble release APKs
./gradlew :app:lintDebug :app:assembleRelease
```

*(On Windows, use `.\gradlew.bat` with `JDK 21` and your Android SDK configured in `local.properties`.)*

See [`CHANGELOG.md`](CHANGELOG.md), [`AGENTS.md`](AGENTS.md), [`CREDITS.md`](CREDITS.md), [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md), and [Project Verification Reports](docs/M5_RELEASE_REPORT.md) for engineering details.

---

### Open-Source Acknowledgments

Alunara is made possible by the work of remarkable open-source projects and maintainers:

- **ArchiveTune Core** (`rukamori/core`) & **ArchiveTune Lyrics** (`rukamori/lyrics`) by [rukamori](https://github.com/rukamori) for the InnerTube & lyrics provider engines (see [`CREDITS.md`](CREDITS.md)).
- **Kuromoji** by [Atilika](https://github.com/atilika/kuromoji) for offline Japanese morphological tokenization and romanization.
- **NewPipe Extractor**, **AndroidX Media3**, **Jetpack Compose for TV**, **Coil**, **Ktor**, and **Room**.

---

## ⚖️ Legal Disclaimer

Alunara is an independent third-party client developed for Android TV.
- Not affiliated with, sponsored by, or endorsed by **Google LLC**, **YouTube**, or **ArchiveTune**.
- Does not bypass YouTube's technical protections.
- Users are encouraged to support artists by purchasing music or subscribing via official channels.

## ⚖️ License, Copyright, and Trademark Notice

Alunara is licensed under the **GNU General Public License v3.0 (GPLv3)**.

You may copy, modify, and redistribute the source code, including commercially, provided that you comply with the GPLv3 — preserving copyright and license notices, clearly marking modified versions, and providing corresponding source code when distributing binaries.

See [`LICENSE`](LICENSE) for the full GPLv3 terms and [`CREDITS.md`](CREDITS.md) for vendored dependency attributions.

---

<div align="center">
  <img src="app/src/main/res/drawable-xxhdpi/tv_banner.png" alt="Alunara Android TV Banner" width="440" style="border-radius: 12px" />
  <p><b>If Alunara elevated your Android TV music experience, please consider giving us a ⭐</b></p>
</div>
