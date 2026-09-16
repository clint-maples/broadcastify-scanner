package com.clintmaples.broadcastifyscanner.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.clintmaples.broadcastifyscanner.data.BroadcastifyClient
import com.clintmaples.broadcastifyscanner.data.BroadcastifyHttp
import com.clintmaples.broadcastifyscanner.data.Feed
import com.clintmaples.broadcastifyscanner.data.FeedStatus
import com.clintmaples.broadcastifyscanner.data.FeedUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class FeedSession(
    initial: Feed,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val client: BroadcastifyClient,
    private val masterVolume: () -> Float,
    private val onChanged: () -> Unit,
    private val onPlayingChanged: () -> Unit,
) {
    var feedId: String = initial.feedId
        private set
    var name: String = initial.name
        private set

    var muted: Boolean = false
        private set
    var volume: Float = 1f
        private set
    var wantPlay: Boolean = false
        private set
    var status: FeedStatus = FeedStatus.IDLE
        private set
    var statusDetail: String = ""
        private set

    private val spectrum = SpectrumAudioProcessor()
    private var player: ExoPlayer? = null
    private var reconnectJob: Job? = null
    private var playJob: Job? = null
    private var playGeneration = 0
    private var spectrumView: WeakReference<SpectrumView> = WeakReference(null)

    init {
        spectrum.listener = SpectrumAudioProcessor.Listener { levels ->
            spectrumView.get()?.setLevels(levels)
        }
    }

    fun uiState(): FeedUiState = FeedUiState(
        feedId = feedId,
        name = name,
        status = status,
        statusDetail = statusDetail,
        muted = muted,
        volume = volume,
        wantPlay = wantPlay,
    )

    fun attachSpectrum(view: SpectrumView?) {
        spectrumView = WeakReference(view)
        if (view == null) return
        if (!wantPlay || status == FeedStatus.IDLE) view.clear()
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        applyGain()
        onChanged()
    }

    fun toggleMute() {
        muted = !muted
        applyGain()
        if (wantPlay && status == FeedStatus.PLAYING && muted) {
            setStatus(FeedStatus.MUTED)
        } else if (wantPlay && status == FeedStatus.MUTED && !muted) {
            setStatus(FeedStatus.PLAYING)
        }
        onChanged()
    }

    fun applyGain() {
        val gain = if (muted) 0f else volume * masterVolume()
        spectrum.outputGain = gain.coerceIn(0f, 1f)
    }

    fun rename(newName: String) {
        if (newName.isNotBlank() && newName != name) {
            name = newName
            onChanged()
        }
    }

    fun play() {
        wantPlay = true
        cancelReconnect()
        val gen = ++playGeneration
        setStatus(FeedStatus.LOADING)
        onChanged()
        playJob?.cancel()
        playJob = scope.launch {
            startOrRefresh(gen, reconnecting = false)
        }
    }

    fun stop() {
        wantPlay = false
        playGeneration++
        playJob?.cancel()
        cancelReconnect()
        releasePlayer()
        spectrumView.get()?.clear()
        setStatus(FeedStatus.IDLE)
        onPlayingChanged()
        onChanged()
    }

    fun reconnect() {
        if (!wantPlay) {
            play()
            return
        }
        cancelReconnect()
        val gen = ++playGeneration
        setStatus(FeedStatus.RECONNECTING)
        onChanged()
        playJob?.cancel()
        playJob = scope.launch {
            startOrRefresh(gen, reconnecting = true)
        }
    }

    fun dispose() {
        stop()
        spectrum.listener = null
    }

    private suspend fun startOrRefresh(gen: Int, reconnecting: Boolean) {
        try {
            val meta = withContext(Dispatchers.IO) { client.fetchFeedMeta(feedId) }
            if (gen != playGeneration || !wantPlay) return
            if (meta.name.isNotBlank() && meta.name != name) {
                name = meta.name
            }
            val exo = ensurePlayer()
            val item = MediaItem.Builder()
                .setUri(meta.hlsUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(LIVE_OFFSET_MS)
                        .build(),
                )
                .build()
            exo.setMediaItem(item)
            exo.prepare()
            exo.playWhenReady = true
            applyGain()
            onPlayingChanged()
            onChanged()
        } catch (e: Exception) {
            if (gen != playGeneration || !wantPlay) return
            val msg = (e.message ?: "failed").take(48)
            setStatus(FeedStatus.ERROR, msg)
            onChanged()
            scheduleReconnect()
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setOffloadMode(DefaultAudioSink.OFFLOAD_MODE_DISABLED)
                    .setAudioProcessors(spectrum)
                    .build()
            }
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BroadcastifyHttp.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to BroadcastifyHttp.REFERER,
                    "Origin" to BroadcastifyHttp.ORIGIN,
                    "Accept" to "*/*",
                ),
            )
        val exo = ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setHandleAudioBecomingNoisy(false)
            .build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus= */ false,
        )
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!wantPlay) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (status != FeedStatus.ERROR) {
                            setStatus(if (reconnectingHint()) FeedStatus.RECONNECTING else FeedStatus.LOADING)
                            onChanged()
                        }
                    }
                    Player.STATE_READY -> {
                        setStatus(if (muted) FeedStatus.MUTED else FeedStatus.PLAYING)
                        onChanged()
                    }
                    Player.STATE_ENDED -> {
                        if (wantPlay) scheduleReconnect()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!wantPlay) return
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player?.seekToDefaultPosition()
                    player?.prepare()
                    return
                }
                val expired = isTokenFailure(error)
                setStatus(FeedStatus.ERROR, if (expired) "token expired" else error.errorCodeName.take(40))
                onChanged()
                scheduleReconnect()
            }
        })
        player = exo
        return exo
    }

    private fun reconnectingHint(): Boolean = status == FeedStatus.RECONNECTING

    private fun isTokenFailure(error: PlaybackException): Boolean {
        val code = error.errorCode
        if (code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            code == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ||
            code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
        ) {
            return true
        }
        val msg = error.message.orEmpty() + (error.cause?.message.orEmpty())
        return msg.contains("403") || msg.contains("401") || msg.contains("jwt", ignoreCase = true)
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        if (!wantPlay) return
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (wantPlay && status != FeedStatus.IDLE) {
                reconnect()
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun setStatus(next: FeedStatus, detail: String = "") {
        status = next
        statusDetail = detail
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 4_000L
        private const val LIVE_OFFSET_MS = 8_000L
    }
}
