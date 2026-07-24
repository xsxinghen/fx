package com.github.king

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat

class MediaService : Service() {
    private var player: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentTitle = "缓冲中..."
    private var githubBitmap: Bitmap? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GK_WIFI_LOCK")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel("media_channel", "媒体播放", NotificationManager.IMPORTANCE_LOW)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }
            
            mediaSession = MediaSessionCompat(this, "MediaService").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() { resumeAudio() }
                    override fun onPause() { pauseAudio() }
                    override fun onStop() { stopAudio() }
                    override fun onSkipToNext() { sendMediaCommand("NEXT") }
                    override fun onSkipToPrevious() { sendMediaCommand("PREV") }
                    // 核心修复1：接管系统卡片/灵动岛的进度条拖拽事件
                    override fun onSeekTo(pos: Long) {
                        try {
                            player?.let {
                                it.seekTo(pos.toInt())
                                updateNotification(it.isPlaying, false)
                            }
                        } catch (e: Throwable) {}
                    }
                })
                isActive = true
            }

            try {
                val pathStr = "M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
                val path = androidx.core.graphics.PathParser.createPathFromPathData(pathStr)
                githubBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(githubBitmap!!)
                canvas.drawColor(Color.parseColor("#F8FAFC"))
                val paint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.scale(512f / 24f, 512f / 24f)
                canvas.drawPath(path, paint)
            } catch (e: Throwable) {
                githubBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(githubBitmap!!)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
                canvas.drawCircle(256f, 256f, 256f, paint)
                paint.color = Color.WHITE
                paint.textSize = 120f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("GK", 256f, 290f, paint)
            }
        } catch (e: Throwable) {}
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseAudio()
                            AudioManager.AUDIOFOCUS_GAIN -> resumeAudio()
                        }
                    }.build()
                audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus({ focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseAudio()
                        AudioManager.AUDIOFOCUS_GAIN -> resumeAudio()
                    }
                }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Throwable) {
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Throwable) {}
    }

    private fun sendMediaCommand(cmd: String) {
        try {
            val intent = Intent("GK_MEDIA_ACTION")
            intent.putExtra("cmd", cmd)
            intent.setPackage(packageName)
            sendBroadcast(intent)
        } catch (e: Throwable) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                "PLAY" -> {
                    val url = intent.getStringExtra("url") ?: return START_STICKY
                    currentTitle = intent.getStringExtra("title") ?: "GK Audio"
                    updateNotification(isPlaying = false, isBuffering = true)
                    playUrl(url)
                }
                "PAUSE" -> pauseAudio()
                "RESUME" -> resumeAudio()
                "STOP" -> stopAudio()
                "NEXT" -> sendMediaCommand("NEXT")
                "PREV" -> sendMediaCommand("PREV")
            }
        } catch (e: Throwable) {}
        return START_STICKY
    }

    private fun playUrl(url: String) {
        try {
            player?.release()
            requestAudioFocus()
            try {
                if (wifiLock?.isHeld == false) wifiLock?.acquire()
            } catch (e: Throwable) {}

            player = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener {
                    it.start()
                    updateNotification(isPlaying = true, isBuffering = false)
                }
                setOnCompletionListener {
                    sendMediaCommand("NEXT")
                }
                setOnErrorListener { _, _, _ ->
                    currentTitle = "网络或资源异常"
                    updateNotification(isPlaying = false, isBuffering = false)
                    true
                }
            }
        } catch (e: Throwable) {
            currentTitle = "解析资源失败"
            updateNotification(isPlaying = false, isBuffering = false)
        }
    }

    private fun pauseAudio() {
        try {
            player?.let {
                if (it.isPlaying) {
                    it.pause()
                    updateNotification(isPlaying = false, isBuffering = false)
                }
            }
        } catch (e: Throwable) {}
    }

    private fun resumeAudio() {
        try {
            if (requestAudioFocus()) {
                player?.let {
                    if (!it.isPlaying) {
                        it.start()
                        updateNotification(isPlaying = true, isBuffering = false)
                    }
                }
            }
        } catch (e: Throwable) {}
    }

    private fun stopAudio() {
        try {
            abandonAudioFocus()
            try {
                if (wifiLock?.isHeld == true) wifiLock?.release()
            } catch (e: Throwable) {}
            player?.release()
            player = null
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Throwable) {}
        stopSelf()
    }

    private fun updateNotification(isPlaying: Boolean, isBuffering: Boolean) {
        try {
            var position = 0L
            var duration = 0L
            if (!isBuffering && player != null) {
                position = player!!.currentPosition.toLong()
                duration = player!!.duration.toLong()
            }

            val state = when {
                isBuffering -> PlaybackStateCompat.STATE_BUFFERING
                isPlaying -> PlaybackStateCompat.STATE_PLAYING
                else -> PlaybackStateCompat.STATE_PAUSED
            }

            // 核心修复2：向系统声明我们支持 ACTION_SEEK_TO (允许拖拽进度条)
            mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(state, position, if (isPlaying && !isBuffering) 1f else 0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO)
                .build())

            val displayArtist = if (isBuffering) "正在网络缓冲..." else "GK Player"
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, displayArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                
            if (githubBitmap != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, githubBitmap)
            }
            mediaSession?.setMetadata(metadataBuilder.build())

            val prevIntent = Intent(this, MediaService::class.java).setAction("PREV")
            val pPrevIntent = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val actionType = if (isPlaying) "PAUSE" else "RESUME"
            val pauseIntent = Intent(this, MediaService::class.java).setAction(actionType)
            val pIntent = PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val nextIntent = Intent(this, MediaService::class.java).setAction("NEXT")
            val pNextIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val actionTitle = if (isPlaying) "Pause" else "Play"

            val appIconRes = applicationInfo.icon
            val safeSmallIcon = if (appIconRes != 0) appIconRes else android.R.drawable.ic_media_play

            val notification = NotificationCompat.Builder(this, "media_channel")
                .setSmallIcon(safeSmallIcon)
                .setLargeIcon(githubBitmap)
                .setContentTitle(currentTitle)
                .setContentText(displayArtist)
                .setOngoing(isPlaying || isBuffering)
                .addAction(android.R.drawable.ic_media_previous, "Previous", pPrevIntent)
                .addAction(iconRes, actionTitle, pIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", pNextIntent)
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2))
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Throwable) {}
    }

    override fun onDestroy() {
        try {
            abandonAudioFocus()
            try {
                if (wifiLock?.isHeld == true) wifiLock?.release()
            } catch (e: Throwable) {}
            player?.release()
            mediaSession?.release()
            githubBitmap?.recycle()
        } catch (e: Throwable) {}
        super.onDestroy()
    }
}
