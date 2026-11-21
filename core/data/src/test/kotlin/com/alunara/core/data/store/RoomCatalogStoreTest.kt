package com.alunara.core.data.store

import com.alunara.core.data.db.FavoritesDao
import com.alunara.core.data.db.HistoryDao
import com.alunara.core.data.db.LocalFavoriteEntity
import com.alunara.core.data.db.PlaybackHistoryEntity
import com.alunara.core.domain.model.AccountScope
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.ResourceBudgetPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCatalogStoreTest {
    private class FakeFavoritesDao : FavoritesDao {
        val entries = mutableListOf<LocalFavoriteEntity>()

        override suspend fun upsertFavorite(entry: LocalFavoriteEntity) {
            entries.removeIf { it.trackId == entry.trackId && it.accountScope == entry.accountScope }
            entries.add(entry)
        }

        override suspend fun removeFavorite(
            trackId: String,
            accountScope: String,
        ) {
            entries.removeIf { it.trackId == trackId && it.accountScope == accountScope }
        }

        override suspend fun getFavorites(accountScope: String): List<LocalFavoriteEntity> =
            entries
                .filter { it.accountScope == accountScope }
                .sortedByDescending { it.addedAtEpochMs }

        override suspend fun clearAccountScope(accountScope: String) {
            entries.removeIf { it.accountScope == accountScope }
        }
    }

    private class FakeHistoryDao : HistoryDao {
        private var nextId = 1L
        val entries = mutableListOf<PlaybackHistoryEntity>()

        override suspend fun insertHistory(entry: PlaybackHistoryEntity) {
            entries.add(entry.copy(id = nextId++))
        }

        override suspend fun getRecentHistory(
            accountScope: String,
            limit: Int,
        ): List<PlaybackHistoryEntity> =
            entries
                .filter { it.accountScope == accountScope }
                .sortedByDescending { it.playedAtEpochMs }
                .take(limit)

        override suspend fun pruneExcess(
            accountScope: String,
            keepCount: Int,
        ) {
            val keepIds =
                entries
                    .filter { it.accountScope == accountScope }
                    .sortedByDescending { it.playedAtEpochMs }
                    .take(keepCount)
                    .map { it.id }
                    .toSet()
            entries.removeIf { it.accountScope == accountScope && it.id !in keepIds }
        }

        override suspend fun clearAccountScope(accountScope: String) {
            entries.removeIf { it.accountScope == accountScope }
        }
    }

    @Test
    fun toggleFavoriteAndClearAccountData_isolatesGuestFromAccountScope() =
        runBlocking {
            var now = 1_000L
            val store = RoomCatalogStore(FakeFavoritesDao(), FakeHistoryDao(), nowEpochMs = { now++ })
            val track1 = Track(id = "t1", title = "Teh Hijau", artists = listOf(ArtistRef(null, "Tulus")))
            val track2 = Track(id = "t2", title = "Iqro", artists = listOf(ArtistRef(null, "Raim Laode")))

            assertTrue(store.toggleFavorite(track1, AccountScope.Guest))
            assertTrue(store.isFavorite("t1", AccountScope.Guest))
            assertFalse(store.isFavorite("t1", AccountScope.Account("user1")))

            assertTrue(store.toggleFavorite(track2, AccountScope.Account("user1")))
            assertEquals(1, store.getFavorites(AccountScope.Guest).size)
            assertEquals(1, store.getFavorites(AccountScope.Account("user1")).size)

            // Toggling again removes favorite
            assertFalse(store.toggleFavorite(track1, AccountScope.Guest))
            assertTrue(store.getFavorites(AccountScope.Guest).isEmpty())

            // Re-add guest favorite & history, then clear account scope: guest must remain intact
            assertTrue(store.toggleFavorite(track1, AccountScope.Guest))
            store.recordHistory(track1, AccountScope.Guest)
            store.recordHistory(track2, AccountScope.Account("user1"))

            store.clearAccountData(AccountScope.Account("user1"))

            assertEquals(1, store.getFavorites(AccountScope.Guest).size)
            assertEquals(1, store.getRecentHistory(AccountScope.Guest).size)
            assertTrue(store.getFavorites(AccountScope.Account("user1")).isEmpty())
            assertTrue(store.getRecentHistory(AccountScope.Account("user1")).isEmpty())
        }

    @Test
    fun recordHistory_deduplicatesConsecutivePlaysAndPrunesAtBudgetLimit() =
        runBlocking {
            var now = 10_000L
            val historyDao = FakeHistoryDao()
            val store = RoomCatalogStore(FakeFavoritesDao(), historyDao, nowEpochMs = { now += 10L; now })
            val sameTrack = Track(id = "same", title = "Same Track")

            store.recordHistory(sameTrack, AccountScope.Guest)
            store.recordHistory(sameTrack, AccountScope.Guest)
            assertEquals(1, historyDao.entries.size)

            for (i in 1..(ResourceBudgetPolicy.DEFAULT_HISTORY_LIMIT + 15)) {
                store.recordHistory(Track(id = "trk-$i", title = "Track $i"), AccountScope.Guest)
            }

            assertEquals(ResourceBudgetPolicy.DEFAULT_HISTORY_LIMIT, historyDao.entries.size)
            val recent = store.getRecentHistory(AccountScope.Guest, limit = 5)
            assertEquals("trk-${ResourceBudgetPolicy.DEFAULT_HISTORY_LIMIT + 15}", recent.first().id)

            store.clearHistory(AccountScope.Guest)
            assertTrue(store.getRecentHistory(AccountScope.Guest).isEmpty())
        }
}
