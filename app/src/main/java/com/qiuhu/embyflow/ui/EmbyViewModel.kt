package com.qiuhu.embyflow.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiuhu.embyflow.BuildConfig
import com.qiuhu.embyflow.data.emby.EmbyBootstrapResult
import com.qiuhu.embyflow.data.emby.EmbyPlaybackSessionState
import com.qiuhu.embyflow.data.emby.EmbyPlaybackSource
import com.qiuhu.embyflow.data.emby.EmbyRepository
import com.qiuhu.embyflow.data.emby.EmbySession
import com.qiuhu.embyflow.data.update.AppUpdateRepository
import com.qiuhu.embyflow.data.update.AppUpdateState
import com.qiuhu.embyflow.data.resume.ContinueWatchingEntry
import com.qiuhu.embyflow.data.resume.ContinueWatchingStore
import com.qiuhu.embyflow.data.resume.toMediaItem
import com.qiuhu.embyflow.data.search.SearchHistoryStore
import com.qiuhu.embyflow.data.server.ServerProfilesStore
import com.qiuhu.embyflow.data.settings.AppSettings
import com.qiuhu.embyflow.data.settings.AppSettingsStore
import com.qiuhu.embyflow.data.settings.LibraryFilterSpec
import com.qiuhu.embyflow.data.settings.libraryFilterFor
import com.qiuhu.embyflow.data.settings.isActive
import com.qiuhu.embyflow.data.settings.normalizeLibrarySortMode
import com.qiuhu.embyflow.model.EmbyHomePayload
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.MediaPerson
import com.qiuhu.embyflow.model.MediaTag
import com.qiuhu.embyflow.model.SampleCatalog
import com.qiuhu.embyflow.model.ServerProfile
import com.qiuhu.embyflow.model.ServerProfilesState
import com.qiuhu.embyflow.model.isEpisode
import com.qiuhu.embyflow.model.isSeason
import com.qiuhu.embyflow.model.isSeries
import com.qiuhu.embyflow.model.normalized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EmbyUiState {
    data object Loading : EmbyUiState

    data class Ready(
        val payload: EmbyHomePayload,
        val isRefreshingLibrary: Boolean = false,
        val isAppendingLibrary: Boolean = false,
    ) : EmbyUiState

    data class Error(
        val title: String,
        val detail: String,
        val fallback: EmbyHomePayload = SampleCatalog.fallbackPayload,
    ) : EmbyUiState
}

sealed interface PlaybackUiState {
    data object Idle : PlaybackUiState

    data class Loading(
        val media: MediaItem,
    ) : PlaybackUiState

    data class Ready(
        val media: MediaItem,
        val source: EmbyPlaybackSource,
        val initialPositionMs: Long = 0L,
    ) : PlaybackUiState

    data class Error(
        val message: String,
    ) : PlaybackUiState
}

private data class PlaybackSourceJob(
    val trigger: String,
    val deferred: Deferred<Result<EmbyPlaybackSource>>,
)

