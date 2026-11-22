 package com.fivemin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            AlarmController.ACTION_MAIN_FIRE,
            AlarmController.ACTION_SNOOZE_FIRE -> {
                Prefs.setPhase(c, Prefs.PHASE_RINGING)
                ContextCompat.startForegroundService(
                    c,
                    AlarmService.intent(c, AlarmService.ACTION_RING)
                )
            }
        }
    }
}
