package com.ubay.bangunproduktif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class AlarmSoundService : Service() {
    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra("EXTRA_ALARM_LABEL") ?: "Waktunya Bangun!"
        val reps = intent?.getIntExtra("EXTRA_ALARM_REPS", 10) ?: 10
        val alarmId = intent?.getStringExtra("EXTRA_ALARM_ID") ?: ""

        createChannel()

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("TRIGGER_CHALLENGE", true)
            putExtra("EXTRA_ALARM_LABEL", label)
            putExtra("EXTRA_ALARM_REPS", reps)
            putExtra("EXTRA_ALARM_ID", alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            9001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Target $reps Push-Up untuk mematikan alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        startForeground(1001, notif)

        // Also bring MainActivity straight to foreground if screen is awake
        try {
            startActivity(fullScreenIntent)
        } catch (_: Exception) {}

        startRingtoneAndVibration()
        return START_STICKY
    }

    private fun startRingtoneAndVibration() {
        try {
            val prefs = getSharedPreferences("bangunproduktif_prefs", Context.MODE_PRIVATE)
            val customUriStr = prefs.getString("custom_ringtone_uri", null)
            val uri: Uri = if (!customUriStr.isNullOrBlank()) {
                Uri.parse(customUriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        } catch (_: Exception) {}

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 700, 300, 700, 300)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Alarm Push-Up BangunProduktif",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm berbunyi meskipun aplikasi ditutup atau layar terkunci"
                setBypassDnd(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "bp_native_alarm_channel"
    }
}
