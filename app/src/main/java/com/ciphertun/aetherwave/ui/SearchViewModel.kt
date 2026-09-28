package com.ciphertun.aetherwave.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ciphertun.aetherwave.data.MusicRepository
import com.ciphertun.aetherwave.model.PodcastEpisode
import com.ciphertun.aetherwave.model.PodcastShow
import com.ciphertun.aetherwave.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LoadState { IDLE, LOADING, ERROR, DONE }

data class MusicUiState(
    val query: String = "",
    /** Search results — only populated/shown while [query] is non-blank. */
    val tracks: List<Track> = emptyList(),
    /** ISO 3166-1 alpha-3 — see FEATURED_MUSIC_COUNTRIES. Drives [countryTracks]. */
    val musicCountry: String = "ZAF",
    val newReleases: List<Track> = emptyList(),
    val trending: List<Track> = emptyList(),
    val countryTracks: List<Track> = emptyList(),
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
        loadMusicHome()
        loadTrendingPodcasts()
    }

    /** Loads the three home rails (New Releases, Popular, Artists from <country>) in parallel. */
    fun loadMusicHome() {
        _music.value = _music.value.copy(loadState = LoadState.LOADING)
        viewModelScope.launch {
            coroutineScope {
                val newReleasesDeferred = async { repository.newReleases() }
                val trendingDeferred = async { repository.trendingMusic() }
                val countryDeferred = async { repository.artistsInCountry(_music.value.musicCountry) }
                _music.value = _music.value.copy(
                    newReleases = newReleasesDeferred.await(),
                    trending = trendingDeferred.await(),
                    countryTracks = countryDeferred.await(),
                    loadState = LoadState.DONE
                )
            }
        }
    }

    fun searchMusic(query: String) {
        _music.value = _music.value.copy(query = query)
        if (query.isBlank()) {
            // Blank query = back to the sectioned home view; those rails are
            // already cached in state, no need to reload them.
            _music.value = _music.value.copy(loadState = LoadState.DONE)
            return
        }
        _music.value = _music.value.copy(loadState = LoadState.LOADING)
        viewModelScope.launch {
            val results = repository.searchMusic(query)
            _music.value = _music.value.copy(
                tracks = results,
                loadState = if (results.isEmpty()) LoadState.ERROR else LoadState.DONE
            )
        }
    }

    /** [countryCode3] is ISO 3166-1 alpha-3, e.g. "ZAF" — see FEATURED_MUSIC_COUNTRIES. */
    fun setMusicCountry(countryCode3: String) {
        _music.value = _music.value.copy(musicCountry = countryCode3)
        viewModelScope.launch {
            val results = repository.artistsInCountry(countryCode3)
            _music.value = _music.value.copy(countryTracks = results)
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
