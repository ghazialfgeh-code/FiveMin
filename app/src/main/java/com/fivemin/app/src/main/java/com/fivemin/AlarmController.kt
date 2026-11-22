package com.fivemin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmController {
    const val RC_MAIN = 1
    const val RC_SNOOZE = 2
    const val SNOOZE_MS = 5 * 60_000L

    private fun alarmManager(c: Context) =
        c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun ringIntent(c: Context, requestCode: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RING)
        return PendingIntent.getBroadcast(
            c, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(c: Context, requestCode: Int, triggerAt: Long) {
        val am = alarmManager(c)
        val pi = ringIntent(c, requestCode)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(c: Context, requestCode: Int) {
        alarmManager(c).cancel(ringIntent(c, requestCode))
    }

    fun startCountdown(c: Context) {
        val t = System.currentTimeMillis() + Prefs.duration(c) * 60_000L
        cancel(c, RC_SNOOZE)
        schedule(c, RC_MAIN, t)
        Prefs.setActive(c, true)
        Prefs.setPhase(c, Prefs.PHASE_COUNTDOWN)
        Prefs.setNextTrigger(c, t)
        FiveMinWidget.refreshAll(c)
    }

    fun startSnooze(c: Context) {
        val t = System.currentTimeMillis() + SNOOZE_MS
        schedule(c, RC_SNOOZE, t)
        Prefs.setPhase(c, Prefs.PHASE_SNOOZE)
        Prefs.setNextTrigger(c, t)
        FiveMinWidget.refreshAll(c)
    }

    fun finalStop(c: Context) {
        cancel(c, RC_MAIN)
        cancel(c, RC_SNOOZE)
        Prefs.setActive(c, false)
        Prefs.setPhase(c, Prefs.PHASE_NONE)
        Prefs.setNextTrigger(c, 0L)
        FiveMinWidget.refreshAll(c)
    }
}
