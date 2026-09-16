package com.clintmaples.broadcastifyscanner.data

data class Feed(
    val feedId: String,
    val name: String,
)

data class FeedMeta(
    val feedId: String,
    val name: String,
    val hlsUrl: String,
)

enum class FeedStatus {
    IDLE,
    LOADING,
    PLAYING,
    MUTED,
    RECONNECTING,
    ERROR,
}

data class FeedUiState(
    val feedId: String,
    val name: String,
    val status: FeedStatus,
    val statusDetail: String = "",
    val muted: Boolean = false,
    val volume: Float = 1f,
    val wantPlay: Boolean = false,
)

data class ScannerUiState(
    val feeds: List<FeedUiState> = emptyList(),
    val masterVolume: Float = 0.8f,
    val keepAwake: Boolean = false,
    val addPanelOpen: Boolean = false,
)

object DefaultFeeds {
    val ALL: List<Feed> = listOf(
        Feed(
            feedId = "14826",
            name = "East Placer and Nevada Counties CAL FIRE NEU - Kings Beach Area",
        ),
        Feed(feedId = "47365", name = "CAL FIRE NEU West"),
        Feed(feedId = "47367", name = "Tahoe National Forest West"),
    )
}