data class TagBrowseState(
    val activeTag: MediaTag? = null,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class ActorBrowseState(
    val activeActor: MediaPerson? = null,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class SearchState(
    val query: String = "",
    val results: List<MediaItem> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class SeriesDetailState(
    val seasons: List<MediaItem> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<MediaItem> = emptyList(),
    val nextUpEpisode: MediaItem? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private const val PlaybackWarmupDebugTag = "AurePPlaybackWarmup"
private const val PlaybackLoadingGateDelayMs = 180L
private const val LocalResumePersistThrottleMs = 4_000L
private const val LocalResumePersistMinPositionDeltaMs = 1_000L

class EmbyViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private var repository: EmbyRepository? = null
    private val appUpdateRepository = AppUpdateRepository()
    private val settingsStore = AppSettingsStore(application.applicationContext)
    private val continueWatchingStore = ContinueWatchingStore(application.applicationContext)
    private val searchHistoryStore = SearchHistoryStore(application.applicationContext)
    private val serverProfilesStore = ServerProfilesStore(application.applicationContext)
    private var session: EmbySession? = null
    private var serverPayload: EmbyHomePayload? = null
    private var activeServerProfileId: String? = null
    private var cachedResumeEntries: List<ContinueWatchingEntry> = emptyList()
    private var localResumeItems: List<MediaItem> = emptyList()
    private val detailLoadingIds = mutableSetOf<String>()
    private val playbackSourceCache = mutableMapOf<String, EmbyPlaybackSource>()
    private val playbackSourceJobs = mutableMapOf<String, PlaybackSourceJob>()
    private var playbackRequestGeneration: Long = 0L
    private var searchJob: Job? = null
    private var resumeLogoEnrichmentJob: Job? = null
    private var searchGeneration: Long = 0L
    private val resumeLogoAttemptedIds = mutableSetOf<String>()
    private var lastLocalResumePersistKey: String? = null
    private var lastLocalResumePersistAtMs: Long = 0L
    private var lastLocalResumePersistPositionMs: Long = 0L

    private val _uiState = MutableStateFlow<EmbyUiState>(EmbyUiState.Loading)
    val uiState: StateFlow<EmbyUiState> = _uiState.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Idle)
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _availableGenres = MutableStateFlow<List<String>>(emptyList())
    val availableGenres: StateFlow<List<String>> = _availableGenres.asStateFlow()

    private val _tagBrowseState = MutableStateFlow(TagBrowseState())
    val tagBrowseState: StateFlow<TagBrowseState> = _tagBrowseState.asStateFlow()

    private val _actorBrowseState = MutableStateFlow(ActorBrowseState())
    val actorBrowseState: StateFlow<ActorBrowseState> = _actorBrowseState.asStateFlow()

    private val _mediaDetails = MutableStateFlow<Map<String, MediaItem>>(emptyMap())
    val mediaDetails: StateFlow<Map<String, MediaItem>> = _mediaDetails.asStateFlow()

    private val _seriesDetails = MutableStateFlow<Map<String, SeriesDetailState>>(emptyMap())
    val seriesDetails: StateFlow<Map<String, SeriesDetailState>> = _seriesDetails.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _serverProfilesState = MutableStateFlow(ServerProfilesState())
    val serverProfilesState: StateFlow<ServerProfilesState> = _serverProfilesState.asStateFlow()

    private val _appUpdateState = MutableStateFlow(
        AppUpdateState(
            currentVersion = BuildConfig.VERSION_NAME,
        ),
    )
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()

    private val _sleepTimerEndMs = MutableStateFlow<Long?>(null)
    val sleepTimerEndMs: StateFlow<Long?> = _sleepTimerEndMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    init {
        observeSettings()
        observeContinueWatching()
        observeSearchHistory()
        observeServerProfiles()
        refreshAppUpdateStatus()
        bootstrap()
    }

    fun refresh() {
        bootstrap()
    }

    fun openPlayer(media: MediaItem) {
        val activeSession = session ?: return
        val repository = repository ?: return
        val requestGeneration = ++playbackRequestGeneration
        val resolvedMedia = enrichPlaybackBrandingFromCache(resolveMediaWithResume(media))
        val cachedDirectSource = resolvedMedia
            .takeIf(::canWarmPlaybackSource)
            ?.let { playbackSourceCache[it.id] }
        if (cachedDirectSource != null) {
            _playbackState.value = PlaybackUiState.Ready(
                media = resolvedMedia,
                source = cachedDirectSource,
                initialPositionMs = resolvedMedia.resumePositionMs.coerceAtLeast(0L),
            )
            refreshPlaybackBrandingIfNeeded(
                media = resolvedMedia,
                requestGeneration = requestGeneration,
            )
            return
        }
        viewModelScope.launch {
            val loadingGate = launch {
                delay(PlaybackLoadingGateDelayMs)
                if (requestGeneration == playbackRequestGeneration) {
                    _playbackState.value = PlaybackUiState.Loading(media)
                }
            }
            val startMs = SystemClock.elapsedRealtime()
            runCatching {
                val playableMedia = repository.resolvePlayableItem(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    media = resolvedMedia,
                )
                val resolvedPlayableMedia = enrichPlaybackBrandingFromCache(
                    resolveMediaWithResume(playableMedia),
                )
                val initialPositionMs = resolvedPlayableMedia.resumePositionMs.coerceAtLeast(0L)
                val source = awaitPlaybackSource(
                    activeSession = activeSession,
                    repository = repository,
                    media = resolvedPlayableMedia,
                    fallbackTitle = resolvedPlayableMedia.title.ifBlank { resolvedMedia.title },
                    trigger = "open-player",
                )
                Triple(resolvedPlayableMedia, source, initialPositionMs)
            }.onSuccess { (playableMedia, source, initialPositionMs) ->
                loadingGate.cancel()
                if (requestGeneration != playbackRequestGeneration) {
                    return@onSuccess
                }
                Log.i(
                    PlaybackWarmupDebugTag,
                    "open ready itemId=${playableMedia.id} title=${playableMedia.title} elapsedMs=${SystemClock.elapsedRealtime() - startMs}",
                )
                _playbackState.value = PlaybackUiState.Ready(
                    media = playableMedia,
                    source = source,
                    initialPositionMs = initialPositionMs,
                )
                refreshPlaybackBrandingIfNeeded(
                    media = playableMedia,
                    requestGeneration = requestGeneration,
                )
            }.onFailure { throwable ->
                loadingGate.cancel()
                if (requestGeneration != playbackRequestGeneration) {
                    return@onFailure
                }
                Log.w(
                    PlaybackWarmupDebugTag,
                    "open failed itemId=${resolvedMedia.id} title=${resolvedMedia.title} elapsedMs=${SystemClock.elapsedRealtime() - startMs}",
                    throwable,
                )
                _playbackState.value = PlaybackUiState.Error(
                    throwable.message ?: "无法创建播放地址",
                )
            }
        }
    }

    fun closePlayer(
        positionMs: Long = 0L,
        durationMs: Long = 0L,
    ) {
        playbackRequestGeneration += 1L
        val activeMedia = when (val state = _playbackState.value) {
            is PlaybackUiState.Loading -> state.media
            is PlaybackUiState.Ready -> state.media
            is PlaybackUiState.Error,
            PlaybackUiState.Idle,
            -> null
        }?.let(::resolveMediaWithResume)

        if (activeMedia != null) {
            val profileId = activeServerProfileId
            val userId = session?.userId
            if (!profileId.isNullOrBlank() && !userId.isNullOrBlank()) {
                persistContinueWatchingSnapshot(
                    media = activeMedia,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    serverProfileId = profileId,
                    serverUserId = userId,
                )
            }
        }

        _playbackState.value = PlaybackUiState.Idle
    }

    fun reportPlaybackStarted(state: EmbyPlaybackSessionState) {
        val activeSession = session ?: return
        val repository = repository ?: return
        viewModelScope.launch {
            runCatching {
                repository.reportPlaybackStarted(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    state = state,
                )
            }.onFailure { throwable ->
                Log.w("AurePPlaybackSession", "report start failed itemId=${state.itemId}", throwable)
            }
        }
    }

    fun reportPlaybackProgress(
        state: EmbyPlaybackSessionState,
        eventName: String,
    ) {
        val activeSession = session ?: return
        val repository = repository ?: return
        persistLocalPlaybackProgressIfNeeded(
            state = state,
            eventName = eventName,
        )
        viewModelScope.launch {
            runCatching {
                repository.reportPlaybackProgress(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    state = state,
                    eventName = eventName,
                )
            }.onFailure { throwable ->
                Log.w(
                    "AurePPlaybackSession",
                    "report progress failed itemId=${state.itemId} event=$eventName",
                    throwable,
                )
            }
        }
    }

    fun reportPlaybackStopped(state: EmbyPlaybackSessionState) {
        val activeSession = session ?: return
        val repository = repository ?: return
        viewModelScope.launch {
            runCatching {
                repository.reportPlaybackStopped(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    state = state,
                )
            }.onFailure { throwable ->
                Log.w("AurePPlaybackSession", "report stop failed itemId=${state.itemId}", throwable)
            }
        }
    }

    fun updatePlayerMode(value: String) {
        viewModelScope.launch {
            settingsStore.updatePlayerMode(value)
        }
    }

    fun updateEmbeddedSubtitleLanguage(value: String) {
        viewModelScope.launch {
            settingsStore.updateEmbeddedSubtitleLanguage(value)
        }
    }

    fun updateExternalSubtitleLanguage(value: String) {
        viewModelScope.launch {
            settingsStore.updateExternalSubtitleLanguage(value)
        }
    }

    @Deprecated("Use updateEmbeddedSubtitleLanguage/updateExternalSubtitleLanguage")
    fun updateSubtitleMode(value: String) {
        viewModelScope.launch {
            settingsStore.updateEmbeddedSubtitleLanguage(value)
            settingsStore.updateExternalSubtitleLanguage(value)
        }
    }

    fun updateLayoutMode(value: String) {
        viewModelScope.launch {
            settingsStore.updateLayoutMode(value)
        }
    }

    fun updateShowLibraryCardTitle(value: Boolean) {
        viewModelScope.launch {
            settingsStore.updateShowLibraryCardTitle(value)
        }
    }

    fun updateShowEpisodeTitle(value: Boolean) {
        viewModelScope.launch {
            settingsStore.updateShowEpisodeTitle(value)
        }
    }

    fun updateLibraryFilter(libraryId: String, filter: LibraryFilterSpec) {
        val currentFilter = _settings.value.libraryFilterFor(libraryId)
        if (currentFilter == filter) return

        _settings.update { current ->
            current.copy(libraryFilters = current.libraryFilters + (libraryId to filter))
        }

        viewModelScope.launch {
            settingsStore.updateLibraryFilter(libraryId, filter)

            val activeSession = session ?: return@launch
            val repository = repository ?: return@launch
            val currentState = _uiState.value as? EmbyUiState.Ready ?: return@launch
            val selectedLibraryId = currentState.payload.selectedLibraryId ?: return@launch

            reloadLibrary(
                repository = repository,
                activeSession = activeSession,
                currentPayload = currentState.payload,
                parentId = selectedLibraryId,
                sortMode = _settings.value.librarySortMode,
                filter = filter,
                updateSelection = false,
                append = false,
            )
        }
    }

    fun updateLibrarySortMode(value: String) {
        val normalized = normalizeLibrarySortMode(value)
        if (_settings.value.librarySortMode == normalized) {
            return
        }

        _settings.update { current ->
            current.copy(librarySortMode = normalized)
        }

        viewModelScope.launch {
            settingsStore.updateLibrarySortMode(normalized)

            val activeSession = session ?: return@launch
            val repository = repository ?: return@launch
            val currentState = _uiState.value as? EmbyUiState.Ready ?: return@launch
            val selectedLibraryId = currentState.payload.selectedLibraryId ?: return@launch

            reloadLibrary(
                repository = repository,
                activeSession = activeSession,
                currentPayload = currentState.payload,
                parentId = selectedLibraryId,
                sortMode = normalized,
                filter = _settings.value.libraryFilterFor(selectedLibraryId),
                updateSelection = false,
                append = false,
            )
        }
    }

    fun updateExperimentalDualBackendRace(value: Boolean) {
        if (_settings.value.experimentalDualBackendRace == value) {
            return
        }

        _settings.update { current ->
            current.copy(experimentalDualBackendRace = value)
        }

        viewModelScope.launch {
            settingsStore.updateExperimentalDualBackendRace(value)
        }
    }

    fun updateHomeModuleOrder(order: List<String>) {
        _settings.update { it.copy(homeModuleOrder = order) }
        viewModelScope.launch { settingsStore.updateHomeModuleOrder(order) }
    }

    fun toggleHomeModuleHidden(moduleId: String) {
        val current = _settings.value.homeModuleHidden
        val updated = if (moduleId in current) current - moduleId else current + moduleId
        _settings.update { it.copy(homeModuleHidden = updated) }
        viewModelScope.launch { settingsStore.updateHomeModuleHidden(updated) }
    }

    fun updateHomeModuleHidden(hidden: Set<String>) {
        _settings.update { it.copy(homeModuleHidden = hidden) }
        viewModelScope.launch { settingsStore.updateHomeModuleHidden(hidden) }
    }

    fun updateLibraryColumnCount(count: Int) {
        val clamped = count.coerceIn(
            com.qiuhu.embyflow.data.settings.LIBRARY_COLUMN_COUNT_MIN,
            com.qiuhu.embyflow.data.settings.LIBRARY_COLUMN_COUNT_MAX,
        )
        _settings.update { it.copy(libraryColumnCount = clamped) }
        viewModelScope.launch { settingsStore.updateLibraryColumnCount(clamped) }
    }

    fun refreshAppUpdateStatus() {
        if (_appUpdateState.value.isChecking) {
            return
        }

        viewModelScope.launch {
            val previousState = _appUpdateState.value
            _appUpdateState.value = previousState.copy(
                currentVersion = BuildConfig.VERSION_NAME,
                isChecking = true,
                errorMessage = null,
            )

            runCatching {
                appUpdateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
            }.onSuccess { state ->
                _appUpdateState.value = state
            }.onFailure { throwable ->
                _appUpdateState.value = AppUpdateState(
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestVersion = previousState.latestVersion,
                    hasUpdate = previousState.hasUpdate,
                    updatePageUrl = previousState.updatePageUrl,
                    downloadUrl = previousState.downloadUrl,
                    isChecking = false,
                    errorMessage = throwable.message ?: "暂时无法检查更新",
                )
            }
        }
    }

    fun saveServerProfile(profile: ServerProfile) {
        val normalized = profile.normalized()
        viewModelScope.launch {
            serverProfilesStore.upsert(
                profile = normalized,
                makeActive = true,
            )
            bootstrap()
        }
    }

    fun deleteServerProfile(profileId: String) {
        viewModelScope.launch {
            serverProfilesStore.delete(profileId)
            bootstrap()
        }
    }

    fun activateServerProfile(profileId: String) {
        if (_serverProfilesState.value.activeProfileId == profileId) {
            return
        }
        viewModelScope.launch {
            serverProfilesStore.setActive(profileId)
            bootstrap()
        }
    }

    fun openTag(tag: MediaTag) {
        val activeSession = session ?: return
        val repository = repository ?: return
        if (_tagBrowseState.value.activeTag == tag && _tagBrowseState.value.items.isNotEmpty()) {
            return
        }

        _tagBrowseState.value = TagBrowseState(
            activeTag = tag,
            items = _tagBrowseState.value.items.takeIf { _tagBrowseState.value.activeTag == tag }.orEmpty(),
            isLoading = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            runCatching {
                repository.loadItemsForTag(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    tag = tag,
                )
            }.onSuccess { items ->
                _tagBrowseState.value = TagBrowseState(
                    activeTag = tag,
                    items = items,
                    isLoading = false,
                    errorMessage = null,
                )
            }.onFailure { throwable ->
                _tagBrowseState.value = TagBrowseState(
                    activeTag = tag,
                    items = emptyList(),
                    isLoading = false,
                    errorMessage = throwable.message ?: "无法加载标签分类内容",
                )
            }
        }
    }

    fun closeTagBrowse() {
        _tagBrowseState.value = TagBrowseState()
    }

    fun openActor(actor: MediaPerson) {
        val activeSession = session ?: return
        val repository = repository ?: return
        val actorId = actor.id.trim()
        if (actorId.isBlank()) {
            _actorBrowseState.value = ActorBrowseState(
                activeActor = actor,
                items = emptyList(),
                isLoading = false,
                errorMessage = "这个演员暂时没有可用的资料",
            )
            return
        }
        if (_actorBrowseState.value.activeActor?.id == actorId && _actorBrowseState.value.items.isNotEmpty()) {
            return
        }

        _actorBrowseState.value = ActorBrowseState(
            activeActor = actor,
            items = _actorBrowseState.value.items.takeIf { _actorBrowseState.value.activeActor?.id == actorId }.orEmpty(),
            isLoading = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            runCatching {
                repository.loadItemsForPerson(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    personId = actorId,
                )
            }.onSuccess { items ->
                _actorBrowseState.value = ActorBrowseState(
                    activeActor = actor,
                    items = items,
                    isLoading = false,
                    errorMessage = null,
                )
            }.onFailure { throwable ->
                _actorBrowseState.value = ActorBrowseState(
                    activeActor = actor,
                    items = emptyList(),
                    isLoading = false,
                    errorMessage = throwable.message ?: "无法加载这个演员的作品",
                )
            }
        }
    }

    fun closeActorBrowse() {
        _actorBrowseState.value = ActorBrowseState()
    }

    fun toggleItemPlayed(media: MediaItem) {
        val activeSession = session ?: return
        val repository = repository ?: return
        viewModelScope.launch {
            runCatching {
                if (media.played) {
                    repository.markUnplayed(activeSession.userId, activeSession.accessToken, media.id)
                } else {
                    repository.markPlayed(activeSession.userId, activeSession.accessToken, media.id)
                }
            }
        }
    }

    fun setSleepTimer(durationMs: Long) {
        cancelSleepTimer()
        val endMs = SystemClock.elapsedRealtime() + durationMs
        _sleepTimerEndMs.value = endMs
        sleepTimerJob = viewModelScope.launch {
            delay(durationMs)
            _sleepTimerEndMs.value = null
            closePlayer()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndMs.value = null
    }

    fun updateSearchQuery(value: String) {
        val query = value
        searchGeneration += 1L
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchState.update { current ->
                current.copy(
                    query = "",
                    results = emptyList(),
                    isLoading = false,
                    errorMessage = null,
                )
            }
            return
        }

        val generation = searchGeneration
        _searchState.update { current ->
            current.copy(
                query = query,
                results = emptyList(),
                isLoading = true,
                errorMessage = null,
            )
        }

        val activeSession = session ?: run {
            _searchState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = "当前还没有可用的 Emby 会话",
                )
            }
            return
        }
        val repository = repository ?: run {
            _searchState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = "当前还没有可用的 Emby 会话",
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(260L)

            runCatching {
                repository.searchMedia(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    query = query,
                )
            }.onSuccess { items ->
                if (generation != searchGeneration || _searchState.value.query != query) return@onSuccess
                _searchState.update { current ->
                    current.copy(
                        results = items,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                if (generation != searchGeneration || _searchState.value.query != query) return@onFailure
                _searchState.update { current ->
                    current.copy(
                        results = emptyList(),
                        isLoading = false,
                        errorMessage = throwable.message ?: "搜索失败",
                    )
                }
            }
        }
    }

    fun selectRecentSearch(query: String) {
        updateSearchQuery(query.trim())
    }

    fun recordSearchQuery(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            searchHistoryStore.record(normalized)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            searchHistoryStore.clear()
        }
    }

    fun clearSearch() {
        searchGeneration += 1L
        searchJob?.cancel()
        _searchState.update { current ->
            current.copy(
                query = "",
                results = emptyList(),
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    fun loadMediaDetail(media: MediaItem) {
        val cachedMedia = resolveMediaWithResume(media)
        _mediaDetails.update { current -> current + (cachedMedia.id to cachedMedia) }
        prefetchPlaybackSourceIfPossible(cachedMedia)
        if (cachedMedia.isSeries) {
            loadSeriesDetail(cachedMedia)
        }

        if (cachedMedia.actors.isNotEmpty()) {
            return
        }

        val activeSession = session ?: return
        val repository = repository ?: return
        if (!detailLoadingIds.add(media.id)) {
            return
        }

        viewModelScope.launch {
            runCatching {
                repository.loadMediaDetail(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    itemId = cachedMedia.id,
                )
            }.onSuccess { detail ->
                val mergedDetail = detail.copy(
                    resumePositionMs = maxOf(
                        detail.resumePositionMs,
                        cachedMedia.resumePositionMs,
                    ),
                    seasonId = detail.seasonId ?: cachedMedia.seasonId,
                    seasonNumber = detail.seasonNumber ?: cachedMedia.seasonNumber,
                    episodeNumber = detail.episodeNumber ?: cachedMedia.episodeNumber,
                )
                prefetchPlaybackSourceIfPossible(mergedDetail)
                _mediaDetails.update { current -> current + (mergedDetail.id to mergedDetail) }
                if (mergedDetail.isSeries) {
                    loadSeriesDetail(mergedDetail)
                }
            }.onFailure {
                _mediaDetails.update { current ->
                    if (current.containsKey(cachedMedia.id)) current else current + (cachedMedia.id to cachedMedia)
                }
            }
            detailLoadingIds.remove(cachedMedia.id)
        }
    }

    fun selectSeriesSeason(
        seriesId: String,
        seasonId: String,
    ) {
        val activeSession = session ?: return
        val repository = repository ?: return
        val current = _seriesDetails.value[seriesId] ?: return
        if (current.selectedSeasonId == seasonId && current.episodes.isNotEmpty()) {
            return
        }

        _seriesDetails.update { state ->
            state + (
                seriesId to current.copy(
                    selectedSeasonId = seasonId,
                    episodes = emptyList(),
                    isLoading = true,
                    errorMessage = null,
                )
            )
        }

        viewModelScope.launch {
            runCatching {
                repository.loadSeasonEpisodes(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    seasonId = seasonId,
                )
            }.onSuccess { episodes ->
                _seriesDetails.update { state ->
                    val latest = state[seriesId] ?: current
                    state + (
                        seriesId to latest.copy(
                            selectedSeasonId = seasonId,
                            episodes = episodes,
                            isLoading = false,
                            errorMessage = null,
                        )
                    )
                }
            }.onFailure { throwable ->
                _seriesDetails.update { state ->
                    val latest = state[seriesId] ?: current
                    state + (
                        seriesId to latest.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "无法读取这个分季的剧集内容",
                        )
                    )
                }
            }
        }
    }

    fun selectLibrary(viewId: String) {
        val activeSession = session ?: return
        val repository = repository ?: return
        val currentState = _uiState.value as? EmbyUiState.Ready ?: return
        if (currentState.payload.selectedLibraryId == viewId && currentState.payload.libraryItems.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            reloadLibrary(
                repository = repository,
                activeSession = activeSession,
                currentPayload = currentState.payload,
                parentId = viewId,
                sortMode = _settings.value.librarySortMode,
                filter = _settings.value.libraryFilterFor(viewId),
                updateSelection = true,
                append = false,
            )
        }

        val collectionType = currentState.payload.libraries
            .firstOrNull { it.id == viewId }
            ?.collectionType
            .orEmpty()
        loadAvailableGenres(repository, activeSession, viewId, collectionType)
    }

    private fun loadAvailableGenres(
        repository: EmbyRepository,
        activeSession: EmbySession,
        parentId: String,
        collectionType: String,
    ) {
        val includeItemTypes = when (collectionType.lowercase(java.util.Locale.US)) {
            "tvshows" -> "Series"
            else -> "Movie,Episode,Series,Video"
        }
        viewModelScope.launch {
            runCatching {
                repository.loadLibraryGenres(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    parentId = parentId,
                    includeItemTypes = includeItemTypes,
                )
            }.onSuccess { genres ->
                _availableGenres.value = genres
            }.onFailure {
                _availableGenres.value = emptyList()
            }
        }
    }

    fun loadMoreLibrary() {
        val activeSession = session ?: return
        val repository = repository ?: return
        val currentState = _uiState.value as? EmbyUiState.Ready ?: return
        val selectedLibraryId = currentState.payload.selectedLibraryId ?: return
        if (currentState.isRefreshingLibrary || currentState.isAppendingLibrary) {
            return
        }
        if (currentState.payload.libraryItems.size >= currentState.payload.libraryTotalCount) {
            return
        }

        viewModelScope.launch {
            reloadLibrary(
                repository = repository,
                activeSession = activeSession,
                currentPayload = currentState.payload,
                parentId = selectedLibraryId,
                sortMode = _settings.value.librarySortMode,
                filter = _settings.value.libraryFilterFor(selectedLibraryId),
                updateSelection = false,
                append = true,
            )
        }
    }

    private fun loadSeriesDetail(media: MediaItem) {
        val activeSession = session ?: return
        val repository = repository ?: return
        val resolvedMedia = resolveMediaWithResume(media)
        val seriesId = resolvedMedia.id
        val preferredSeasonId = resolvedMedia.seasonId?.takeIf {
            resolvedMedia.resumePositionMs > 0L && it.isNotBlank()
        }
        val current = _seriesDetails.value[seriesId]
        val alreadyReady = current != null &&
            current.episodes.isNotEmpty() &&
            (
                current.seasons.isEmpty() ||
                    current.selectedSeasonId == preferredSeasonId ||
                    (preferredSeasonId == null && current.selectedSeasonId != null)
                )
        if (current?.isLoading == true || alreadyReady) {
            return
        }

        _seriesDetails.update { state ->
            state + (
                seriesId to (current ?: SeriesDetailState()).copy(
                    isLoading = true,
                    errorMessage = null,
                )
            )
        }

        viewModelScope.launch {
            runCatching {
                val content = repository.loadSeriesContent(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    seriesId = seriesId,
                )
                val resolvedSelectedSeasonId = preferredSeasonId?.takeIf { preferredId ->
                    content.seasons.any { it.id == preferredId }
                } ?: content.selectedSeasonId
                val resolvedEpisodes = if (
                    preferredSeasonId != null &&
                    resolvedSelectedSeasonId != content.selectedSeasonId
                ) {
                    repository.loadSeasonEpisodes(
                        userId = activeSession.userId,
                        token = activeSession.accessToken,
                        seasonId = preferredSeasonId,
                    )
                } else {
                    content.episodes
                }
                content.nextUpEpisode?.let(::prefetchPlaybackSourceIfPossible)
                    ?: resolvedEpisodes.firstOrNull()?.let(::prefetchPlaybackSourceIfPossible)
                _seriesDetails.update { state ->
                    state + (
                        seriesId to SeriesDetailState(
                            seasons = content.seasons,
                            selectedSeasonId = resolvedSelectedSeasonId,
                            episodes = resolvedEpisodes,
                            nextUpEpisode = content.nextUpEpisode,
                            isLoading = false,
                            errorMessage = null,
                        )
                    )
                }
            }.onFailure { throwable ->
                _seriesDetails.update { state ->
                    val latest = state[seriesId] ?: SeriesDetailState()
                    state + (
                        seriesId to latest.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "无法整理这个剧集的分季信息",
                        )
                    )
                }
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            serverProfilesStore.ensureSeeded(
                defaultUrl = BuildConfig.EMBY_SERVER_URL,
                defaultUsername = BuildConfig.EMBY_USERNAME,
                defaultPassword = BuildConfig.EMBY_PASSWORD,
            )
            val serverProfiles = serverProfilesStore.currentState()
            _serverProfilesState.value = serverProfiles
            val activeSettings = settingsStore.currentSettings()
            _settings.value = activeSettings
            val activeProfile = serverProfiles.activeProfile

            if (activeProfile == null) {
                activeServerProfileId = null
                session = null
                repository = null
                serverPayload = null
                clearPlaybackPreparationCache()
                syncScopedResumeItems()
                _uiState.value = EmbyUiState.Error(
                    title = "还没有配置服务器",
                    detail = "请在设置里的服务器卡片中新增可用的 Emby 节点。",
                )
                return@launch
            }

            if (
                activeProfile.serverUrl.isBlank() ||
                activeProfile.username.isBlank() ||
                activeProfile.password.isBlank()
            ) {
                activeServerProfileId = activeProfile.id
                session = null
                repository = null
                serverPayload = null
                clearPlaybackPreparationCache()
                syncScopedResumeItems()
                _uiState.value = EmbyUiState.Error(
                    title = "服务器配置不完整",
                    detail = "请补全服务器地址、用户名和密码后再连接。",
                )
                return@launch
            }

            val activeRepository = EmbyRepository(activeProfile.serverUrl)
            activeServerProfileId = activeProfile.id
            clearPlaybackPreparationCache()
            repository = activeRepository
            session = null
            syncScopedResumeItems()

            _uiState.value = EmbyUiState.Loading
            _tagBrowseState.value = TagBrowseState()
            _actorBrowseState.value = ActorBrowseState()
            _mediaDetails.value = emptyMap()
            _seriesDetails.value = emptyMap()
            detailLoadingIds.clear()
            resumeLogoAttemptedIds.clear()
            resumeLogoEnrichmentJob?.cancel()
            _searchState.update { current ->
                current.copy(
                    query = "",
                    results = emptyList(),
                    isLoading = false,
                    errorMessage = null,
                )
            }
            searchGeneration += 1L
            searchJob?.cancel()

            runCatching {
                activeRepository.bootstrap(
                    username = activeProfile.username,
                    password = activeProfile.password,
                    librarySortMode = activeSettings.librarySortMode,
                )
            }.onSuccess { result ->
                applyBootstrap(result)
            }.onFailure { throwable ->
                session = null
                serverPayload = null
                clearPlaybackPreparationCache()
                syncScopedResumeItems()
                _uiState.value = EmbyUiState.Error(
                    title = "连接 Emby 失败",
                    detail = throwable.message ?: "未知错误",
                )
            }
        }
    }

    private fun applyBootstrap(result: EmbyBootstrapResult) {
        session = result.session
        serverPayload = result.payload
        syncScopedResumeItems()
        _uiState.value = EmbyUiState.Ready(
            payload = result.payload.withMergedResume(localResumeItems),
        )
        scheduleResumeLogoEnrichment()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.settings.collectLatest { settings ->
                _settings.value = settings
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            continueWatchingStore.entries.collectLatest { entries ->
                cachedResumeEntries = entries
                syncScopedResumeItems()
                serverPayload?.let { payload ->
                    _uiState.update { current ->
                        (current as? EmbyUiState.Ready)?.copy(
                            payload = payload.withMergedResume(localResumeItems),
                        ) ?: current
                    }
                    scheduleResumeLogoEnrichment()
                }
            }
        }
    }

    private fun scheduleResumeLogoEnrichment() {
        val activeSession = session ?: return
        val activeRepository = repository ?: return
        val currentPayload = (_uiState.value as? EmbyUiState.Ready)?.payload ?: return
        val targets = currentPayload.resumeItems
            .asSequence()
            .filterNot { it.isFolder }
            .filter { item ->
                item.titleLogoUrl.isNullOrBlank() ||
                    (
                        item.isEpisode &&
                            !item.seriesId.isNullOrBlank() &&
                            (
                                item.seriesTitleLogoUrl.isNullOrBlank() ||
                                    item.seriesPrimaryImageUrl.isNullOrBlank() ||
                                    item.seriesBackdropImageUrl.isNullOrBlank()
                                )
                        )
            }
            .filterNot { resumeLogoAttemptedIds.contains(it.id) }
            .take(8)
            .toList()

        if (targets.isEmpty()) return

        targets.forEach { resumeLogoAttemptedIds += it.id }
        resumeLogoEnrichmentJob?.cancel()
        resumeLogoEnrichmentJob = viewModelScope.launch {
            targets.forEach { item ->
                runCatching {
                    activeRepository.loadMediaDetail(
                        userId = activeSession.userId,
                        token = activeSession.accessToken,
                        itemId = item.id,
                    )
                }.onSuccess { detail ->
                    val seriesDetail = if (detail.isEpisode && !detail.seriesId.isNullOrBlank()) {
                        runCatching {
                            activeRepository.loadMediaDetail(
                                userId = activeSession.userId,
                                token = activeSession.accessToken,
                                itemId = detail.seriesId,
                            )
                        }.getOrNull()
                    } else {
                        null
                    }
                    val enrichedDetail = detail.copy(
                        resumePositionMs = maxOf(
                            detail.resumePositionMs,
                            item.resumePositionMs,
                            resolveScopedResumePosition(item),
                        ),
                        seriesTitleLogoUrl = detail.seriesTitleLogoUrl
                            ?: seriesDetail?.titleLogoUrl
                            ?: item.seriesTitleLogoUrl,
                        seriesPrimaryImageUrl = detail.seriesPrimaryImageUrl
                            ?: seriesDetail?.primaryImageUrl
                            ?: item.seriesPrimaryImageUrl,
                        seriesBackdropImageUrl = detail.seriesBackdropImageUrl
                            ?: seriesDetail?.backdropImageUrl
                            ?: item.seriesBackdropImageUrl,
                    )
                    _mediaDetails.update { current -> current + (item.id to enrichedDetail) }
                    serverPayload = serverPayload?.copy(
                        resumeItems = serverPayload
                            ?.resumeItems
                            .orEmpty()
                            .map { resumeItem ->
                                if (resumeItem.id == item.id) {
                                    enrichedDetail
                                } else {
                                    resumeItem
                                }
                            },
                    )
                    _uiState.update { current ->
                        val ready = current as? EmbyUiState.Ready ?: return@update current
                        ready.copy(
                            payload = ready.payload.copy(
                                resumeItems = ready.payload.resumeItems.map { resumeItem ->
                                    if (resumeItem.id == item.id) {
                                        enrichedDetail.mergeWithResumeFallback(resumeItem)
                                    } else {
                                        resumeItem
                                    }
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun syncScopedResumeItems() {
        val profileId = activeServerProfileId
        val userId = session?.userId
        localResumeItems = cachedResumeEntries
            .asSequence()
            .filter { entry ->
                profileId != null &&
                    entry.serverProfileId == profileId &&
                    (userId.isNullOrBlank() || entry.serverUserId == userId)
            }
            .map { it.toMediaItem() }
            .toList()
    }

    private fun resolveScopedResumePosition(media: MediaItem): Long {
        return resolveScopedResumeItem(media)?.resumePositionMs?.coerceAtLeast(0L) ?: 0L
    }

    private fun needsPlaybackBrandingRefresh(media: MediaItem): Boolean {
        return when {
            media.isEpisode -> !media.seriesId.isNullOrBlank() && media.seriesTitleLogoUrl.isNullOrBlank()
            media.isFolder -> false
            else -> media.titleLogoUrl.isNullOrBlank()
        }
    }

    private fun enrichPlaybackBrandingFromCache(media: MediaItem): MediaItem {
        val cachedSelf = _mediaDetails.value[media.id]
        val cachedSeries = media.seriesId
            ?.takeIf { media.isEpisode && it.isNotBlank() }
            ?.let(_mediaDetails.value::get)

        return media.copy(
            titleLogoUrl = media.titleLogoUrl ?: cachedSelf?.titleLogoUrl,
            seriesTitleLogoUrl = media.seriesTitleLogoUrl
                ?: cachedSelf?.seriesTitleLogoUrl
                ?: cachedSeries?.titleLogoUrl
                ?: cachedSeries?.seriesTitleLogoUrl,
            seriesPrimaryImageUrl = media.seriesPrimaryImageUrl
                ?: cachedSelf?.seriesPrimaryImageUrl
                ?: cachedSeries?.primaryImageUrl
                ?: cachedSeries?.seriesPrimaryImageUrl,
            seriesBackdropImageUrl = media.seriesBackdropImageUrl
                ?: cachedSelf?.seriesBackdropImageUrl
                ?: cachedSeries?.backdropImageUrl
                ?: cachedSeries?.seriesBackdropImageUrl,
        )
    }

    private fun refreshPlaybackBrandingIfNeeded(
        media: MediaItem,
        requestGeneration: Long,
    ) {
        if (!needsPlaybackBrandingRefresh(media)) return
        val activeSession = session ?: return
        val activeRepository = repository ?: return

        viewModelScope.launch {
            val detail = runCatching {
                activeRepository.loadMediaDetail(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    itemId = media.id,
                )
            }.getOrNull() ?: media
            val seriesDetail = media.seriesId
                ?.takeIf { media.isEpisode && it.isNotBlank() }
                ?.let { seriesId ->
                    runCatching {
                        activeRepository.loadMediaDetail(
                            userId = activeSession.userId,
                            token = activeSession.accessToken,
                            itemId = seriesId,
                        )
                    }.getOrNull()
                }

            val enrichedMedia = enrichPlaybackBrandingFromCache(
                resolveMediaWithResume(
                    detail.copy(
                        resumePositionMs = maxOf(detail.resumePositionMs, media.resumePositionMs),
                        titleLogoUrl = detail.titleLogoUrl ?: media.titleLogoUrl,
                        seriesTitleLogoUrl = detail.seriesTitleLogoUrl
                            ?: media.seriesTitleLogoUrl
                            ?: seriesDetail?.titleLogoUrl
                            ?: seriesDetail?.seriesTitleLogoUrl,
                        seriesPrimaryImageUrl = detail.seriesPrimaryImageUrl
                            ?: media.seriesPrimaryImageUrl
                            ?: seriesDetail?.primaryImageUrl
                            ?: seriesDetail?.seriesPrimaryImageUrl,
                        seriesBackdropImageUrl = detail.seriesBackdropImageUrl
                            ?: media.seriesBackdropImageUrl
                            ?: seriesDetail?.backdropImageUrl
                            ?: seriesDetail?.seriesBackdropImageUrl,
                    ),
                ),
            )

            _mediaDetails.update { current ->
                buildMap {
                    putAll(current)
                    put(enrichedMedia.id, enrichedMedia)
                    seriesDetail?.let { put(it.id, it) }
                }
            }

            if (requestGeneration != playbackRequestGeneration) {
                return@launch
            }

            _playbackState.update { current ->
                val ready = current as? PlaybackUiState.Ready ?: return@update current
                if (ready.media.id != media.id) {
                    current
                } else {
                    ready.copy(
                        media = enrichPlaybackBrandingFromCache(
                            ready.media.mergeWithResumeFallback(enrichedMedia),
                        ),
                    )
                }
            }
        }
    }

    private fun resolveMediaWithResume(media: MediaItem): MediaItem {
        val cachedMedia = _mediaDetails.value[media.id]
        val cachedSeriesTitleLogoUrl = media.seriesId
            ?.takeIf { media.isEpisode && it.isNotBlank() }
            ?.let { seriesId ->
                _mediaDetails.value[seriesId]?.titleLogoUrl
                    ?: _mediaDetails.value[seriesId]?.seriesTitleLogoUrl
            }
        val scopedResumeItem = resolveScopedResumeItem(media)
        val cachedSeriesPrimaryImageUrl = media.seriesId
            ?.takeIf { media.isEpisode && it.isNotBlank() }
            ?.let { seriesId ->
                _mediaDetails.value[seriesId]?.primaryImageUrl
                    ?: _mediaDetails.value[seriesId]?.seriesPrimaryImageUrl
            }
        val cachedSeriesBackdropImageUrl = media.seriesId
            ?.takeIf { media.isEpisode && it.isNotBlank() }
            ?.let { seriesId ->
                _mediaDetails.value[seriesId]?.backdropImageUrl
                    ?: _mediaDetails.value[seriesId]?.seriesBackdropImageUrl
            }
        val resolvedMedia = (cachedMedia?.mergeWithResumeFallback(media) ?: media)
            .mergeWithResumeFallback(scopedResumeItem ?: media)

        return resolvedMedia.copy(
            resumePositionMs = maxOf(
                cachedMedia?.resumePositionMs ?: 0L,
                media.resumePositionMs,
                scopedResumeItem?.resumePositionMs ?: 0L,
            ),
            seasonId = media.seasonId ?: cachedMedia?.seasonId,
            seasonNumber = media.seasonNumber ?: cachedMedia?.seasonNumber,
            episodeNumber = media.episodeNumber ?: cachedMedia?.episodeNumber,
            seriesTitleLogoUrl = resolvedMedia.seriesTitleLogoUrl ?: cachedSeriesTitleLogoUrl,
            seriesPrimaryImageUrl = resolvedMedia.seriesPrimaryImageUrl ?: cachedSeriesPrimaryImageUrl,
            seriesBackdropImageUrl = resolvedMedia.seriesBackdropImageUrl ?: cachedSeriesBackdropImageUrl,
        )
    }

    private fun resolveScopedResumeItem(media: MediaItem): MediaItem? {
        val profileId = activeServerProfileId ?: return null
        val userId = session?.userId
        val exactMatch = cachedResumeEntries
            .firstOrNull { entry ->
                entry.serverProfileId == profileId &&
                    (userId.isNullOrBlank() || entry.serverUserId == userId) &&
                    entry.id == media.id
            }
            ?.toMediaItem()
        if (exactMatch != null) {
            return exactMatch
        }
        if (!media.isSeries && !media.isSeason) {
            return null
        }
        return cachedResumeEntries
            .firstOrNull { entry ->
                entry.serverProfileId == profileId &&
                    (userId.isNullOrBlank() || entry.serverUserId == userId) &&
                    resumeGroupingKey(entry) == resumeGroupingKey(media)
            }
            ?.toMediaItem()
    }

    private fun applyOptimisticResumeUpdate(
        media: MediaItem,
        positionMs: Long,
        durationMs: Long,
        serverProfileId: String,
        serverUserId: String,
    ) {
        val keepResume = shouldKeepResume(
            positionMs = positionMs,
            durationMs = durationMs,
            isFolder = media.isFolder,
        )
        val updatedEntries = cachedResumeEntries
            .filterNot { entry ->
                entry.serverProfileId == serverProfileId &&
                    entry.serverUserId == serverUserId &&
                    resumeGroupingKey(entry) == resumeGroupingKey(media)
            }
            .toMutableList()

        if (keepResume) {
            updatedEntries += ContinueWatchingEntry(
                id = media.id,
                serverProfileId = serverProfileId,
                serverUserId = serverUserId,
                title = media.title,
                meta = media.meta,
                summary = media.summary,
                score = media.score,
                year = media.year,
                genres = media.genres,
                primaryImageUrl = media.primaryImageUrl,
                titleLogoUrl = media.titleLogoUrl,
                seriesTitleLogoUrl = media.seriesTitleLogoUrl,
                seriesPrimaryImageUrl = media.seriesPrimaryImageUrl,
                seriesBackdropImageUrl = media.seriesBackdropImageUrl,
                backdropImageUrl = media.backdropImageUrl,
                extraFanartUrls = media.extraFanartUrls,
                seriesId = media.seriesId,
                seriesName = media.seriesName,
                mediaType = media.mediaType,
                seasonId = media.seasonId,
                seasonNumber = media.seasonNumber,
                episodeNumber = media.episodeNumber,
                isFolder = media.isFolder,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            )
        }

        cachedResumeEntries = updatedEntries
        syncScopedResumeItems()
        serverPayload?.let { payload ->
            _uiState.update { current ->
                (current as? EmbyUiState.Ready)?.copy(
                    payload = payload.withMergedResume(localResumeItems),
                ) ?: current
            }
        }
        val updatedMedia = resolveMediaWithResume(media).copy(
            resumePositionMs = if (keepResume) positionMs else 0L,
        )
        _mediaDetails.update { current ->
            current + (media.id to updatedMedia)
        }

        val seriesId = media.seriesId?.takeIf { it.isNotBlank() }
        if (seriesId != null) {
            val cachedSeries = _mediaDetails.value[seriesId]
            if (cachedSeries != null) {
                val refreshedSeries = resolveMediaWithResume(cachedSeries).copy(
                    resumePositionMs = if (keepResume) positionMs else 0L,
                    seasonId = media.seasonId ?: cachedSeries.seasonId,
                    seasonNumber = media.seasonNumber ?: cachedSeries.seasonNumber,
                    episodeNumber = media.episodeNumber ?: cachedSeries.episodeNumber,
                )
                _mediaDetails.update { current ->
                    current + (seriesId to refreshedSeries)
                }
                _seriesDetails.update { state ->
                    val currentSeries = state[seriesId] ?: return@update state
                    val updatedEpisodes = currentSeries.episodes.map { episode ->
                        if (episode.id == media.id) {
                            episode.copy(
                                resumePositionMs = if (keepResume) positionMs else 0L,
                            )
                        } else {
                            episode
                        }
                    }
                    val updatedNextUp = currentSeries.nextUpEpisode?.let { nextUp ->
                        if (nextUp.id == media.id) {
                            nextUp.copy(
                                resumePositionMs = if (keepResume) positionMs else 0L,
                            )
                        } else {
                            nextUp
                        }
                    }
                    state + (
                        seriesId to currentSeries.copy(
                            episodes = updatedEpisodes,
                            nextUpEpisode = updatedNextUp,
                        )
                    )
                }
            }
        }
    }

    private fun persistLocalPlaybackProgressIfNeeded(
        state: EmbyPlaybackSessionState,
        eventName: String,
    ) {
        val profileId = activeServerProfileId?.takeIf { it.isNotBlank() } ?: return
        val userId = session?.userId?.takeIf { it.isNotBlank() } ?: return
        val playbackMedia = when (val current = _playbackState.value) {
            is PlaybackUiState.Loading -> current.media
            is PlaybackUiState.Ready -> current.media
            else -> null
        }?.let(::resolveMediaWithResume) ?: return

        val positionMs = state.positionMs.coerceAtLeast(0L)
        val durationMs = state.durationMs.coerceAtLeast(0L)
        if (!shouldKeepResume(
                positionMs = positionMs,
                durationMs = durationMs,
                isFolder = playbackMedia.isFolder,
            )
        ) {
            return
        }

        val persistenceKey = buildString {
            append(profileId)
            append('|')
            append(userId)
            append('|')
            append(resumeGroupingKey(playbackMedia))
        }
        val nowMs = SystemClock.elapsedRealtime()
        val positionDeltaMs = kotlin.math.abs(positionMs - lastLocalResumePersistPositionMs)
        val shouldPersist = when {
            persistenceKey != lastLocalResumePersistKey -> true
            eventName == "AppBackground" -> true
            eventName == "TimeUpdate" ->
                nowMs - lastLocalResumePersistAtMs >= LocalResumePersistThrottleMs ||
                    positionDeltaMs >= LocalResumePersistMinPositionDeltaMs

            eventName == "Pause" || eventName == "Unpause" ->
                nowMs - lastLocalResumePersistAtMs >= LocalResumePersistMinPositionDeltaMs ||
                    positionDeltaMs >= LocalResumePersistMinPositionDeltaMs

            else -> false
        }

        if (!shouldPersist) {
            return
        }

        lastLocalResumePersistKey = persistenceKey
        lastLocalResumePersistAtMs = nowMs
        lastLocalResumePersistPositionMs = positionMs
        persistContinueWatchingSnapshot(
            media = playbackMedia,
            positionMs = positionMs,
            durationMs = durationMs,
            serverProfileId = profileId,
            serverUserId = userId,
        )
    }

    private fun persistContinueWatchingSnapshot(
        media: MediaItem,
        positionMs: Long,
        durationMs: Long,
        serverProfileId: String,
        serverUserId: String,
    ) {
        applyOptimisticResumeUpdate(
            media = media,
            positionMs = positionMs,
            durationMs = durationMs,
            serverProfileId = serverProfileId,
            serverUserId = serverUserId,
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                continueWatchingStore.update(
                    media = media,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    serverProfileId = serverProfileId,
                    serverUserId = serverUserId,
                )
            }.onFailure { throwable ->
                Log.w(
                    "AurePPlaybackSession",
                    "persist resume failed itemId=${media.id}",
                    throwable,
                )
            }
        }
    }

    private fun shouldKeepResume(
        positionMs: Long,
        durationMs: Long,
        isFolder: Boolean,
    ): Boolean {
        if (isFolder || durationMs <= 0L) return false
        if (positionMs < 15_000L) return false
        val progress = positionMs / durationMs.toFloat()
        val remainingMs = durationMs - positionMs
        return progress < 0.96f && remainingMs > 30_000L
    }

    private fun clearPlaybackPreparationCache() {
        playbackSourceJobs.values.forEach { request ->
            if (request.deferred.isActive) {
                request.deferred.cancel()
            }
        }
        playbackSourceJobs.clear()
        playbackSourceCache.clear()
    }

    private fun canWarmPlaybackSource(media: MediaItem): Boolean {
        return media.id.isNotBlank() &&
            !media.isFolder &&
            !media.isSeries &&
            !media.isSeason
    }

    private fun prefetchPlaybackSourceIfPossible(media: MediaItem) {
        val activeSession = session ?: return
        val repository = repository ?: return
        if (!canWarmPlaybackSource(media)) {
            return
        }
        startPlaybackSourceRequest(
            activeSession = activeSession,
            repository = repository,
            media = media,
            fallbackTitle = media.title,
            trigger = "prefetch",
        )
    }

    private suspend fun awaitPlaybackSource(
        activeSession: EmbySession,
        repository: EmbyRepository,
        media: MediaItem,
        fallbackTitle: String,
        trigger: String,
    ): EmbyPlaybackSource {
        playbackSourceCache[media.id]?.let { cached ->
            return cached
        }
        return startPlaybackSourceRequest(
            activeSession = activeSession,
            repository = repository,
            media = media,
            fallbackTitle = fallbackTitle,
            trigger = trigger,
        ).await().getOrThrow()
    }

    private fun startPlaybackSourceRequest(
        activeSession: EmbySession,
        repository: EmbyRepository,
        media: MediaItem,
        fallbackTitle: String,
        trigger: String,
    ): Deferred<Result<EmbyPlaybackSource>> {
        playbackSourceCache[media.id]?.let { cached ->
            return CompletableDeferred(Result.success(cached))
        }

        playbackSourceJobs[media.id]?.let { existing ->
            when {
                existing.deferred.isCompleted || existing.deferred.isCancelled -> {
                    playbackSourceJobs.remove(media.id)
                }
                trigger == "open-player" && existing.trigger == "prefetch" -> {
                    existing.deferred.cancel()
                    playbackSourceJobs.remove(media.id)
                }
                else -> return existing.deferred
            }
        }

        val deferred = viewModelScope.async(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            runCatching {
                repository.loadPlaybackSource(
                    userId = activeSession.userId,
                    token = activeSession.accessToken,
                    itemId = media.id,
                    fallbackTitle = fallbackTitle,
                )
            }.also { result ->
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                result.onSuccess {
                    Log.i(
                        PlaybackWarmupDebugTag,
                        "source ready trigger=$trigger itemId=${media.id} title=${fallbackTitle.ifBlank { media.title }} elapsedMs=$elapsedMs",
                    )
                }.onFailure { error ->
                    Log.w(
                        PlaybackWarmupDebugTag,
                        "source failed trigger=$trigger itemId=${media.id} title=${fallbackTitle.ifBlank { media.title }} elapsedMs=$elapsedMs",
                        error,
                    )
                }
            }
        }
        playbackSourceJobs[media.id] = PlaybackSourceJob(
            trigger = trigger,
            deferred = deferred,
        )
        viewModelScope.launch {
            val result = runCatching { deferred.await() }.getOrNull()
            if (playbackSourceJobs[media.id]?.deferred === deferred) {
                playbackSourceJobs.remove(media.id)
            }
            result?.onSuccess { source ->
                playbackSourceCache[media.id] = source
            }
        }
        return deferred
    }

    private fun observeServerProfiles() {
        viewModelScope.launch {
            serverProfilesStore.state.collectLatest { profiles ->
                _serverProfilesState.value = profiles
            }
        }
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            searchHistoryStore.queries.collectLatest { queries ->
                _searchState.update { current ->
                    current.copy(recentQueries = queries)
                }
            }
        }
    }

    private suspend fun reloadLibrary(
        repository: EmbyRepository,
        activeSession: EmbySession,
        currentPayload: EmbyHomePayload,
        parentId: String,
        sortMode: String,
        filter: LibraryFilterSpec = LibraryFilterSpec(),
        updateSelection: Boolean,
        append: Boolean,
    ) {
        _uiState.update { current ->
            (current as? EmbyUiState.Ready)?.copy(
                payload = current.payload.copy(
                    selectedLibraryId = if (updateSelection) parentId else current.payload.selectedLibraryId,
                ),
                isRefreshingLibrary = !append,
                isAppendingLibrary = append,
            ) ?: current
        }

        val collectionType = currentPayload.libraries
            .firstOrNull { it.id == parentId }
            ?.collectionType
            .orEmpty()

        runCatching {
            repository.loadLibraryItems(
                userId = activeSession.userId,
                token = activeSession.accessToken,
                parentId = parentId,
                collectionType = collectionType,
                sortMode = sortMode,
                filter = filter,
                startIndex = if (append) currentPayload.libraryItems.size else 0,
            )
        }.onSuccess { page ->
            val basePayload = serverPayload ?: currentPayload
            val mergedItems = if (append) {
                (basePayload.libraryItems + page.items).distinctBy { it.id }
            } else {
                page.items
            }
            val updatedPayload = basePayload.copy(
                selectedLibraryId = if (updateSelection) parentId else basePayload.selectedLibraryId,
                libraryItems = mergedItems,
                libraryTotalCount = page.totalCount,
            )
            serverPayload = updatedPayload
            _uiState.update { current ->
                (current as? EmbyUiState.Ready)?.copy(
                    payload = updatedPayload.withMergedResume(localResumeItems),
                    isRefreshingLibrary = false,
                    isAppendingLibrary = false,
                ) ?: current
            }
        }.onFailure { throwable ->
            if (append) {
                _uiState.update { current ->
                    (current as? EmbyUiState.Ready)?.copy(isAppendingLibrary = false) ?: current
                }
                return
            }
            _uiState.update { current ->
                (current as? EmbyUiState.Ready)?.copy(
                    isRefreshingLibrary = false,
                    isAppendingLibrary = false,
                ) ?: current
            }
            _uiState.value = EmbyUiState.Error(
                title = "媒体库加载失败",
                detail = throwable.message ?: "无法读取媒体库内容",
                fallback = currentPayload.withMergedResume(localResumeItems),
            )
        }
    }
}

private fun EmbyHomePayload.withMergedResume(
    localItems: List<MediaItem>,
): EmbyHomePayload = copy(
    resumeItems = mergeResumeItems(
        serverItems = resumeItems,
        localItems = localItems,
    ),
)

private fun mergeResumeItems(
    serverItems: List<MediaItem>,
    localItems: List<MediaItem>,
): List<MediaItem> {
    val items = LinkedHashMap<String, MediaItem>()
    localItems.forEach { item ->
        val key = resumeGroupingKey(item)
        if (!items.containsKey(key)) {
            items[key] = item
        }
    }
    serverItems.forEach { item ->
        val key = resumeGroupingKey(item)
        val localItem = items[key]
        items[key] = if (localItem == null) {
            item
        } else {
            localItem.mergeWithResumeFallback(item)
        }
    }
    return items.values.toList()
}

private fun MediaItem.mergeWithResumeFallback(
    fallback: MediaItem,
): MediaItem = copy(
    title = title.ifBlank { fallback.title },
    subtitle = subtitle.ifBlank { fallback.subtitle },
    meta = meta.ifBlank { fallback.meta },
    summary = summary.ifBlank { fallback.summary },
    score = score.ifBlank { fallback.score },
    colors = if (colors.isNotEmpty()) colors else fallback.colors,
    year = year.ifBlank { fallback.year },
    genres = if (genres.isNotEmpty()) genres else fallback.genres,
    primaryImageAspectRatio = primaryImageAspectRatio ?: fallback.primaryImageAspectRatio,
    primaryImageUrl = primaryImageUrl ?: fallback.primaryImageUrl,
    titleLogoUrl = titleLogoUrl ?: fallback.titleLogoUrl,
    seriesTitleLogoUrl = seriesTitleLogoUrl ?: fallback.seriesTitleLogoUrl,
    seriesPrimaryImageUrl = seriesPrimaryImageUrl ?: fallback.seriesPrimaryImageUrl,
    seriesBackdropImageUrl = seriesBackdropImageUrl ?: fallback.seriesBackdropImageUrl,
    backdropImageUrl = backdropImageUrl ?: fallback.backdropImageUrl,
    extraFanartUrls = if (extraFanartUrls.isNotEmpty()) extraFanartUrls else fallback.extraFanartUrls,
    actors = if (actors.isNotEmpty()) actors else fallback.actors,
    mediaType = mediaType.ifBlank { fallback.mediaType },
    collectionType = collectionType.ifBlank { fallback.collectionType },
    seriesId = seriesId ?: fallback.seriesId,
    seriesName = seriesName.ifBlank { fallback.seriesName },
    seasonId = seasonId ?: fallback.seasonId,
    seasonNumber = seasonNumber ?: fallback.seasonNumber,
    episodeNumber = episodeNumber ?: fallback.episodeNumber,
    childCount = childCount ?: fallback.childCount,
    unplayedItemCount = unplayedItemCount ?: fallback.unplayedItemCount,
    resumePositionMs = maxOf(resumePositionMs, fallback.resumePositionMs),
    chapters = if (chapters.isNotEmpty()) chapters else fallback.chapters,
    trickplay = if (trickplay.isNotEmpty()) trickplay else fallback.trickplay,
)

private fun resumeGroupingKey(media: MediaItem): String = when {
    !media.seriesId.isNullOrBlank() -> "series:${media.seriesId}"
    media.isEpisode -> "series:${media.seriesName.takeIf { it.isNotBlank() } ?: media.id}"
    media.isSeries -> "series:${media.id}"
    else -> "item:${media.id}"
}

private fun resumeGroupingKey(entry: ContinueWatchingEntry): String = when {
    !entry.seriesId.isNullOrBlank() -> "series:${entry.seriesId}"
    entry.mediaType.equals("Episode", ignoreCase = true) ->
        "series:${entry.seriesName.takeIf { it.isNotBlank() } ?: entry.id}"
    entry.mediaType.equals("Series", ignoreCase = true) -> "series:${entry.id}"
    else -> "item:${entry.id}"
}
