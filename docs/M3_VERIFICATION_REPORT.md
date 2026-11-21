# Milestone M3 Verification Report — Account & Catalog (`0.1.0-alpha`)

**Status:** Milestone M3 Verified on Physical Android TV (Pending M3 Human Review)  
**Target Package:** `com.alunara.app` (`versionName = "0.1.0-alpha"`, Debug APK)  
**Audio Disk Cache (`FG-03`):** Disabled (`ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED = false`, `0` bytes)  
**Release Classification (`PRD §10.1 / §15.1`):** Internal Alpha (`0.1.0-alpha`) — `KI-02` / `FG-04` remains documented as a Beta blocker until a TV-compliant InnerTube login mechanism exists without Google password collection or insecure manual cookie copy-paste.

---

## 1. Delivered M3 Scope

| Capability | PRD / Spec Target | Implemented Artifacts |
|---|---|---|
| **Verified Account Session & Android Keystore Isolation** | `FR-11`, `FR-22`, §§8.2, 10.1–10.2, `FG-04` | `AccountCredentials`, `AccountProfile`, `AuthSessionState` (`Guest` / `Authenticated` / `Expired`) in `:core:domain`; `ArchiveTuneMusicAdapter.verifyAccountSession` & `clearAccountSession` in `:core:music`; `KeystoreAccountSessionStore` (`AndroidKeystoreAesGcmCipher`, AES-256-GCM in `no_backup/alunara_session.enc`, zero plaintext secrets in Room/logs) in `:core:data`; `AUTH_INVALID` session expiry downgrade & `logoutAccount()` data wipe in `PlaybackSessionManager`; Settings Account/FG-04 status & confirmed Logout UI |
| **Unified Catalog Browsing (Playlists, Albums, Artists)** | `FR-12`, UI/UX Spec §§4.2, 4.4 | `SearchResults.playlists` + `fetchAlbumDetail`, `fetchPlaylistDetail`, `fetchArtistDetail` in `ArchiveTuneMusicAdapter`; `CatalogDetailScreen.kt` (Play All, Start Radio, `+ Queue All`, vertical track list, Artist -> Album/Playlist drill-down); D-pad `catalogStack` & `BackHandler` in `MainActivity.kt`; clickable Album/Playlist/Artist cards across `HomeScreen`, `SearchScreen`, and `LibraryScreen` |
| **Radio & Bounded Autoplay Tail Recommendations** | `FR-15`, `FR-16`, UI/UX Spec §5.2 | `fetchRadioTracks` (`RDAMVM<trackId>` + watch fallback) in `ArchiveTuneMusicAdapter`; `startRadio()` & deduplicated single-shot tail extension (`lastAutoplayExtendedTrackId` guard against infinite request loops) in `PlaybackSessionManager`; **Favorite (`♥`)** & **Start Radio** controls in `NowPlayingScreen.kt` |
| **Account-Scoped Favorites, History, Library Tabs & Home Personal Rails** | `FR-13`, `FR-14`, UI/UX Spec §§4.1, 4.3 | `CatalogStore` + `RoomCatalogStore` (uses existing v1 Room `local_favorites` & `playback_history` tables pruned at `ResourceBudgetPolicy.DEFAULT_HISTORY_LIMIT = 200`, isolated by `AccountScope.storageKey`); automatic playback history recording; 4-tab `LibraryScreen.kt` (`Queue`, `Favorites`, `History`, `Account Playlists` with honest `FG-04` Guest Mode lock); Home `Recently Played` & `Your Favorites` horizontal rails; deprecated `PlaylistAdd` replaced by `Icons.AutoMirrored.Rounded.PlaylistAdd` |

---

## 2. Gradle Build, Lint & Unit Test Gate (`52 / 52 Passed`)

Executed under JDK 21 (`C:\Program Files\Java\jdk-21.0.12.1`):

