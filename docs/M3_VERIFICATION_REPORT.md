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
