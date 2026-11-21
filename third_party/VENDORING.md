# Base Engine Vendoring Record

Alunara pins and vendors its mandatory upstream base engines inside `third_party/` so builds remain reproducible from clean checkouts without external submodule state drift, while enabling selective inclusion of audited lyrics provider modules (FG-05).

## Vendored Upstream Repositories

| Local Path | Upstream Repository | Pinned Commit SHA | Commit Date | License | Scope Included |
|---|---|---|---|---|---|
| `third_party/core/` | <https://github.com/rukamori/core> | `83f829007efac2bcd5be1c3e266a01fe617c6f57` | `2026-11-20` | **GPL-3.0** | Full InnerTube core module (`LICENSE`, `README.md`, `build.gradle.kts`, `src/**`; upstream `.gitignore` omitted) |
| `third_party/lyrics/` | <https://github.com/rukamori/lyrics> | `aa6fcd688ae464a56b044989f3bd7632afb8cadf` | `2026-11-11` | **GPL-3.0** | Selective M1 shortlist only (`LICENSE`, `README.md`, `lrclib/**`, `simpmusic/**`, `kugou/**`); the remaining 8 upstream provider modules are excluded |

## Vendoring Policy

1. **Header & License Preservation (GPL-3.0 §§4–5):** Upstream copyright notices, license files (`LICENSE`), and per-file headers (`Do not remove or alter this notice`) MUST NOT be removed or altered.
2. **No Silent Divergence:** Upstream source files inside `third_party/` remain unmodified unless a specific blocker requires a documented, approved patch recorded in this file and `docs/M0_PREFLIGHT_AUDIT.md`.
3. **Isolation Boundary:** Alunara application code accesses `third_party/core` solely through `:core:music` (`ArchiveTuneMusicAdapter`) and `third_party/lyrics` solely through `:core:lyrics` (`ArchiveTuneLyricsAdapter`).
4. **Why Vendoring over Git Submodules:** Selected at the M0 Human Checkpoint so `settings.gradle.kts` can selectively include only the approved lyrics shortlist (`lrclib`, `simpmusic`, `kugou`) and guarantee clean offline/CI builds with a single version catalog (`gradle/libs.versions.toml`).