```powershell
.\gradlew.bat --console=plain :core:domain:test :core:music:test :core:lyrics:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

| Verification Item | Result | Count / Output |
|---|---|---|
| **`:core:domain:test`** | **PASS** | `22 / 22` passed across `6` suites (`0` failed, `0` skipped) |
| **`:core:music:test`** | **PASS** | `11 / 11` passed in `1` suite (`ArchiveTuneMusicAdapterTest`, covering M3 catalog detail, playlist search, radio, & session verification) |
| **`:core:lyrics:test`** | **PASS** | `6 / 6` passed in `1` suite (`0` failed, `0` skipped) |
| **`:core:data:testDebugUnitTest`** | **PASS** | `8 / 8` passed across `4` suites (`RoomCatalogStoreTest`, `KeystoreAccountSessionStoreTest`, `CachedLyricsRepositoryTest`, `CacheBudgetManagerTest`) |
| **`:app:testDebugUnitTest`** | **PASS** | `5 / 5` passed across `3` suites (`PlaybackSessionManagerTest`, `PlaybackErrorClassifierTest`, `AlunaraThemeTokensTest`) |
| **Grand Unit Test Total** | **PASS** | **`52 / 52` passed** (`15` suites, `0` failed, `0` errors, `0` skipped) |
| **`:app:lintDebug`** | **PASS** | `0` errors (`PlaylistAdd` deprecation eliminated) |
| **`:app:assembleDebug`** | **PASS** | `app-debug.apk` = `78,465,229` bytes (`74.83 MiB`) |

---

## 3. Physical Changhong `AI PONT` TV Verification (`192.168.100.124:5555`)

| Check | Measured M3 Result |
|---|---|
| **Clean Install & Cold Start** | `pm clear com.alunara.app` + `am start -W`: `LaunchState: COLD`, `TotalTime: 9905 ms`, `WaitTime: 9936 ms` |
| **AudioFlinger Active Session** | Active playback (`Teh Hijau` -> natural autoplay transition to `Cinta Sudah Lewat`): `com.alunara.app`, `Standby: no`, `Sample rate: 48000 Hz`, `3 Tracks of which 1 are active`, `0` underruns |
| **Playback Memory (`dumpsys meminfo`)** | `TOTAL PSS: 104,402 KB` (`Java Heap: 11,136 KB`, `Native Heap: 11,360 KB`, `Code: 45,000 KB`, `Stack: 1,640 KB`, `Graphics: 13,760 KB`, `TOTAL RSS: 149,560 KB`, `TOTAL SWAP PSS: 3,075 KB`) |
| **Now Playing Favorite & Radio** | Toggled **Favorite (`♥`)** ON (`Icons.Rounded.Favorite` mint fill) alongside **Start Radio** transport control during live `SimpMusic` word-sync playback |
| **Search & Album Detail (`CatalogDetailScreen`)** | Searched `"tulus"`, opened `Monokrom` album detail displaying `Tulus · 2016`, `Kembali`, `Putar Semua`, `Mulai Radio`, `+ Semua ke Antrean`, and full track list (`Manusia Kuat`, `Pamit`, `Ruang Sendiri`, `Tukar Jiwa`) |
| **4-Tab Library (`Koleksi & Antrean`)** | Verified live tabs: `Antrean (19)`, `Favorit (1)` (`Teh Hijau`), `Riwayat (2)` (`Cinta Sudah Lewat` & `Teh Hijau`), and `Playlist Akun` (honest `FG-04` Guest Mode card + `Buka Cari` / `Status Akun` shortcuts) |
| **Settings Account & Session Card** | Verified `Putar otomatis & rekomendasi radio di akhir antrean` (`AKTIF`), `Akun & Isolasi Sesi (FG-04)` (`Android Keystore AES-256-GCM · Tanpa rahasia plaintext di Room`), and GPL-3.0 open-source credits |
| **Stop-on-Exit & Logcat Health** | `KEYCODE_HOME` + `3s` -> `dumpsys media.audio_flinger`: `2 Tracks of which 0 are active`; `logcat`: `0` crashes, `0` ANRs, `0` `AlunaraPlayback` / `SQLiteLog` errors |

---

## 4. M3.1 WebView YouTube Music Sign-In Verification

Implemented Option 1 (`YouTubeWebViewLoginScreen.kt`) using the device's system Chromium WebView (`com.google.android.webview` v`150.0.7871.46` observed via `dumpsys webviewupdate`), capturing `SAPISID` / `__Secure-3PAPISID` / `__Secure-1PAPISID` cookies plus `VISITOR_DATA` / `DATASYNC_ID` when reaching `https://music.youtube.com`, verifying via `ArchiveTuneMusicAdapter.verifyAccountSession()`, encrypting credentials in `KeystoreAccountSessionStore` (AES-256-GCM), and immediately destroying the `WebView` instance on exit (`logoutAccount()` also flushes/clears `CookieManager`).

