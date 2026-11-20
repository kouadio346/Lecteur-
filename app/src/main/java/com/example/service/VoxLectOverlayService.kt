package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.model.PlaybackState
import com.example.ui.theme.VoxLectTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class VoxLectOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var engine: VoxLectEngine
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var savedBubbleX: Int = 24
    private var savedBubbleY: Int = 280
    private var currentOverlayMode: OverlayMode = OverlayMode.BUBBLE

    private enum class OverlayMode {
        BUBBLE,
        PANEL,
        PICKER
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        engine = VoxLectEngine.getInstance(applicationContext)
        createNotificationChannel()
        startForegroundCompat(buildNotification(PlaybackState.Idle))

        serviceScope.launch {
            combine(
                engine.isOverlayExpanded,
                engine.isScreenPickerActive
            ) { expanded, picker ->
                when {
                    picker -> OverlayMode.PICKER
                    expanded -> OverlayMode.PANEL
                    else -> OverlayMode.BUBBLE
                }
            }.collect { mode ->
                updateOverlayLayoutMode(mode)
            }
        }

        serviceScope.launch {
            engine.playbackState.collect { state ->
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine.stopPlayback()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                engine.togglePlayPause()
            }
            ACTION_STOP_PLAYBACK -> {
                engine.stopPlayback()
            }
            ACTION_OPEN_SELECTOR -> {
                ensureOverlayAttached()
                engine.openScreenSelector()
            }
            else -> {
                ensureOverlayAttached()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground service and floating overlay active when the user leaves or swipes the app.
        if (Settings.canDrawOverlays(this)) {
            ensureOverlayAttached()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (currentOverlayMode == OverlayMode.BUBBLE) {
            val view = overlayView ?: return
            val lp = layoutParams ?: return
            val (clampedX, clampedY) = clampBubbleCoordinates(lp.x, lp.y)
            lp.x = clampedX
            lp.y = clampedY
            savedBubbleX = clampedX
            savedBubbleY = clampedY
            runCatching { windowManager.updateViewLayout(view, lp) }
        }
    }

    private fun ensureOverlayAttached() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (overlayView != null) {
            engine.setOverlayRunning(true)
            return
        }

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        lifecycleOwner = owner

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedBubbleX
            y = savedBubbleY
        }
        layoutParams = params

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                VoxLectTheme(darkTheme = true) {
                    VoxLectOverlayRoot(
                        engine = engine,
                        onDragBubble = { dx, dy ->
                            if (currentOverlayMode == OverlayMode.BUBBLE) {
                                this@VoxLectOverlayService.layoutParams?.let { lp ->
                                    val (clampedX, clampedY) = clampBubbleCoordinates(
                                        lp.x + dx.toInt(),
                                        lp.y + dy.toInt()
                                    )
                                    lp.x = clampedX
                                    lp.y = clampedY
                                    savedBubbleX = clampedX
                                    savedBubbleY = clampedY
                                    runCatching {
                                        windowManager.updateViewLayout(this@apply, lp)
                                    }
                                }
                            }
                        },
                        onOpenAccessibilitySettings = {
                            val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(settingsIntent)
                        },
                        onCloseService = {
                            engine.stopPlayback()
                            stopSelf()
                        }
                    )
                }
            }
        }
        overlayView = composeView
        runCatching {
            windowManager.addView(composeView, params)
            engine.setOverlayRunning(true)
        }.onFailure {
            engine.setOverlayRunning(false)
            stopSelf()
        }
    }

    private fun updateOverlayLayoutMode(mode: OverlayMode) {
        val view = overlayView ?: return
        val lp = layoutParams ?: return
        if (currentOverlayMode == mode) return

        if (currentOverlayMode == OverlayMode.BUBBLE) {
            savedBubbleX = lp.x
            savedBubbleY = lp.y
        }
        currentOverlayMode = mode

        when (mode) {
            OverlayMode.BUBBLE -> {
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = savedBubbleX
                lp.y = savedBubbleY
            }
            OverlayMode.PANEL -> {
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                lp.gravity = Gravity.CENTER
                lp.x = 0
                lp.y = 0
            }
            OverlayMode.PICKER -> {
                lp.width = WindowManager.LayoutParams.MATCH_PARENT
                lp.height = WindowManager.LayoutParams.MATCH_PARENT
                lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = 0
                lp.y = 0
            }
        }
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun clampBubbleCoordinates(x: Int, y: Int): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        val bubblePx = (66 * density).toInt()
        val (screenW, screenH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
        val maxX = (screenW - bubblePx).coerceAtLeast(0)
        val maxY = (screenH - bubblePx).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }

    private fun buildNotification(state: PlaybackState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val selectorIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoxLectOverlayService::class.java).apply { action = ACTION_OPEN_SELECTOR },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, VoxLectOverlayService::class.java).apply { action = ACTION_TOGGLE_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopServiceIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, VoxLectOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when (state) {
            is PlaybackState.Idle -> getString(R.string.notification_text_idle)
            is PlaybackState.Translating -> getString(R.string.notification_text_translating)
            is PlaybackState.Speaking ->
                "${getString(R.string.notification_text_speaking)} (${state.currentChunk}/${state.totalChunks})"
            is PlaybackState.Paused ->
                "En pause (${state.currentChunk}/${state.totalChunks})"
            is PlaybackState.Error -> state.message
        }

        val playPauseLabel = if (state is PlaybackState.Speaking) {
            getString(R.string.action_pause)
        } else {
            getString(R.string.action_resume)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notification_title_active))
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.action_open_selector), selectorIntent)
            .addAction(0, playPauseLabel, playPauseIntent)
            .addAction(0, getString(R.string.action_stop), stopServiceIntent)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        engine.setOverlayRunning(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.example.action.SHOW_OVERLAY"
        const val ACTION_STOP = "com.example.action.STOP_OVERLAY"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_STOP_PLAYBACK = "com.example.action.STOP_PLAYBACK"
        const val ACTION_OPEN_SELECTOR = "com.example.action.OPEN_SELECTOR"

        private const val CHANNEL_ID = "voxlect_overlay_channel"
        private const val NOTIFICATION_ID = 4102
    }
}
