package com.alunara.app.ui.screens

import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class LatestSearchTest {
    @Test
    fun completedOlderRequestCannotReplaceTheLatestResults() = runTest {
        val requests = mutableMapOf<String, Continuation<Result<SearchResults>>>()
        val search = LatestSearch(this) { query ->
            suspendCoroutine { requests[query] = it }
        }
        search.submit("first")
        runCurrent()
        search.submit("second")
        runCurrent()

        val second = SearchResults(songs = listOf(Track(id = "2", title = "Second")))
        requests.getValue("second").resume(Result.success(second))
        runCurrent()
        requests.getValue("first").resume(Result.failure(IllegalStateException("Old error")))
        runCurrent()

        assertEquals(SearchUiState(query = "second", results = second), search.state)
        assertFalse(search.state.loading)
    }
}
