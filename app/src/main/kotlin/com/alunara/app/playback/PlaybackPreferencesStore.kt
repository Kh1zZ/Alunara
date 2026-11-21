package com.alunara.app.playback

import android.content.Context
import android.content.SharedPreferences
import com.alunara.core.domain.policy.AudioQualityPreset

interface PlaybackPreferences {
    fun getAudioQuality(): AudioQualityPreset

    fun setAudioQuality(preset: AudioQualityPreset)

    fun getLyricsSyncOffsetMs(trackId: String): Long

    fun setLyricsSyncOffsetMs(
        trackId: String,
        offsetMs: Long,
    )
}

class InMemoryPlaybackPreferences(
    initialQuality: AudioQualityPreset = AudioQualityPreset.AUTO,
) : PlaybackPreferences {
    private var audioQuality: AudioQualityPreset = initialQuality
    private val offsets = LinkedHashMap<String, Long>()

    override fun getAudioQuality(): AudioQualityPreset = audioQuality

    override fun setAudioQuality(preset: AudioQualityPreset) {
        audioQuality = preset
    }

    override fun getLyricsSyncOffsetMs(trackId: String): Long = offsets[trackId] ?: 0L

    override fun setLyricsSyncOffsetMs(
        trackId: String,
        offsetMs: Long,
    ) {
        val clamped = offsetMs.coerceIn(MIN_LYRICS_OFFSET_MS, MAX_LYRICS_OFFSET_MS)
        if (clamped == 0L) {
            offsets.remove(trackId)
        } else {
            offsets[trackId] = clamped
        }
    }

    companion object {
        const val MIN_LYRICS_OFFSET_MS: Long = -10_000L
        const val MAX_LYRICS_OFFSET_MS: Long = 10_000L
    }
}

class SharedPreferencesPlaybackPreferencesStore(
    context: Context,
) : PlaybackPreferences {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun getAudioQuality(): AudioQualityPreset {
        val saved = prefs.getString(KEY_AUDIO_QUALITY, null) ?: return AudioQualityPreset.AUTO
        return AudioQualityPreset.entries.firstOrNull { it.name == saved } ?: AudioQualityPreset.AUTO
    }

    @Synchronized
    override fun setAudioQuality(preset: AudioQualityPreset) {
        prefs.edit().putString(KEY_AUDIO_QUALITY, preset.name).apply()
    }

    @Synchronized
    override fun getLyricsSyncOffsetMs(trackId: String): Long {
        if (trackId.isBlank()) return 0L
        return prefs
            .getLong(offsetKey(trackId), 0L)
            .coerceIn(InMemoryPlaybackPreferences.MIN_LYRICS_OFFSET_MS, InMemoryPlaybackPreferences.MAX_LYRICS_OFFSET_MS)
    }

    @Synchronized
    override fun setLyricsSyncOffsetMs(
        trackId: String,
        offsetMs: Long,
    ) {
        if (trackId.isBlank()) return
        val clamped = offsetMs.coerceIn(InMemoryPlaybackPreferences.MIN_LYRICS_OFFSET_MS, InMemoryPlaybackPreferences.MAX_LYRICS_OFFSET_MS)
        val keys =
            prefs
                .getString(KEY_LYRIC_OFFSET_INDEX, "")
                .orEmpty()
                .split('\n')
                .filter { it.isNotEmpty() }
                .toMutableList()

        val editor = prefs.edit()
        keys.remove(trackId)
        if (clamped == 0L) {
            editor.remove(offsetKey(trackId))
        } else {
            keys.add(trackId)
            while (keys.size > MAX_STORED_LYRIC_OFFSETS) {
                val oldest = keys.removeAt(0)
                editor.remove(offsetKey(oldest))
            }
            editor.putLong(offsetKey(trackId), clamped)
        }
        editor.putString(KEY_LYRIC_OFFSET_INDEX, keys.joinToString("\n")).apply()
    }

    private fun offsetKey(trackId: String): String = "$PREFIX_LYRIC_OFFSET$trackId"

    companion object {
        private const val PREFS_NAME = "alunara_playback_prefs"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_LYRIC_OFFSET_INDEX = "lyric_offset_keys"
        private const val PREFIX_LYRIC_OFFSET = "lyric_offset_"
        private const val MAX_STORED_LYRIC_OFFSETS = 200
    }
}
