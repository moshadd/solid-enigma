package com.example.reminder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import java.util.Date
import kotlin.random.Random

class ReminderService : Service() {

    companion object {
        const val ACTION_START = "com.example.reminder.action.START"
        const val ACTION_STOP = "com.example.reminder.action.STOP"

        private const val CHANNEL_ID = "reminder_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "reminder_prefs"
        private const val KEY_RUNNING = "running"

        private const val MIN_DELAY_MS = 3 * 60 * 1000L
        private const val MAX_DELAY_MS = 5 * 60 * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null

    private val remindTask = object : Runnable {
        override fun run() {
            if (!running) return
            playSound()
            scheduleNext()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ReminderService::WakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopReminder()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> startReminder()

            else -> {
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                if (prefs.getBoolean(KEY_RUNNING, false)) {
                    startReminder()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("WakelockTimeout")
    private fun startReminder() {
        if (running) {
            updateNotification("提醒运行中，每 3~5 分钟响一次")
            return
        }

        running = true
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, true)
            .apply()

        startForeground(
            NOTIFICATION_ID,
            buildNotification("提醒已启动，每 3~5 分钟响一次")
        )

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }

        scheduleNext()
    }

    private fun scheduleNext() {
        val delayMs = Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)
        val nextTime = System.currentTimeMillis() + delayMs
        val timeText = DateFormat.getTimeFormat(this).format(Date(nextTime))

        updateNotification("下次响铃约 $timeText（${delayMs / 1000} 秒后）")

        handler.removeCallbacks(remindTask)
        handler.postDelayed(remindTask, delayMs)
    }

    private fun playSound() {
        try {
            ringtone?.stop()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (_: Exception) {
            try {
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
            } catch (_: Exception) {
            }
        }
    }

    private fun stopReminder() {
        running = false
        handler.removeCallbacks(remindTask)

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, false)
            .apply()

        ringtone?.stop()
        ringtone = null

        toneGenerator?.release()
        toneGenerator = null

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopReminder()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "定时提醒",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "每 3~5 分钟提醒一次"
                setSound(null, null)
                enableVibration(false)
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("定时提示音")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }
}