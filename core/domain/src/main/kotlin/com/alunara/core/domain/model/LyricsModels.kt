package com.alunara.core.domain.model

enum class LyricsProviderId {
    LRCLIB,
    SIMPMUSIC,
    KUGOU,
}

data class SyncedLyricLine(
    val startTimeMs: Long,
    val text: String,
    val romanizedText: String? = null,
)

sealed interface LyricsDocument {
    data class Synced(
        val provider: LyricsProviderId,
        val lines: List<SyncedLyricLine>,
    ) : LyricsDocument

    data class Plain(
        val provider: LyricsProviderId,
        val text: String,
    ) : LyricsDocument

    data class Unavailable(
        val reason: String,
    ) : LyricsDocument
}
