package com.med.shortscounter

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Sayım verilerini ve ayarları SharedPreferences içinde tutar. */
object Stats {
    const val PREFS_STATS = "stats"
    const val PREFS_SETTINGS = "settings"
    const val PREFS_DEBUG = "debug"

    const val APP_IG = "ig"
    const val APP_YT = "yt"

    private const val MAX_LOG_LINES = 60

    fun stats(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)

    fun settings(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    private fun debugPrefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS_DEBUG, Context.MODE_PRIVATE)

    fun today(): String = LocalDate.now().toString()

    private fun dayKey(date: String, app: String? = null) =
        if (app == null) "d_$date" else "d_${date}_$app"

    fun add(c: Context, app: String, n: Int) {
        if (n <= 0) return
        val p = stats(c)
        val d = today()
        p.edit()
            .putInt(dayKey(d), p.getInt(dayKey(d), 0) + n)
            .putInt(dayKey(d, app), p.getInt(dayKey(d, app), 0) + n)
            .putInt("total", p.getInt("total", 0) + n)
            .putInt("total_$app", p.getInt("total_$app", 0) + n)
            .apply()
    }

    fun day(c: Context, date: String, app: String? = null): Int =
        stats(c).getInt(dayKey(date, app), 0)

    fun total(c: Context, app: String? = null): Int =
        stats(c).getInt(if (app == null) "total" else "total_$app", 0)

    fun resetToday(c: Context) {
        val p = stats(c)
        val d = today()
        val ig = day(c, d, APP_IG)
        val yt = day(c, d, APP_YT)
        p.edit()
            .putInt("total", (total(c) - ig - yt).coerceAtLeast(0))
            .putInt("total_$APP_IG", (total(c, APP_IG) - ig).coerceAtLeast(0))
            .putInt("total_$APP_YT", (total(c, APP_YT) - yt).coerceAtLeast(0))
            .remove(dayKey(d))
            .remove(dayKey(d, APP_IG))
            .remove(dayKey(d, APP_YT))
            .apply()
    }

    // ---- Ayarlar ----
    fun overlayOn(c: Context) = settings(c).getBoolean("overlay", true)
    fun setOverlay(c: Context, on: Boolean) = settings(c).edit().putBoolean("overlay", on).apply()

    fun debugOn(c: Context) = settings(c).getBoolean("debug", false)
    fun setDebug(c: Context, on: Boolean) = settings(c).edit().putBoolean("debug", on).apply()

    // ---- Hata ayıklama günlüğü ----
    fun log(c: Context, line: String) {
        if (!debugOn(c)) return
        val p = debugPrefs(c)
        val old = p.getString("log", "") ?: ""
        val time = java.time.LocalTime.now().withNano(0).toString()
        val lines = ("$time $line\n$old").lineSequence().take(MAX_LOG_LINES).joinToString("\n")
        p.edit().putString("log", lines).apply()
    }

    fun readLog(c: Context): String = debugPrefs(c).getString("log", "") ?: ""

    fun clearLog(c: Context) = debugPrefs(c).edit().remove("log").apply()

    fun debugPrefsFor(c: Context): SharedPreferences = debugPrefs(c)
}
