 package com.fivemin

import android.content.Context

object Prefs {
    private const val FILE = "fivemin_prefs"

    const val PHASE_NONE = "none"
    const val PHASE_COUNTDOWN = "countdown"
    const val PHASE_RINGING = "ringing"
    const val PHASE_SNOOZE = "snooze"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun duration(c: Context): Long = sp(c).getLong("duration", 45L)
    fun setDuration(c: Context, minutes: Long) = sp(c).edit().putLong("duration", minutes).apply()

    fun active(c: Context): Boolean = sp(c).getBoolean("active", false)
    fun setActive(c: Context, v: Boolean) = sp(c).edit().putBoolean("active", v).apply()

    fun phase(c: Context): String = sp(c).getString("phase", PHASE_NONE) ?: PHASE_NONE
    fun setPhase(c: Context, p: String) = sp(c).edit().putString("phase", p).apply()

    fun nextTrigger(c: Context): Long = sp(c).getLong("next", 0L)
    fun setNextTrigger(c: Context, t: Long) = sp(c).edit().putLong("next", t).apply()

    fun toneUri(c: Context): String? = sp(c).getString("tone", null)
    fun setToneUri(c: Context, uri: String?) = sp(c).edit().putString("tone", uri).apply()

    fun vibrate(c: Context): Boolean = sp(c).getBoolean("vibrate", true)
    fun setVibrate(c: Context, v: Boolean) = sp(c).edit().putBoolean("vibrate", v).apply()

    // Aliases used by MainActivity.kt
    fun getStatus(c: Context): String = sp(c).getString("status", "STOPPED") ?: "STOPPED"
    fun setStatus(c: Context, s: String) = sp(c).edit().putString("status", s).apply()

    fun getNextFireAt(c: Context): Long = nextTrigger(c)
    fun getToneUri(c: Context): String? = toneUri(c)
    fun setIntervalMillis(c: Context, millis: Long) = setDuration(c, millis / 60_000L)
    fun setVibrationEnabled(c: Context, v: Boolean) = setVibrate(c, v)
}
