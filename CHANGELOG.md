# Changelog

Seluruh perubahan penting pada **Alunara** dicatat di berkas ini secara inkremental sesuai ketentuan `AGENTS.md` (entri versi terdahulu tidak pernah dihapus).

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
