package com.clintmaples.broadcastifyscanner

import android.app.Application
import com.clintmaples.broadcastifyscanner.data.BroadcastifyClient
import com.clintmaples.broadcastifyscanner.data.Feed
import com.clintmaples.broadcastifyscanner.data.FeedStore
import com.clintmaples.broadcastifyscanner.data.ScannerUiState
import com.clintmaples.broadcastifyscanner.player.FeedSession
import com.clintmaples.broadcastifyscanner.player.PlaybackService
import com.clintmaples.broadcastifyscanner.player.SpectrumView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScannerController(private val app: Application) {
    private val store = FeedStore(app)
    private val client = BroadcastifyClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions = linkedMapOf<String, FeedSession>()

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    init {
        store.loadFeeds().forEach { addSession(it, persist = false) }
        _state.update {
            it.copy(
                masterVolume = store.loadMasterVolume(),
                keepAwake = store.loadKeepAwake(),
            )
        }
        publish()
    }

    fun playAll() {
        sessions.values.forEach { session ->
            if (!session.wantPlay) session.play()
            else session.reconnect()
        }
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
    }

    fun play(feedId: String) {
        sessions[feedId]?.play()
    }

    fun stop(feedId: String) {
        sessions[feedId]?.stop()
    }

    fun reconnect(feedId: String) {
        sessions[feedId]?.reconnect()
    }

    fun toggleMute(feedId: String) {
        sessions[feedId]?.toggleMute()
    }

    fun setFeedVolume(feedId: String, volume: Float) {
        sessions[feedId]?.setVolume(volume)
    }

    fun setMasterVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        store.saveMasterVolume(v)
        _state.update { it.copy(masterVolume = v) }
        sessions.values.forEach { it.applyGain() }
        publish()
    }

    fun setKeepAwake(value: Boolean) {
        store.saveKeepAwake(value)
        _state.update { it.copy(keepAwake = value) }
    }

    fun setAddPanelOpen(open: Boolean) {
        _state.update { it.copy(addPanelOpen = open) }
    }

    fun addFeed(rawId: String, rawName: String?) {
        val id = rawId.filter { it.isDigit() }
        if (id.isEmpty() || sessions.containsKey(id)) return
        val name = rawName?.trim().orEmpty().ifBlank { "Feed $id" }
        addSession(Feed(id, name), persist = true)
        publish()
        if (rawName.isNullOrBlank()) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { client.fetchFeedMeta(id) } }
                    .onSuccess { meta -> sessions[id]?.rename(meta.name) }
            }
        }
    }

    fun removeFeed(feedId: String) {
        sessions.remove(feedId)?.dispose()
        persistFeeds()
        syncService()
        publish()
    }

    fun attachSpectrum(feedId: String, view: SpectrumView?) {
        sessions[feedId]?.attachSpectrum(view)
    }

    fun playingCount(): Int = sessions.values.count { it.wantPlay }

    private fun addSession(feed: Feed, persist: Boolean) {
        if (sessions.containsKey(feed.feedId)) return
        val session = FeedSession(
            initial = feed,
            appContext = app,
            scope = scope,
            client = client,
            masterVolume = { _state.value.masterVolume },
            onChanged = { publish() },
            onPlayingChanged = { syncService() },
        )
        sessions[feed.feedId] = session
        if (persist) persistFeeds()
    }

    private fun persistFeeds() {
        store.saveFeeds(sessions.values.map { Feed(it.feedId, it.name) })
    }

    private fun publish() {
        persistFeeds()
        _state.update { current ->
            current.copy(feeds = sessions.values.map { it.uiState() })
        }
    }

    private fun syncService() {
        val n = playingCount()
        if (n > 0) {
            PlaybackService.start(app, n)
        } else {
            PlaybackService.stop(app)
        }
    }
}
