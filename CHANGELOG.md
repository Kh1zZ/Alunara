# Changelog

Seluruh perubahan penting pada **Alunara** dicatat di berkas ini secara inkremental sesuai ketentuan `AGENTS.md` (entri versi terdahulu tidak pernah dihapus).

## [0.2.2] - 2026-11-24

### Fixed
- **D-Pad Uninitialized `FocusRequester` Crashes**:
  - Replaced unsafe `focusProperties { up = ... }` on `DockedMiniPlayer` controls (`TvComponents.kt`, `MainActivity.kt`) with safe `onPreviewKeyEvent` handlers routing to `browsePaneFocusRequester.safeRequestFocus()`, eliminating the `IllegalStateException: FocusRequester is not initialized` crash when pressing **Up** from the mini player after scrolling down in Settings or Home.
  - Replaced unsafe `focusProperties { down = ... }` on `TvHeaderSearchBar` and `focusProperties { right = ... }` on left-rail items (`MainActivity.kt`) with safe `onPreviewKeyEvent` / `safeRequestFocus()` routing, and migrated pane containers to Compose's safe `.focusRestorer()` API.
  - Fixed `LibraryScreen` tab header `up` focus target when switching away from Playlists tab, wrapped the active tab body in a persistent `focusGroup()` + `.focusRestorer()`, and replaced off-screen `firstContentItemRequester` `down` properties with safe `onPreviewKeyEvent` routing.
  - Updated `MainActivity` `BackHandler` for the companion sign-in overlay to cleanly stop incomplete companion server sessions on remote Back press.

## [0.2.1] - 2026-11-24

### Added
- **Full YouTube Music Account Sync**:
  - Playlist management directly from the TV UI: create account playlists, add tracks from Now Playing / Search / Detail views via `AddToPlaylistDialog`, and remove tracks from owned playlists (`removeTrackFromAccountPlaylist` with `setVideoId` fallback resolution).
  - Save and unsave external playlists/albums (`setRemotePlaylistSaved`), subscribe and unsubscribe artists (`setRemoteArtistSubscription` with channel ID lookup), and report authenticated playback to YouTube Music history via `videostatsPlaybackUrl` (`reportPlaybackToAccount`).
  - Real-time `SyncNoticePill` feedback banner + automatic optimistic UI rollback on failed like, save, or subscribe mutations.
- **GitHub Pages QR Pairing Portal (Option A) & Phone Completion Modal**:
  - Added static pairing portal (`docs/login/index.html`) hosted at `https://kh1zz.github.io/Alunara/login` so users can type a compact 6/7/12-character Base32 **TV Pairing Code** (or scan the QR) instead of manually typing a raw local IP/port URL.
  - Added fixed preferred companion server port `4580` with `GET /p?k=` short-link redirect and a celebratory **"Sign-In Complete"** popup modal (`#doneModal`) on the phone companion page triggered immediately once the TV confirms authentication (kept alive for a 25-second grace period after TV login completes).

### Changed
- **TV Focus Stability & Navigation Flow**:
  - Replaced bouncy spring focus scaling with crisp `tween(120ms)` transitions, tuned `LocalBringIntoViewSpec` scroll easing (`165ms`), and added a `90ms` debounce on left-rail tab switching to eliminate D-pad focus jitter.
  - Fixed D-pad **Right** navigation from the left sidebar so focus deterministically enters the active screen's primary content (`entryFocusRequester`) instead of jumping into the header search bar or bottom mini player.
- **Library Cover Geometry**: Updated `TvMediaCard` / `TvArtwork` (`fillWidthSquare`) and `LibraryScreen` (`GridCells.Adaptive(AlunaraDimens.CardWidth)` with outer focus-scale padding) so square playlist covers render cleanly without clipping.
- **Redesigned Sign-In Screen**: Expanded the QR + TV Pairing Code card into a 66% hero panel and compacted the live WebView into a 34% secondary preview pane.
- **Repository Hygiene**: Expanded `.gitignore` to ignore `.kotlin/`, `**/.kotlin/`, `dist/`, `*.hprof`, editor swap/workspace files, and local AI assistant state directories.

## [0.2.0] - 2026-11-24

### Lyrics & Romaji

