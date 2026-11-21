package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.SyncedLyricLine
import com.alunara.core.domain.model.SyncedLyricWord

/**
 * Resolves active lyric line and word indices strictly from current playback position (PRD §7.2).
 */
object LyricsSyncResolver {
    fun findActiveLineIndex(
        lines: List<SyncedLyricLine>,
        positionMs: Long,
    ): Int = findLastAtOrBefore(lines.size, positionMs) { lines[it].startTimeMs }

    fun findActiveWordIndex(
        words: List<SyncedLyricWord>,
        positionMs: Long,
    ): Int = findLastAtOrBefore(words.size, positionMs) { words[it].startTimeMs }

    private inline fun findLastAtOrBefore(
        size: Int,
        positionMs: Long,
        timestampAt: (Int) -> Long,
    ): Int {
        if (size == 0 || positionMs < timestampAt(0)) {
            return -1
        }
        var low = 0
        var high = size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (timestampAt(mid) <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
