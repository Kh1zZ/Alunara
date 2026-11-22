package com.alunara.core.domain.model

enum class LyricsProviderId {
    LRCLIB,
    SIMPMUSIC,
    KUGOU,
}

data class SyncedLyricWord(
    val startTimeMs: Long,
    val text: String,
    val romanizedText: String? = null,
)

data class SyncedLyricLine(
    val startTimeMs: Long,
    val text: String,
    val romanizedText: String? = null,
    val words: List<SyncedLyricWord> = emptyList(),
) {
    val hasWordSync: Boolean
        get() = words.isNotEmpty()
}

sealed interface LyricsDocument {
    data class Synced(
        val provider: LyricsProviderId,
        val lines: List<SyncedLyricLine>,
    ) : LyricsDocument {
        val isWordSynced: Boolean
            get() = lines.any { it.hasWordSync }
    }

    data class Plain(
        val provider: LyricsProviderId,
        val text: String,
        val romanizedText: String? = null,
    ) : LyricsDocument

    data class Unavailable(
        val reason: String,
    ) : LyricsDocument
}
