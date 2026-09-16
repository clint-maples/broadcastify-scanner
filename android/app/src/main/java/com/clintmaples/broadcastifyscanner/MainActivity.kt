package com.clintmaples.broadcastifyscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clintmaples.broadcastifyscanner.ui.ScannerScreen
import com.clintmaples.broadcastifyscanner.ui.theme.ScannerTheme

class MainActivity : ComponentActivity() {
    private val controller by lazy { (application as ScannerApp).controller }

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* notification is optional; playback still works */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        maybeRequestNotifications()

        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            DisposableEffect(state.keepAwake) {
                if (state.keepAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            ScannerTheme {
                ScannerScreen(
                    state = state,
                    onPlayAll = controller::playAll,
                    onStopAll = controller::stopAll,
                    onPlay = controller::play,
                    onStop = controller::stop,
                    onMute = controller::toggleMute,
                    onReconnect = controller::reconnect,
                    onRemove = controller::removeFeed,
                    onFeedVolume = controller::setFeedVolume,
                    onMasterVolume = controller::setMasterVolume,
                    onKeepAwake = controller::setKeepAwake,
                    onToggleAdd = { controller.setAddPanelOpen(!state.addPanelOpen) },
                    onAddFeed = { id, name -> controller.addFeed(id, name) },
                    onAttachSpectrum = controller::attachSpectrum,
                )
            }
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