| Check | Measured M3.1 Outcome |
|---|---|
| **Gradle Unit Tests & Lint** | **`52 / 52` passed** across `15` suites (`0` failed, `0` skipped); `:app:lintDebug` passed (`0` errors, `0` Kotlin deprecation warnings); `app-debug.apk` = `78,493,501` bytes (`74.86 MiB`) |
| **Changhong TV WebView Sign-In Load** | Tapping **`Masuk Akun YouTube Music (WebView)`** (`SettingsScreen` / `LibraryScreen`) loaded Google's official sign-in page (`accounts.google.com`, *"Login · Lanjutkan ke YouTube Music"*) with D-pad/IME input ready and **zero `403 disallowed_useragent`** |
| **WebView Memory & Stability (`192.168.100.124:5555`)** | Active Chromium WebView `dumpsys meminfo`: `TOTAL PSS: 109,289 KB` (`Java Heap: 7,172 KB`, `Native Heap: 7,484 KB`, `Code: 25,048 KB`, `Stack: 972 KB`, `Graphics: 16,444 KB`, `TOTAL RSS: 167,372 KB`, `TOTAL SWAP PSS: 16,493 KB`); `logcat` (`AlunaraPlayback`, `AndroidRuntime`, `chromium`): `0` crashes, `0` ANRs, `0` errors |

---

## 5. M3.2 High-Contrast 3-State TV Focus & WebView Input Assist Fix

Resolved two real-device usability findings from Changhong TV testing:

| Check | Measured M3.2 Outcome |
|---|---|
| **Gradle Unit Tests & Lint** | **`52 / 52` passed** across `15` suites (`0` failed, `0` skipped); `:app:lintDebug` passed (`0` errors, `0` warnings); `app-debug.apk` = `78,516,461` bytes (`74.88 MiB`) |
| **3-State Focus Contrast (`AlunaraPalette` / `TvComponents` / `RailItem`)** | Replaced ambiguous solid mint fill on unfocused `highlighted = true` buttons/tabs/rail items with clear 3-state separation: **Normal Unfocused** (`#1D2B22` surface, `#E3EEE5` text, `#26382D` border), **Selected/Primary Unfocused** (`#26382D` dark emerald, `#85BDA0` mint text/icon, `55%` mint outline), and **D-Pad Focused** (`#C8F0DA` bright mint-white fill, `#0B120E` bold dark text/icon, `2.5.dp` `#FFFFFF` white focus ring). Verified visually on Changhong Settings (`AKTIF` unfocused vs `Masuk Akun YouTube Music (WebView)` focused) and Rail (`Beranda` focused vs `Pengaturan` selected) |
| **WebView TV Remote Input Assist Bar (`YouTubeWebViewLoginScreen`)** | Added native Compose `BasicTextField` input assist bar (`imeAction = Send`, auto-focused on open for immediate Gboard TV popup), **`Isi & Lanjut`** (`Fill & Next`) JS injector (`HTMLInputElement.prototype.value` setter + `input`/`change` events + Google Next click), and **`◀ Elemen` / `Elemen ▶` / `Klik`** DOM navigator highlighting active web elements with `3px solid #FFD54F`. Verified live on Changhong TV: typed `"alunara.test@example.com"`, submitted into Google's `"Email atau nomor telepon"` input box via `Isi & Lanjut`, and observed server validation response (`Tidak dapat menemukan akun ini`) with `0` crashes/ANRs/errors |

