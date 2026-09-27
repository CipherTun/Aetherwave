package com.ciphertun.aetherwave.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ciphertun.aetherwave.data.MusicRepository
import com.ciphertun.aetherwave.model.PodcastEpisode
import com.ciphertun.aetherwave.model.PodcastShow
import com.ciphertun.aetherwave.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LoadState { IDLE, LOADING, ERROR, DONE }

data class MusicUiState(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val loadState: LoadState = LoadState.IDLE
)

data class PodcastUiState(
    val query: String = "",
    val country: String = "ZA",
    val shows: List<PodcastShow> = emptyList(),
    val selectedShow: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val loadState: LoadState = LoadState.IDLE
)

class SearchViewModel(
    private val repository: MusicRepository = MusicRepository()
) : ViewModel() {

    private val _music = MutableStateFlow(MusicUiState())
    val music: StateFlow<MusicUiState> = _music.asStateFlow()

    private val _podcasts = MutableStateFlow(PodcastUiState())
    val podcasts: StateFlow<PodcastUiState> = _podcasts.asStateFlow()

    init {
        loadTrendingMusic()
        loadTrendingPodcasts()
    }

    fun loadTrendingMusic() {
        _music.value = _music.value.copy(loadState = LoadState.LOADING)
        viewModelScope.launch {
            val results = repository.trendingMusic()
            _music.value = _music.value.copy(tracks = results, loadState = LoadState.DONE)
        }
    }

    fun searchMusic(query: String) {
        _music.value = _music.value.copy(query = query, loadState = LoadState.LOADING)
        if (query.isBlank()) {
            loadTrendingMusic()
            return
        }
        viewModelScope.launch {
            val results = repository.searchMusic(query)
            _music.value = _music.value.copy(
                tracks = results,
                loadState = if (results.isEmpty()) LoadState.ERROR else LoadState.DONE
            )
        }
    }

    private fun loadTrendingPodcasts() {
        _podcasts.value = _podcasts.value.copy(loadState = LoadState.LOADING)
        viewModelScope.launch {
            val results = repository.trendingPodcasts(country = _podcasts.value.country)
            _podcasts.value = _podcasts.value.copy(shows = results, loadState = LoadState.DONE)
        }
    }

    fun searchPodcasts(query: String) {
        val country = _podcasts.value.country
        _podcasts.value = _podcasts.value.copy(query = query, loadState = LoadState.LOADING, selectedShow = null)
        if (query.isBlank()) {
            loadTrendingPodcasts()
            return
        }
        viewModelScope.launch {
            val results = repository.searchPodcasts(query, country = country)
            _podcasts.value = _podcasts.value.copy(
                shows = results,
                loadState = if (results.isEmpty()) LoadState.ERROR else LoadState.DONE
            )
        }
    }

    /** Switches the active storefront and re-runs whatever's currently on screen. */
    fun setCountry(country: String) {
        _podcasts.value = _podcasts.value.copy(country = country, selectedShow = null)
        if (_podcasts.value.query.isBlank()) loadTrendingPodcasts() else searchPodcasts(_podcasts.value.query)
    }

    fun openPodcast(show: PodcastShow) {
        _podcasts.value = _podcasts.value.copy(selectedShow = show, loadState = LoadState.LOADING)
        viewModelScope.launch {
            val episodes = repository.episodesFor(show)
            _podcasts.value = _podcasts.value.copy(episodes = episodes, loadState = LoadState.DONE)
        }
    }

    fun closePodcast() {
        _podcasts.value = _podcasts.value.copy(selectedShow = null, episodes = emptyList())
    }
}
