package com.med.shortscounter

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.LocalTime

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

    private fun hourKey(date: String, hour: Int) = "h_${date}_$hour"

    private fun timeKey(date: String, app: String? = null) =
        if (app == null) "t_$date" else "t_${date}_$app"

    // ---------------- Video sayısı ----------------

    fun add(c: Context, app: String, n: Int) {
        if (n <= 0) return
        val p = stats(c)
        val d = today()
        val h = LocalTime.now().hour
        val e = p.edit()
            .putInt(dayKey(d), p.getInt(dayKey(d), 0) + n)
            .putInt(dayKey(d, app), p.getInt(dayKey(d, app), 0) + n)
            .putInt(hourKey(d, h), p.getInt(hourKey(d, h), 0) + n)
            .putInt("total", p.getInt("total", 0) + n)
            .putInt("total_$app", p.getInt("total_$app", 0) + n)
        if (!p.contains("first_day")) e.putString("first_day", d)
        e.apply()
    }

    fun day(c: Context, date: String, app: String? = null): Int =
        stats(c).getInt(dayKey(date, app), 0)

    fun hour(c: Context, date: String, hour: Int): Int =
        stats(c).getInt(hourKey(date, hour), 0)

    fun total(c: Context, app: String? = null): Int =
        stats(c).getInt(if (app == null) "total" else "total_$app", 0)

    /** İlk sayımın yapıldığı gün (yoksa bugün). */
    fun firstDay(c: Context): LocalDate {
        val s = stats(c).getString("first_day", null) ?: return LocalDate.now()
        return try {
            LocalDate.parse(s)
        } catch (_: Exception) {
            LocalDate.now()
        }
    }

    // ---------------- İzleme süresi (saniye) ----------------

    fun addTime(c: Context, app: String, sec: Long) {
        if (sec <= 0) return
        val p = stats(c)
        val d = today()
        val hk = "ht_${d}_${LocalTime.now().hour}"
        p.edit()
            .putLong(timeKey(d), p.getLong(timeKey(d), 0) + sec)
            .putLong(timeKey(d, app), p.getLong(timeKey(d, app), 0) + sec)
            .putLong(hk, p.getLong(hk, 0) + sec)
            .putLong("time_total", p.getLong("time_total", 0) + sec)
            .apply()
    }

    fun hourTime(c: Context, date: String, hour: Int): Long =
        stats(c).getLong("ht_${date}_$hour", 0)

    // ---------------- Video başına izleme süresi dağılımı ----------------
    // (v1.2'den itibaren tutulur)

    val BUCKET_LABELS = arrayOf("< 2 sn", "2–5 sn", "5–15 sn", "15–30 sn", "30–60 sn", "60 sn +")
    val BUCKETS = BUCKET_LABELS.size

    fun bucketOf(sec: Long): Int = when {
        sec < 2 -> 0
        sec < 5 -> 1
        sec < 15 -> 2
        sec < 30 -> 3
        sec < 60 -> 4
        else -> 5
    }

    fun addBucket(c: Context, b: Int) {
        val p = stats(c)
        val k = "b_${today()}_$b"
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply()
    }

    fun bucket(c: Context, date: String, b: Int): Int = stats(c).getInt("b_${date}_$b", 0)

    // ---------------- Oturumlar ----------------
    // Oturum: arada 3 dakikadan uzun ara olmadan art arda kaydırma.

    fun startSession(c: Context) {
        val p = stats(c)
        val k = "s_${today()}"
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply()
    }

    fun updateSessionMax(c: Context, sec: Long, videos: Int) {
        val p = stats(c)
        val d = today()
        val e = p.edit()
        var changed = false
        if (sec > p.getLong("smax_$d", 0)) { e.putLong("smax_$d", sec); changed = true }
        if (videos > p.getInt("svmax_$d", 0)) { e.putInt("svmax_$d", videos); changed = true }
        if (changed) e.apply()
    }

    fun sessions(c: Context, date: String): Int = stats(c).getInt("s_$date", 0)
    fun sessionMax(c: Context, date: String): Long = stats(c).getLong("smax_$date", 0)
    fun sessionMaxVideos(c: Context, date: String): Int = stats(c).getInt("svmax_$date", 0)

    fun time(c: Context, date: String, app: String? = null): Long =
        stats(c).getLong(timeKey(date, app), 0)

    fun timeTotal(c: Context): Long = stats(c).getLong("time_total", 0)

    /** 45 -> "45 sn", 75 -> "1 dk", 3900 -> "1 sa 5 dk" */
    fun fmtTime(sec: Long): String {
        val m = sec / 60
        return when {
            sec <= 0 -> "0 dk"
            sec < 60 -> "$sec sn"
            m < 60 -> "$m dk"
            else -> "${m / 60} sa ${m % 60} dk"
        }
    }

    // ---------------- Sıfırlama ----------------

    fun resetToday(c: Context) {
        val p = stats(c)
        val d = today()
        val ig = day(c, d, APP_IG)
        val yt = day(c, d, APP_YT)
        val e = p.edit()
            .putInt("total", (total(c) - ig - yt).coerceAtLeast(0))
            .putInt("total_$APP_IG", (total(c, APP_IG) - ig).coerceAtLeast(0))
            .putInt("total_$APP_YT", (total(c, APP_YT) - yt).coerceAtLeast(0))
            .putLong("time_total", (timeTotal(c) - time(c, d)).coerceAtLeast(0))
            .remove(dayKey(d))
            .remove(dayKey(d, APP_IG))
            .remove(dayKey(d, APP_YT))
            .remove(timeKey(d))
            .remove(timeKey(d, APP_IG))
            .remove(timeKey(d, APP_YT))
        for (h in 0..23) {
            e.remove(hourKey(d, h))
            e.remove("ht_${d}_$h")
        }
        for (b in 0 until BUCKETS) e.remove("b_${d}_$b")
        e.remove("s_$d").remove("smax_$d").remove("svmax_$d")
        e.apply()
    }

    // ---------------- Ayarlar ----------------

    fun overlayOn(c: Context) = settings(c).getBoolean("overlay", true)
    fun setOverlay(c: Context, on: Boolean) = settings(c).edit().putBoolean("overlay", on).apply()

    fun debugOn(c: Context) = settings(c).getBoolean("debug", false)
    fun setDebug(c: Context, on: Boolean) = settings(c).edit().putBoolean("debug", on).apply()

    // ---------------- Hata ayıklama günlüğü ----------------

    fun log(c: Context, line: String) {
        if (!debugOn(c)) return
        val p = debugPrefs(c)
        val old = p.getString("log", "") ?: ""
        val time = LocalTime.now().withNano(0).toString()
        val lines = ("$time $line\n$old").lineSequence().take(MAX_LOG_LINES).joinToString("\n")
        p.edit().putString("log", lines).apply()
    }

    fun readLog(c: Context): String = debugPrefs(c).getString("log", "") ?: ""

    fun clearLog(c: Context) = debugPrefs(c).edit().remove("log").apply()

    fun debugPrefsFor(c: Context): SharedPreferences = debugPrefs(c)
}
