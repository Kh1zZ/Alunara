package com.alunara.core.music

import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import moe.rukamori.archivetune.innertube.MusicBackend
import moe.rukamori.archivetune.innertube.NetworkGatekeeperControl
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin isolation adapter around ArchiveTune Core ([MusicBackend]).
 * Unlocks [NetworkGatekeeperControl] lazily on first request and normalizes InnerTube models
 * into Alunara domain models without leaking upstream types.
 */
class ArchiveTuneMusicAdapter internal constructor(
    private val backend: MusicBackend,
    private val allowConnectionsAction: () -> Unit,
) : MusicEngine {
    constructor() : this(
        backend = YouTubeMusicBackend,
        allowConnectionsAction = { NetworkGatekeeperControl.allowConnections() },
    )
    private val gatekeeperUnlocked = AtomicBoolean(false)

    fun isGatekeeperUnlocked(): Boolean = gatekeeperUnlocked.get()

    private fun ensureGatekeeperUnlocked() {
        if (gatekeeperUnlocked.compareAndSet(false, true)) {
            allowConnectionsAction()
        }
    }

    override suspend fun searchGuest(query: String): Result<SearchResults> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return Result.success(SearchResults())
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = false

        return backend.searchSummary(normalizedQuery).map { page ->
            val songs = mutableListOf<Track>()
            val albums = mutableListOf<AlbumRef>()
            val artists = mutableListOf<ArtistRef>()

            page.summaries
                .asSequence()
                .flatMap { it.items.asSequence() }
                .forEach { item ->
                    when (item) {
                        is SongItem -> songs += item.toDomainTrack()
                        is AlbumItem -> albums += item.toDomainAlbum()
                        is ArtistItem -> artists += item.toDomainArtist()
                        else -> Unit
                    }
                }

            SearchResults(
                songs = songs.distinctBy { it.id },
                albums = albums.distinctBy { it.id },
                artists = artists.distinctBy { it.id ?: it.name },
            )
        }
    }

    private fun SongItem.toDomainTrack(): Track =
        Track(
            id = id,
            title = title,
            artists = artists.map { ArtistRef(id = it.id, name = it.name) },
            album = album?.let { AlbumRef(id = it.id, title = it.name) },
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            isExplicit = explicit,
        )

    private fun AlbumItem.toDomainAlbum(): AlbumRef =
        AlbumRef(
            id = browseId,
            playlistId = playlistId,
            title = title,
            artists = artists.orEmpty().map { ArtistRef(id = it.id, name = it.name) },
            year = year,
            thumbnailUrl = thumbnail,
        )

    private fun ArtistItem.toDomainArtist(): ArtistRef =
        ArtistRef(
            id = id,
            name = title,
            thumbnailUrl = thumbnail,
        )
}
