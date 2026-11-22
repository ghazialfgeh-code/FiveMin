package com.fivemin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import java.util.Locale

class AlarmService : Service() {

    companion object {
        const val ACTION_RING = "com.fivemin.RING"
        const val ACTION_STOP_SOUND = "com.fivemin.STOP_SOUND"
        const val ACTION_SNOOZE = "com.fivemin.SNOOZE"
        const val ACTION_CANCEL_SNOOZE = "com.fivemin.CANCEL_SNOOZE"
        const val ACTION_FINAL_STOP = "com.fivemin.FINAL_STOP"

        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_RINGING = "RINGING"
        const val STATUS_SNOOZING = "SNOOZING"
        const val STATUS_STOPPED = "STOPPED"

        const val CHANNEL_STATUS = "fivemin_status"
        const val CHANNEL_ALARM = "fivemin_alarm"
        const val NOTIF_STATUS_ID = 1
        const val NOTIF_ALERT_ID = 2

        const val SNOOZE_MS = 5 * 60_000L

        fun intent(context: Context, action: String): Intent =
            Intent(context, AlarmService::class.java).setAction(action)
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tickerRunning = false

    private val ticker = object : Runnable {
        override fun run() {
            updateStatusNotification()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        vibrator = getVibratorService()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground quickly after startForegroundService()
        startForegroundCompat(buildStatusNotification())

        when (intent?.action) {
            ACTION_RING -> onRing()
            ACTION_STOP_SOUND -> onStopSound()
            ACTION_SNOOZE -> onSnooze()
            ACTION_CANCEL_SNOOZE -> onCancelSnooze()
            ACTION_FINAL_STOP -> onFinalStop()
            else -> {
                if (Prefs.getStatus(this) == STATUS_STOPPED) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        if (Prefs.getStatus(this) != STATUS_STOPPED) {
            startTicker()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTicker()
        releasePlayer()
        super.onDestroy()
    }

    // ---------- Actions ----------

    private fun onRing() {
        Prefs.setStatus(this, STATUS_RINGING)
        startSound()
        startVibration()
        showAlertNotification()
        updateStatusNotification()
    }

    private fun onStopSound() {
        stopSoundAndVibration()
        AlarmController.cancelSnooze(this)
        AlarmController.scheduleMain(this, Prefs.getIntervalMillis(this))
        Prefs.setStatus(this, STATUS_ACTIVE)
        clearAlertNotification()
        updateStatusNotification()
    }

    private fun onSnooze() {
        stopSoundAndVibration()
        AlarmController.scheduleSnooze(this, SNOOZE_MS)
        Prefs.setStatus(this, STATUS_SNOOZING)
        clearAlertNotification()
        updateStatusNotification()
    }

    private fun onCancelSnooze() {
        AlarmController.cancelSnooze(this)
        AlarmController.scheduleMain(this, Prefs.getIntervalMillis(this))
        Prefs.setStatus(this, STATUS_ACTIVE)
        updateStatusNotification()
    }

    private fun onFinalStop() {
        stopSoundAndVibration()
        AlarmController.cancelAll(this)
        Prefs.setStatus(this, STATUS_STOPPED)
        clearAlertNotification()
        stopTicker()
        stopForegroundCompat()
        stopSelf()
    }

    // ---------- Audio and vibration ----------

    private fun startSound() {
        releasePlayer()
        val toneUri: Uri = Prefs.getToneUri(this)?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmService, toneUri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun startVibration() {
        if (!Prefs.isVibrationEnabled(this)) return
        val pattern = longArrayOf(0, 800, 600)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopSoundAndVibration() {
        releasePlayer()
        vibrator?.cancel()
    }

    private fun releasePlayer() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        player = null
    }

    @Suppress("DEPRECATION")
    private fun getVibratorService(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    // ---------- Notifications ----------

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "حالة المؤقت", NotificationManager.IMPORTANCE_LOW).apply {
                description = "الوقت المتبقي وحالة التنبيه"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "تنبيه 5 MIN", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه عند انتهاء المدة"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_STATUS_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_STATUS_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            intent(this, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildStatusNotification(): Notification {
        val status = Prefs.getStatus(this)
        val statusText = when (status) {
            STATUS_RINGING -> "يرن الآن"
            STATUS_SNOOZING -> "غفوة"
            STATUS_ACTIVE -> "نشط"
            else -> "متوقف"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("5 MIN - $statusText")
            .setContentText("المتبقي: ${formatTime(remainingMillis())}")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when (status) {
            STATUS_RINGING -> {
                builder.addAction(0, "غفوة 5 دقائق", actionPendingIntent(ACTION_SNOOZE, 11))
                builder.addAction(0, "إيقاف الصوت", actionPendingIntent(ACTION_STOP_SOUND, 12))
            }
            STATUS_SNOOZING -> {
                builder.addAction(0, "إلغاء الغفوة", actionPendingIntent(ACTION_CANCEL_SNOOZE, 13))
            }
        }
        builder.addAction(0, "إيقاف نهائي", actionPendingIntent(ACTION_FINAL_STOP, 14))

        return builder.build()
    }

    private fun updateStatusNotification() {
        if (Prefs.getStatus(this) == STATUS_STOPPED) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_STATUS_ID, buildStatusNotification())
    }

    private fun showAlertNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("5 MIN - انتهت المدة")
            .setContentText("اضغط لإيقاف الصوت أو اختر الغفوة")
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(0, "غفوة 5 دقائق", actionPendingIntent(ACTION_SNOOZE, 21))
            .addAction(0, "إيقاف الصوت", actionPendingIntent(ACTION_STOP_SOUND, 22))
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ALERT_ID, notification)
    }

    private fun clearAlertNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ALERT_ID)
    }

    // ---------- Helpers ----------

    private fun remainingMillis(): Long {
        val nextFire = Prefs.getNextFireAt(this)
        return if (nextFire <= 0L) 0L else (nextFire - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ENGLISH, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
        }
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        handler.post(ticker)
    }

    private fun stopTicker() {
        tickerRunning = false
        handler.removeCallbacks(ticker)
    }
}
