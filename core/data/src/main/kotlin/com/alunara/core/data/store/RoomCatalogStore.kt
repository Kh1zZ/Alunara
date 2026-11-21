package com.alunara.core.data.store

import com.alunara.core.data.db.FavoritesDao
import com.alunara.core.data.db.HistoryDao
import com.alunara.core.data.db.LocalFavoriteEntity
import com.alunara.core.data.db.PlaybackHistoryEntity
import com.alunara.core.domain.contract.CatalogStore
import com.alunara.core.domain.model.AccountScope
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.ResourceBudgetPolicy

/**
 * Persists account-scoped favorites and bounded playback history in Room (PRD §§8.2, 9.1, FR-13, FR-14, FR-22).
 */
class RoomCatalogStore(
    private val favoritesDao: FavoritesDao,
    private val historyDao: HistoryDao,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) : CatalogStore {
    override suspend fun getFavorites(scope: AccountScope): List<Track> =
        favoritesDao.getFavorites(scope.storageKey).map { entity ->
            Track(
                id = entity.trackId,
                title = entity.title,
                artists = parseArtists(entity.artistNames),
                thumbnailUrl = entity.thumbnailUrl,
            )
        }

    override suspend fun isFavorite(
        trackId: String,
        scope: AccountScope,
    ): Boolean = favoritesDao.getFavorites(scope.storageKey).any { it.trackId == trackId }

    override suspend fun toggleFavorite(
        track: Track,
        scope: AccountScope,
    ): Boolean {
        val currentlyFavorite = isFavorite(track.id, scope)
        if (currentlyFavorite) {
            favoritesDao.removeFavorite(trackId = track.id, accountScope = scope.storageKey)
            return false
        }
        favoritesDao.upsertFavorite(
            LocalFavoriteEntity(
                trackId = track.id,
                accountScope = scope.storageKey,
                title = track.title,
                artistNames = track.primaryArtistName,
                thumbnailUrl = track.thumbnailUrl,
                addedAtEpochMs = nowEpochMs(),
            ),
        )
        return true
    }

    override suspend fun recordHistory(
        track: Track,
        scope: AccountScope,
    ) {
        val latest = historyDao.getRecentHistory(scope.storageKey, limit = 1).firstOrNull()
        if (latest?.trackId == track.id) return

        historyDao.insertHistory(
            PlaybackHistoryEntity(
                trackId = track.id,
                title = track.title,
                artistNames = track.primaryArtistName,
                thumbnailUrl = track.thumbnailUrl,
                playedAtEpochMs = nowEpochMs(),
                accountScope = scope.storageKey,
            ),
        )
        historyDao.pruneExcess(
            accountScope = scope.storageKey,
            keepCount = ResourceBudgetPolicy.DEFAULT_HISTORY_LIMIT,
        )
    }

    override suspend fun getRecentHistory(
        scope: AccountScope,
        limit: Int,
    ): List<Track> =
        historyDao
            .getRecentHistory(scope.storageKey, limit)
            .distinctBy { it.trackId }
            .map { entity ->
                Track(
                    id = entity.trackId,
                    title = entity.title,
                    artists = parseArtists(entity.artistNames),
                    thumbnailUrl = entity.thumbnailUrl,
                )
            }

    override suspend fun clearHistory(scope: AccountScope) {
        historyDao.clearAccountScope(scope.storageKey)
    }

    override suspend fun clearAccountData(scope: AccountScope.Account) {
        favoritesDao.clearAccountScope(scope.storageKey)
        historyDao.clearAccountScope(scope.storageKey)
    }

    private fun parseArtists(artistNames: String): List<ArtistRef> =
        artistNames
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ArtistRef(id = null, name = it) }
}
