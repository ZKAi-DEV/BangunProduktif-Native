package com.ubay.bangunproduktif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("EXTRA_ALARM_LABEL") ?: "Waktunya Bangun & Push-Up!"
        val reps = intent.getIntExtra("EXTRA_ALARM_REPS", 10)
        val alarmId = intent.getStringExtra("EXTRA_ALARM_ID") ?: ""

        val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
            putExtra("EXTRA_ALARM_LABEL", label)
            putExtra("EXTRA_ALARM_REPS", reps)
            putExtra("EXTRA_ALARM_ID", alarmId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
