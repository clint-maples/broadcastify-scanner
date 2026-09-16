package com.clintmaples.broadcastifyscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.clintmaples.broadcastifyscanner.data.FeedStatus
import com.clintmaples.broadcastifyscanner.data.FeedUiState
import com.clintmaples.broadcastifyscanner.data.ScannerUiState
import com.clintmaples.broadcastifyscanner.player.SpectrumView
import com.clintmaples.broadcastifyscanner.ui.theme.Accent
import com.clintmaples.broadcastifyscanner.ui.theme.AccentDim
import com.clintmaples.broadcastifyscanner.ui.theme.Bg
import com.clintmaples.broadcastifyscanner.ui.theme.BgCard
import com.clintmaples.broadcastifyscanner.ui.theme.BgElev
import com.clintmaples.broadcastifyscanner.ui.theme.Border
import com.clintmaples.broadcastifyscanner.ui.theme.Danger
import com.clintmaples.broadcastifyscanner.ui.theme.Info
import com.clintmaples.broadcastifyscanner.ui.theme.Muted
import com.clintmaples.broadcastifyscanner.ui.theme.TextPrimary
import com.clintmaples.broadcastifyscanner.ui.theme.Warn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    state: ScannerUiState,
    onPlayAll: () -> Unit,
    onStopAll: () -> Unit,
    onPlay: (String) -> Unit,
    onStop: (String) -> Unit,
    onMute: (String) -> Unit,
    onReconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onFeedVolume: (String, Float) -> Unit,
    onMasterVolume: (Float) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onToggleAdd: () -> Unit,
    onAddFeed: (String, String) -> Unit,
    onAttachSpectrum: (String, SpectrumView?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        TopBar(
            masterVolume = state.masterVolume,
            keepAwake = state.keepAwake,
            addOpen = state.addPanelOpen,
            onPlayAll = onPlayAll,
            onStopAll = onStopAll,
            onMasterVolume = onMasterVolume,
            onKeepAwake = onKeepAwake,
            onToggleAdd = onToggleAdd,
        )
        Text(
            text = "Scanners are often silent between transmissions. Free feeds may play a short Broadcastify preroll first. Spectrum still moves when muted.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorBanner)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.addPanelOpen) {
            AddFeedPanel(onAddFeed = onAddFeed)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.feeds, key = { it.feedId }) { feed ->
                FeedCard(
                    feed = feed,
                    onPlay = { onPlay(feed.feedId) },
                    onStop = { onStop(feed.feedId) },
                    onMute = { onMute(feed.feedId) },
                    onReconnect = { onReconnect(feed.feedId) },
                    onRemove = { onRemove(feed.feedId) },
                    onVolume = { onFeedVolume(feed.feedId, it) },
                    onAttachSpectrum = { view -> onAttachSpectrum(feed.feedId, view) },
                )
            }
        }
    }
}

private val ColorBanner = androidx.compose.ui.graphics.Color(0xFF12171E)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopBar(
    masterVolume: Float,
    keepAwake: Boolean,
    addOpen: Boolean,
    onPlayAll: () -> Unit,
    onStopAll: () -> Unit,
    onMasterVolume: (Float) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onToggleAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgElev)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◉", color = Accent, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Broadcastify Scanner",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton("Play all", onPlayAll)
            GhostButton("Stop all", onStopAll)
            GhostButton(if (addOpen) "Close" else "＋ Feed", onToggleAdd)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Awake", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = keepAwake,
                    onCheckedChange = onKeepAwake,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AccentDim,
                        checkedThumbColor = Accent,
                    ),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text("Master", color = Muted, fontSize = 12.sp)
            Slider(
                value = masterVolume,
                onValueChange = onMasterVolume,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = sliderColors(),
            )
        }
    }
}

@Composable
private fun AddFeedPanel(onAddFeed: (String, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = Accent,
        unfocusedBorderColor = Border,
        focusedLabelColor = Muted,
        unfocusedLabelColor = Muted,
        cursorColor = Accent,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorBanner)
            .padding(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = id,
                onValueChange = { id = it.filter { ch -> ch.isDigit() }.take(12) },
                label = { Text("Feed ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.weight(1.4f),
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Add feed") {
            if (id.isNotBlank()) {
                onAddFeed(id, name)
                id = ""
                name = ""
            }
        }
        Text(
            "Tokens are scraped fresh from the Broadcastify popout on each play/reconnect. Never hardcoded.",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedCard(
    feed: FeedUiState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onMute: () -> Unit,
    onReconnect: () -> Unit,
    onRemove: () -> Unit,
    onVolume: (Float) -> Unit,
    onAttachSpectrum: (SpectrumView?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    feed.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("ID ${feed.feedId}", color = Muted, fontSize = 11.sp)
            }
            StatusChip(feed.status, feed.statusDetail)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallButton("Play", onPlay, enabled = !feed.wantPlay || feed.status == FeedStatus.ERROR)
            SmallButton("Stop", onStop, enabled = feed.wantPlay)
            SmallButton(if (feed.muted) "Unmute" else "Mute", onMute, tinted = feed.muted)
            SmallButton("Reconnect", onReconnect)
            TextButton(onClick = onRemove) {
                Text("✕", color = Muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vol", color = Muted, fontSize = 12.sp)
            Slider(
                value = feed.volume,
                onValueChange = onVolume,
                modifier = Modifier.weight(1f),
                colors = sliderColors(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF0A0E14))
                .border(1.dp, androidx.compose.ui.graphics.Color(0xFF1A2332), RoundedCornerShape(6.dp))
                .padding(4.dp),
        ) {
            AndroidView(
                factory = { context ->
                    SpectrumView(context).also { onAttachSpectrum(it) }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> onAttachSpectrum(view) },
            )
            DisposableEffect(feed.feedId) {
                onDispose { onAttachSpectrum(null) }
            }
        }
    }
}

@Composable
private fun StatusChip(status: FeedStatus, detail: String) {
    val (fg, bg) = when (status) {
        FeedStatus.PLAYING -> Accent to androidx.compose.ui.graphics.Color(0x1F3FB950)
        FeedStatus.LOADING, FeedStatus.RECONNECTING -> Warn to androidx.compose.ui.graphics.Color(0x1FD29922)
        FeedStatus.ERROR -> Danger to androidx.compose.ui.graphics.Color(0x24F85149)
        FeedStatus.MUTED -> Info to androidx.compose.ui.graphics.Color(0x1A58A6FF)
        FeedStatus.IDLE -> Muted to androidx.compose.ui.graphics.Color.Transparent
    }
    val label = buildString {
        append(status.name.lowercase())
        if (detail.isNotBlank()) append(": ").append(detail)
    }
    Text(
        text = label,
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AccentDim, contentColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun GhostButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, fontSize = 13.sp) }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit, enabled: Boolean = true, tinted: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (tinted) Info else TextPrimary,
            disabledContentColor = Muted,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (tinted) Info else Border),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(label, fontSize = 12.sp) }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Accent,
    activeTrackColor = Accent,
    inactiveTrackColor = Border,
)