- Normalize artificial ASCII whitespace inserted between Japanese and Chinese characters (`Han`, `Hiragana`, `Katakana`, and CJK punctuation) in `LrcParser` word-synced and line-synced parsing while preserving Hangul word spacing and embedded Latin words in mixed-script lines.
- Remove Mandarin Pinyin ICU fallback on confirmed Japanese lyric segments in `LyricsRomanizer` so unknown Kanji stay identifiable rather than emitting Pinyin, and track covered word slices during single-pass word-sync romanization to eliminate double spaces around CJK quotes and compound words.
- Group opening brackets and CJK quotes (`「`, `『`, `(`, `[`) with their following token in `ActiveWordSyncedLyricText` and skip empty absorbed slices cleanly without disrupting karaoke word timing indices.
- Upgrade `CachedLyricsRepository` synced cache format tag to `LRC_V2` without a Room schema migration so stale pre-`0.1.8` cached LRC rows refresh cleanly online while retaining parsed offline fallback.

### Documentation

- Refresh `README.md` for `v0.2.0` to cover Calm Vocal Sweep & Center Glide lyrics, CJK-normalized offline romanization, in-app GitHub Releases auto-update, trilingual TV localization (English, Bahasa Indonesia, Japanese), and the updated local-debug / GitHub Actions release verification workflow.

## [0.1.8] - 2026-11-24

### Lyrics & Localization

- Add a calm **Vocal Sweep & Center Glide** animation suite for synced lyrics: fix center-anchored scroll distance tracking with `animateScrollBy`, add a minimalist left accent bar on the active row, replace bouncy per-word jumps with continuous left-to-right GPU vocal sweep fill (`clipRect` in `drawWithContent`) on the singing token, and display a gentle three-dot instrumental intro cue on long track intros.
- Capture leading Enhanced LRC words before the first inline `<mm:ss.xx>` timestamp, protect Japanese/CJK/Hangul word-synced tokens from space injection in `LrcParser.repairWordBoundaries`, avoid redundant reference fetches for clean word-synced tracks, fix KuGou's tail credit stripping index and lazy HTTP initialization, and preserve coroutine cancellation in `SimpMusicLyrics`.
- Contextualize Japanese and Korean word-level romanization in a single pass per line (eliminating compound Kanji/liaison mismatches and ~85% of Kuromoji tokenization work) and switch Kuromoji's tokenizer cache to `WeakReference` for 1 GB RAM TVs.

### Playback, Memory & TV UI

- Stop previous audio immediately on track switches, guard `playerListener` and position syncing with the active prepared track ID to eliminate ghost audio and `STATE_ENDED` races during slow stream resolution, and reuse `ProgressiveMediaSource.Factory` across tracks sharing the same User-Agent.
- Reset `AppContainer`'s playback service tracking flag in `AlunaraPlaybackService.onDestroy()`, trim Coil's bitmap memory cache on `TRIM_MEMORY_RUNNING_LOW`/`CRITICAL`, optimize `TvBackdropBlurTransformation` to 240px and row thumbnails to 160px, and quantize `SeekableProgressBar` accessibility updates to 1 Hz.
- Restrict `TvIconButton` `AnimatedContent` transitions strictly to dynamic icons (Play/Pause and Favorite), and scale `CatalogDetailScreen` typography with `AlunaraTypography` accessibility preferences.

## [0.1.7] - 2026-11-24

### TV Interface

- Keep the displayed screen and Back navigation in sync, consume Now Playing notification requests once, and prevent older searches from replacing newer results.
- Restore focus on return to content and after dismissing confirmations. Show retry instructions when Android cannot open the update installer or its permission settings.
- Adapt Now Playing controls to compact displays, expose seek actions to accessibility services, and keep the first remote input after idle from moving the playhead.
- Reduce karaoke text allocations and background lyric work, improve high-contrast lyrics, and allow zoom in the phone sign-in page.

## [0.1.6] - 2026-11-23

### App Updates

- Add an in-app GitHub Releases updater (`Kh1zZ/Alunara/releases/latest`, with automatic web redirect fallback if anonymous API calls are rate-limited) featuring SemVer comparison and automatic split APK selection (`alunara-<ver>.armv8a.apk` vs `.armv7a.apk`) from `Build.SUPPORTED_ABIS`.
- Add startup update detection (prompting first with the new version badge and formatted release notes when Auto-Update is enabled) plus an **App Updates** card in Settings with installed version display, **Auto-Update** toggle, and manual **Check for Updates** trigger.
- Implement direct in-app APK streaming downloads to cache with live progress tracking and immediate system PackageInstaller handoff via `FileProvider` + `REQUEST_INSTALL_PACKAGES` (with seamless `ON_RESUME` auto-continuation after granting unknown-app installs).
- Localize all update UI, progress labels, and permission prompts across English, Bahasa Indonesia, and Japanese with parity unit tests.

