package com.fivemin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmController {
    const val ACTION_MAIN_FIRE = "com.fivemin.MAIN_FIRE"
    const val ACTION_SNOOZE_FIRE = "com.fivemin.SNOOZE_FIRE"

    private const val REQ_MAIN = 1001
    private const val REQ_SNOOZE = 1002

    private fun alarmManager(c: Context) =
        c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingFor(c: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(c, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            c,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setExact(c: Context, at: Long, pi: PendingIntent) {
        val am = alarmManager(c)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun scheduleMain(c: Context, intervalMillis: Long) {
        val at = System.currentTimeMillis() + intervalMillis
        Prefs.setNextTrigger(c, at)
        setExact(c, at, pendingFor(c, ACTION_MAIN_FIRE, REQ_MAIN))
    }

    fun scheduleSnooze(c: Context, delayMillis: Long) {
        val at = System.currentTimeMillis() + delayMillis
        Prefs.setNextTrigger(c, at)
        setExact(c, at, pendingFor(c, ACTION_SNOOZE_FIRE, REQ_SNOOZE))
    }

    fun cancelSnooze(c: Context) {
        alarmManager(c).cancel(pendingFor(c, ACTION_SNOOZE_FIRE, REQ_SNOOZE))
    }

    fun cancelAll(c: Context) {
        alarmManager(c).cancel(pendingFor(c, ACTION_MAIN_FIRE, REQ_MAIN))
        cancelSnooze(c)
        Prefs.setNextTrigger(c, 0L)
    }
}
