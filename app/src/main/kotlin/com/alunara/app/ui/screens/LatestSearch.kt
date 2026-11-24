package com.alunara.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alunara.core.domain.model.SearchResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class SearchUiState(
    val query: String = "",
    val results: SearchResults? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

internal class LatestSearch(
    private val scope: CoroutineScope,
    private val search: suspend (String) -> Result<SearchResults>,
) {
    var state by mutableStateOf(SearchUiState())
        private set

    private var request: Job? = null

    fun submit(input: String) {
        val query = input.trim()
        if (query.isEmpty()) return
        request?.cancel()
        state = SearchUiState(query = query, loading = true)
        request =
            scope.launch {
                val result = search(query)
                ensureActive()
                state =
                    result.fold(
                        onSuccess = { SearchUiState(query = query, results = it) },
                        onFailure = { SearchUiState(query = query, error = it.message ?: "Search failed") },
                    )
            }
    }
}