## [0.1.5] - 2026-11-23

### Lyrics, Localization & UI Polish

- Anchor synced lyrics vertically at the exact viewport center (`contentPadding` equal to half the viewport height and item-center scroll offsets) so even opening lines at track start and closing lines remain centered, with refined multi-tier distance fade.
- Add subtle spring bounce physics (`scale` and upward lift via GPU `graphicsLayer`) to both active line-sync rows and active word-sync tokens (using safe syllable-grouped `FlowRow` tokens on the active line, while preserving fast single-text rendering when `Reduce Motion` is enabled).
- Rework in-app language selection into a scalable, focus-locked spotlight picker triggered from Settings: pressing OK locks D-pad focus onto the centered selector, D-pad Left/Right smoothly cycles languages with horizontal transitions and debounced InnerTube refresh, and OK/Back returns focus cleanly.
- Add Japanese (`日本語`, `values-ja`, `hl=ja`) as the third supported language alongside Bahasa Indonesia and English, with explicit English secondary fallback (`LocaleList(target, ENGLISH)`), complete localized QR login companion support, Indonesian terminology/register polish, and expanded parity/placeholder unit tests.
- Add lightweight GPU focus micro-scale, Play/Pause icon morphing, artwork playback breathing scale, Now Playing tab crossfade, browse pane fade transition, and slide/fade animations for `DockedMiniPlayer` (all automatically bypassed when `Reduce Motion` is active).

## [0.1.4] - 2026-11-23

### Playback & Performance

- Fix an infinite self-triggering `AlunaraPlaybackService` foreground start loop (`onStartCommand` calling `onUpdateNotification(session, true)` and overriding `startInForegroundRequired || keepForegroundAlive`), which repeatedly dispatched `startForegroundService()` (~90 starts/sec), congested the Main Looper (`VsyncDelay` 55–178 ms / ~10–15 FPS), and flooded system Binder transactions (`FAILED BINDER TRANSACTION`) during playback.
- Create the playback notification channel once in `AlunaraPlaybackService.onCreate()`, remove the redundant `startForeground()` call from `onCreate()`, guard bootstrap `ensureForegroundStarted()` with `isBootstrapForegroundStarted` when no active track is attached, and preserve Media3's native `startInForegroundRequired` signal in `onUpdateNotification()`.

## [0.1.3] - 2026-11-23

### Playback & UI Performance

- Bake Now Playing horizontal and vertical dark gradients directly into the 360px `TvBackdropBlurTransformation` bitmap and remove `.allowHardware(false)` plus redundant full-screen gradient overlay boxes to eliminate 4.5x overdraw.
- Isolate `ActiveWordSyncedLyricText`, `SyncedLyricsViewport`, `ElapsedTimeLabel`, and `IdleDimmableControl` recomposition scopes; pre-cache per-word `AnnotatedString` states; and add `CompositingStrategy.ModulateAlpha` to lyric rows to prevent offscreen FBO `saveLayerAlpha` allocations and per-tick text relayouts.
- Enable touch scrolling (`userScrollEnabled = true`) in Now Playing lyrics panels while preserving TV D-pad focus safety.
- Remove explicit `System.gc()` calls, switch Kuromoji tokenizer caching to `SoftReference`, add a fast single-pass basic-Latin check in `LyricsRomanizer`, move `isSyllableFragmented(doc)` out of the per-line loop in `LrcParser`, and preserve word `endTimeMs` tags in `CachedLyricsRepository`.
- Batch queue additions via `QueueAction.EnqueueAll`, deduplicate redundant `PlaybackSessionUiState` emissions across track start and `STATE_READY`/`onIsPlayingChanged`, debounce repeated `startService` IPC calls in `AppContainer`, cache `Player.Commands` in `AlunaraPlaybackService`, and stabilize root media-key handling in `MainActivity`.

## [0.1.2] - 2026-11-23

### Playback

- Rank AAC/MP4A and Opus streams by direct URL availability and preset bitrate, with no codec preference.
- Evict rejected stream URLs from the playback and resolver caches before retrying.
- Load lyrics when the lyrics panel is visible and defer next-track stream prefetch until playback has stabilized.

## [0.1.1] - 2026-11-23

