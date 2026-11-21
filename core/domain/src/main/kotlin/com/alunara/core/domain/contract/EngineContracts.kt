package com.alunara.core.domain.contract

import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track

interface MusicEngine {
    suspend fun searchGuest(query: String): Result<SearchResults>

    suspend fun fetchGuestHome(): Result<List<HomeSection>>

    suspend fun resolveAudioStream(trackId: String): Result<AudioStreamSource>
}

interface LyricsEngine {
    suspend fun fetchLyrics(
        track: Track,
        preferredProvider: LyricsProviderId? = null,
    ): LyricsDocument
}

interface QueueStore {
    suspend fun loadQueue(): QueueState

    suspend fun saveQueue(state: QueueState)
}

interface PlaybackLifecycleController {
    fun onAppBackgroundedOrStopped()
}
