# Alunara Agent Governance & Release Rules (`AGENTS.md`)

Seluruh agent yang bekerja pada repositori **Alunara** WAJIB mematuhi seluruh klausul di bawah ini tanpa pengecualian.

---

## 1. Aturan Engineering & Alur Kerja

1. **Versioning SemVer Inkremental (`+0.0.1`):**
   - Versi dasar resmi saat ini dimulai dari **`0.1.0`**.
   - Setiap ada perubahan fitur, perbaikan, atau optimisasi yang relatif banyak, naikkan versi patch secara inkremental sebesar **`+0.0.1`** (`0.1.0` -> `0.1.1` -> `0.1.2`, dst.).
   - Nilai `versionCode` dihitung deterministik dengan rumus `(MAJOR * 10000) + (MINOR * 100) + PATCH` (contoh: `0.1.0` = `100`, `0.1.1` = `101`).
2. **Context-Mode & RTK Selalu Aktif:**
   - Selalu gunakan **Context-Mode (`ctx_*` tools)** untuk pembacaan konfigurasi/dokumen, analisis/grep data besar, dan eksekusi skrip agar konteks tetap hemat token.
   - Selalu gunakan prefix **`rtk`** pada perintah terminal harian (`rtk git status`, `rtk git diff`, `rtk git log`, dll.).
3. **Graphify First:**
   - Selalu manfaatkan indeks knowledge graph (**`graphify-out/graph.json`** via `query_graph`, `shortest_path`, `get_neighbors`, `god_nodes`) untuk memahami arsitektur, alur data, serta mencari simbol/dependensi sebelum membaca file mentah.
4. **Alur Wajib `Planning -> Review -> Eksekusi`:**
   - **Semua** usulan perubahan WAJIB melewati fase **Planning -> Review Dev -> Eksekusi**.
   - Tidak boleh ada modifikasi kode/konfigurasi sekecil apa pun yang dieksekusi tanpa persetujuan eksplisit Dev (`proceed`).
5. **`CHANGELOG.md` Bersifat Inkremental (Append-Only):**
   - File [`CHANGELOG.md`](./CHANGELOG.md) mencatat seluruh perubahan setiap kenaikan versi secara kronologis (versi terbaru di atas).
   - **Dilarang keras menghapus atau memangkas entri/baris versi sebelumnya** — seluruh sejarah versi mulai dari `0.1.0` dan seterusnya wajib terekam utuh.
6. **Instalasi & Uji Perangkat TV Hanya Atas Permintaan:**
   - Pemasangan APK ke Android TV fisik maupun pengujian ADB live **hanya dilakukan jika Dev memerintahkannya secara eksplisit**. Secara default, cukup selesaikan build dan rangkaian pengujian otomatis (unit test, lint, split APK release verification).

---

## 2. Sistem Rilis GitHub (Dev-Driven)

Proses rilis sepenuhnya dikendalikan oleh instruksi Dev dan dijalankan secara otomatis oleh agent:

1. **`bump`:** Jika Dev memberi instruksi `bump`, agent menaikkan versi sesuai ketentuan Dev (`app/build.gradle.kts` + entri versi baru inkremental di `CHANGELOG.md`).
2. **`proceed`:** Kata kunci persetujuan Dev bahwa Implementation Plan telah disetujui dan siap dieksekusi.
3. **Eksekusi Rilis ke GitHub Releases:**
   - Setelah `proceed` dan `bump` disetujui/dijalankan, agent:
     1. Memastikan seluruh unit test, Android Lint, dan split release APK build lokal lulus (`exit 0`),
     2. Membuat commit bersih,
     3. Membuat Git tag `v<versi>` (contoh: `v0.1.0`),
     4. Menjalankan push ke `origin/main` serta push tag `v<versi>` untuk memicu workflow [`.github/workflows/release.yml`](./.github/workflows/release.yml),
     5. Memastikan rilis langsung masuk ke halaman **GitHub Releases** resmi (**bukan** Pre-Release / `prerelease: false`).
4. **Format & Arsitektur APK Rilis:**
   - Build R8 release menghasilkan **2 APK split** yang ditandatangani (32-bit `armeabi-v7a` dan 64-bit `arm64-v8a`), **tanpa** file checksum `SHA256`, dengan penamaan persis:
     - `alunara-<versi>.armv7a.apk`
     - `alunara-<versi>.armv8a.apk`
   - Kedua file APK tersebut langsung diunggah ke halaman GitHub Release untuk versi terkait.
5. **Deskripsi Rilis Otomatis dari `CHANGELOG.md`:**
   - Deskripsi halaman GitHub Release wajib diambil **hanya** dari bagian versi yang relevan (`## [<versi>]`) di `CHANGELOG.md` agar catatan rilis akurat dan ringkas sesuai APK yang dipublikasikan.