### Fitur Baru & Peningkatan Lokalisasi
- **Pengalih Bahasa Langsung di Pengaturan Alunara (Bahasa Indonesia & English):**
  - Menambahkan kartu pilihan **Bahasa / Language** langsung di layar **Pengaturan** Alunara dengan 2 tombol D-pad instan (**Bahasa Indonesia** dan **English**), sepenuhnya independen dari pengaturan bahasa sistem Android TV dan siap dikembangkan untuk bahasa tambahan di masa mendatang.
  - Menerapkan pergantian bahasa **tanpa restart Activity (`zero-restart`)** melalui pembaruan reaktif `LocalConfiguration` & `LocalContext` sehingga musik terus diputar mulus tanpa jeda audio, tanpa kedipan hitam, dan fokus D-pad tetap terjaga.
  - Menyimpan preferensi bahasa di `AlunaraLanguageStore` (`SharedPreferences`) dan menerapkannya sejak *cold start* melalui `MainActivity.attachBaseContext()` sambil tetap mempertahankan kode wilayah (`gl`) perangkat.
- **Sinkronisasi Bahasa Katalog YouTube Music (`hl`) & QR Login Companion:**
  - Menyinkronkan parameter bahasa konten InnerTube (`hl` = `id` / `en`) lewat `MusicEngine.setContentLanguage()` pada `Dispatchers.IO` serta menyegarkan judul seksi Beranda otomatis saat bahasa diubah.
  - Melokalisasi penuh seluruh pesan umpan balik WebView login dan halaman web **Smart Phone QR Login Wizard** (`LocalLoginCompanionServer`) agar mengikuti pilihan bahasa aktif Alunara (Bahasa Indonesia / English), serta menjaga konteks Activity murni untuk konstruksi `WebView`.
- **Cakupan Pengujian Otomatis Lokalisasi:** Menambahkan suite unit test `AppLanguageTest`, `StringResourcesParityTest` (jaminan paritas 100% key `values` ↔ `values-in`), uji `setContentLanguage` pada `ArchiveTuneMusicAdapterTest`, serta uji lokalisasi English/Indonesian pada `LocalLoginCompanionServerTest`.

## [0.1.0] - 2026-11-23

### Fitur Utama & Rilis Resmi Perdana
- **Prioritas Penuh Codec Audio AAC (Opus Fallback Only):** Seluruh preset kualitas audio (`Rendah`, `Sedang`, `Otomatis`, `Tinggi`) kini memprioritaskan stream **AAC** yang didukung akselerasi hardware DSP Android TV untuk menghilangkan lonjakan beban decode CPU; **Opus** hanya dipakai 100% sebagai cadangan apabila YouTube tidak menyediakan format AAC untuk lagu tertentu (preset `Tinggi` tetap memilih bitrate tertinggi yang tersedia).
- **Zero-Lag Playback Navigation & Buffer Tuning:**
  - Memisahkan status indeks lirik aktif ke `activeLyricIndexFlow` dan menurunkan frekuensi ticker ke `1 Hz` tanpa update `uiState` selama pengguna menjelajahi Beranda, Koleksi, Pencarian, atau Pengaturan saat musik diputar.
  - Mengoptimalkan `MiniPlayerProgressStrip` dengan gambar langsung ke canvas GPU (`drawBehind`) serta meniadakan rekomposisi root `NowPlayingScreen` saat menekan tombol D-pad remote.
  - Menyetel buffer `DefaultLoadControl` audio-only Android TV (`minBufferMs = 15s`, `maxBufferMs = 30s`, target `2 MB`).
- **Sinkronisasi & Animasi Lirik Presisi:**
  - Menambahkan dukungan tag LRC global `[offset:±ms]`, penyimpanan timestamp akhir kata akurat (`endTimeMs`) untuk kata karaoke bernada panjang/pendek, serta penghitungan baris lirik awal langsung dari posisi player aktual.
  - Mempertahankan `FontWeight` konstan saat perpindahan baris aktif agar bebas re-wrap CPU (`StaticLayout`), mengisolasi state jauh (`distanceTier`), memperhalus interpolasi karaoke 8-langkah, dan mempercepat lompatan posisi > 3 baris.
- **Sidebar Kiri Pure Icon-Only:** Menghapus teks label pada rail navigasi kiri menjadi ikon terpusat murni berlebar tetap `72.dp` agar area konten layar TV lebih luas tanpa layout shift atau blink hitam.
- **Rilis Split Resmi (`armv7a` & `armv8a`):** Resmi keluar dari fase beta menuju rilis GitHub produksi (`alunara-0.1.0.armv7a.apk` & `alunara-0.1.0.armv8a.apk`) dengan build R8 full mode, signing terpusat v3, romanisasi Kuromoji/Hangul/Cyrillic, dan Smart QR Login Wizard real-time.
