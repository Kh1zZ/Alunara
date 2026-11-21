# Credits & Third-Party Notices

Alunara is an independent YouTube Music client developed for Android TV (`com.alunara.app`). Its application layer and Android TV frontend are independently authored and are **not** officially affiliated with, sponsored by, or endorsed by ArchiveTune or `rukamori`.

> Alunara is an independent YouTube Music client developed for Android TV. Its music integration is built on [ArchiveTune Core](https://github.com/rukamori/core) and its lyrics integration is built on [ArchiveTune Lyrics](https://github.com/rukamori/lyrics), open-source projects by [rukamori](https://github.com/rukamori). Alunara's application layer and Android TV frontend are independently developed. Alunara is not officially affiliated with or endorsed by ArchiveTune.

---

## Mandatory Base Engines

| Component | Repository (Vendored Path) | Author | Audited Pinned Commit (Date) | License | Role in Alunara |
|---|---|---|---|---|---|
| **ArchiveTune Core** | <https://github.com/rukamori/core> (`third_party/core/`) | [rukamori](https://github.com/rukamori) | `83f829007efac2bcd5be1c3e266a01fe617c6f57` (`2026-11-20`) | **GPL-3.0** | InnerTube YouTube Music client, request signing, response & page models, playback auth state |
| **ArchiveTune Lyrics** | <https://github.com/rukamori/lyrics> (`third_party/lyrics/`) | [rukamori](https://github.com/rukamori) | `aa6fcd688ae464a56b044989f3bd7632afb8cadf` (`2026-11-11`) | **GPL-3.0** | Modular multi-provider lyrics clients (selective M1 subset: `lrclib`, `simpmusic`, `kugou`) |

---

## Lyrics Provider & Third-Party Obligations

Selective activation policy applies (`rukamori/lyrics` exposes 11 provider modules; Alunara activates only audited, compliant modules). Status as of Milestone M1:

| Module / Source | Audited Obligation / Finding | Status at M1 |
|---|---|---|
| `lrclib`, `simpmusic`, `kugou` | Approved initial M1 shortlist (`third_party/lyrics/{lrclib,simpmusic,kugou}`) behind bounded max-2 concurrency adapter | **Enabled in M1** (subject to ongoing FG-05 checks) |
| `lrcred` (`lrc.red`) | Requires visible attribution link back to <https://lrc.red> wherever lyrics data is displayed and configuring `LrcRed.setUserAgent(appName, versionName)` | Excluded from vendored build / deferred |
| `unison` (`unison.betterlyrics.org`) | API responses expose `submitterName` (`submitter.displayName`) for submitter attribution | Excluded from vendored build / deferred |
| `betterlyrics`, `paxsenix`, `musixmatch`, `binimum`, `megalobiz`, `youlyplus` | Individual terms of service, rate limits, proxy dependencies, and copyright notices must be verified before enabling | Excluded from vendored build / deferred |

---

## Transitive Dependencies & GPL-3.0 Compliance

1. **Copyright & Header Preservation (GPL-3.0 §§4–5):** Original copyright headers and license notices inside `rukamori/core` and `rukamori/lyrics` MUST NOT be removed or altered.
2. **Combined Work & Source Distribution:** Distributing an APK incorporating GPL-3.0 base engines constitutes distribution of a combined work under GPL-3.0; complete corresponding source code, build instructions, `LICENSE`, and open-source notices must be made available to recipients.
3. **In-App Notices:** Distributed builds of Alunara must provide an accessible in-app screen/section displaying `CREDITS.md`, `LICENSE` (GPL-3.0), and transitive dependency notices.
4. **Transitive Inventory (M1 Deliverable):** Full automated dependency license scanning (including `ktor`, `okhttp`, `newpipe-extractor` / Rhino, `re2j`, `brotli`, AndroidX/Media3, Room, and Compose TV) will be generated once Gradle modules are initialized in **Milestone M1**.
