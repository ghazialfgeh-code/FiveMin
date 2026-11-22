package com.fivemin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != ACTION_RING) return
        Prefs.setPhase(c, Prefs.PHASE_RINGING)
        Prefs.setNextTrigger(c, 0L)
        AlarmService.send(c, AlarmService.ACTION_RING)
    }

    companion object {
        const val ACTION_RING = "com.fivemin.RING"
    }
}
