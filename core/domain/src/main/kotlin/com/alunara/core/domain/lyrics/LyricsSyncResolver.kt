package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.SyncedLyricLine

/**
 * Resolves the active lyric line index strictly from current playback position (PRD §7.2).
 */
object LyricsSyncResolver {
    fun findActiveLineIndex(
        lines: List<SyncedLyricLine>,
        positionMs: Long,
    ): Int {
        if (lines.isEmpty() || positionMs < lines.first().startTimeMs) {
            return -1
        }
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startTimeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
