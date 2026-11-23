# Changelog

Seluruh perubahan penting pada **Alunara** dicatat di berkas ini secara inkremental sesuai ketentuan `AGENTS.md` (entri versi terdahulu tidak pernah dihapus).

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