---

## 6. M3.3 Personalized Home Fix, QR Companion Login, 3-Tab Library & NowPlaying Radio Queue Verification

| Check | Measured M3.3 Outcome |
|---|---|
| **Gradle Unit Tests & Lint** | **`60 / 60` passed** across `16` suites (`0` failed, `0` skipped — including new `QrCodeMatrixTest`, `ArchiveTuneMusicAdapterTest` personalized mixes/charts/queueChips/likes/history tests, and `PlaybackSessionManagerTest` auto-radio & remote sync tests); `:app:lintDebug` passed (`0` errors, `0` M3.3 lint matches); `app-debug.apk` = `78,623,277` bytes (`74.98 MiB`) |
| **Personalized Home Fix (`MainActivity` / `ArchiveTuneMusicAdapter` / `HomeScreen`)** | Fixed frozen Guest Home cache by triggering `loadHome(force = true)` whenever `authSession.scope.storageKey` transitions (recovering startup Keystore restore + live WebView login/logout), updating `ArchiveTuneMusicAdapter.fetchGuestHome()` to map `PlaylistItem` mixes, fetch 1 continuation shelf when `< 6` sections, and append `getChartsPage()` sections, plus `YouTubeWebViewLoginScreen` multi-attempt `ytcfg` capture. Verified live on Changhong TV: displayed **`Sinkron YouTube` / `Beranda · Akhis`**, user mixes (`J-Pop · 178 lagu` in `Dengarkan lagi`), `Mix lagu`, and `Hits Internasional` |
| **Option 1: QR Companion Login (`QrCodeMatrix` / `LocalLoginCompanionServer` / `YouTubeWebViewLoginScreen`)** | Replaced clunky WebView Assist Bar with minimal `← Batal` top bar, direct D-pad DOM stepper (`Up`/`Down`/`Center`) on left WebView, and ephemeral LAN HTTP form + pure-Kotlin ISO 18004 QR Code card on right (`http://<lan-ip>:<port>/l?t=<token>`) with in-memory credential injection for Email, Password, 2FA/Text, and Click Next |
| **3-Tab Account-Synced Library (`Playlist > Favorit > Riwayat`)** | Reordered `LibraryScreen` to exact 3 tabs removing Queue: **`Playlist (24)`** (synced 4-column YouTube playlist grid: `J-Pop`, `2016-2018 edm vibes`, `Gacor`, `Indo Old`, `2025 RECAP`), **`Favorit (100)`** (`LM` synced + instant `likeVideo`), and **`Riwayat (201)`** (`FEmusic_history` + local history) |
| **NowPlaying Queue & YouTube Algorithm (`NowPlayingScreen` / `PlaybackSessionManager`)** | Added `Lirik <-> Antrean` panel toggle and transport button in `NowPlayingScreen`, automatic single-track YouTube `RDAMVM` radio expansion, and `QueueFilterChip` row; verified live `Diputar` playback (`8 Letters` & `Pretty Please`), single-row `SimpMusic · Sinkron kata` header, 50-item playlist queue, AND live `RDAMVM` `Antrean (25)` radio recommendations with YouTube `QueueFilterChip`s (`Semua`, `All`, `Korean dance`, `Familiar`, `Popular`) (`TOTAL PSS: 127,228 KB`, `logcat`: `0` crashes/errors) |


