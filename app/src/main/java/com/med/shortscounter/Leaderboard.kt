package com.med.shortscounter

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Sıralamadaki bir kullanıcının özeti. */
data class Entry(
    val id: String,
    val name: String,
    val videos: Long,
    val time: Long,
    val timedVideos: Long,
    val days: Long,
    val bestDay: Long,
    val sessMax: Long,
    val sessMaxVideos: Long,
    val last7: Long,
    val last7Time: Long,
    val fastPct: Long,
    val updatedAt: String
) {
    val secPerVideo: Double get() = if (timedVideos > 0) time.toDouble() / timedVideos else 0.0
    val dailyAvg: Double get() = if (days > 0) videos.toDouble() / days else 0.0
}

/** Bir sıralama kategorisi (ödül). */
class Category(
    val key: String,
    val emoji: String,
    val title: String,
    val desc: String,
    val ascending: Boolean,
    val requirement: String?,
    val eligible: (Entry) -> Boolean,
    val value: (Entry) -> Double,
    val fmt: (Entry) -> String
)

/**
 * Firebase (Firestore REST + anonim giriş) üzerinden çalışan, isteğe bağlı sıralama.
 * Sadece kullanıcı butona bastığında ağa çıkılır.
 */
object Leaderboard {

    private const val MAX_PAGES = 10

    val categories: List<Category> = listOf(
        Category(
            "most", "🏆", "Kaydırma Kralı", "Tüm zamanlarda en çok video kaydıran", false, null,
            { it.videos > 0 }, { it.videos.toDouble() }, { "${it.videos} video" }
        ),
        Category(
            "week", "📅", "Haftanın Yıldızı", "Son 7 günde en çok video kaydıran", false, null,
            { it.last7 > 0 }, { it.last7.toDouble() }, { "${it.last7} video" }
        ),
        Category(
            "fast", "⚡", "Şimşek Parmak", "Video başına en kısa süre — en hızlı kaydıran", true,
            "En az 50 ölçülmüş video gerekli",
            { it.timedVideos >= 50 && it.time > 0 }, { it.secPerVideo },
            { String.format(java.util.Locale("tr"), "%.1f sn/video", it.secPerVideo) }
        ),
        Category(
            "time", "⏱", "Maratoncu", "En uzun toplam izleme süresi", false, null,
            { it.time > 0 }, { it.time.toDouble() }, { Stats.fmtTime(it.time) }
        ),
        Category(
            "session", "🎯", "Oturum Canavarı", "Tek seferde en uzun kaydırma oturumu", false, null,
            { it.sessMax > 0 }, { it.sessMax.toDouble() },
            { "${Stats.fmtTime(it.sessMax)} · ${it.sessMaxVideos} video" }
        ),
        Category(
            "record", "🔥", "Rekor Gün", "Tek günde en çok video", false, null,
            { it.bestDay > 0 }, { it.bestDay.toDouble() }, { "${it.bestDay} video" }
        ),
        Category(
            "zen", "🧘", "Zen Ustası", "Günlük ortalaması en düşük — en az kaydıran", true,
            "En az 3 günlük veri gerekli",
            { it.days >= 3 }, { it.dailyAvg },
            { String.format(java.util.Locale("tr"), "günde %.1f video", it.dailyAvg) }
        )
    )

    fun configured() = Config.FIREBASE_PROJECT_ID.isNotBlank() && Config.FIREBASE_API_KEY.isNotBlank()

    private val base: String
        get() = "https://firestore.googleapis.com/v1/projects/${Config.FIREBASE_PROJECT_ID}/databases/(default)/documents"

    private fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("leaderboard", Context.MODE_PRIVATE)

    // ---------------- Yerel ayarlar ----------------

    fun name(c: Context): String = prefs(c).getString("name", "") ?: ""
    fun setName(c: Context, n: String) = prefs(c).edit().putString("name", n).apply()

    /** İsim bir kez sunucuya gönderildiyse kilitlenir, bir daha değiştirilemez. */
    fun nameLocked(c: Context): Boolean {
        val p = prefs(c)
        if (p.getBoolean("name_locked", false)) return true
        // Önceki sürümde zaten katılmış kullanıcılar
        return p.getString("uid", null) != null && p.getLong("last_fetch", 0) > 0 && name(c).isNotBlank()
    }

    fun lockName(c: Context) = prefs(c).edit().putBoolean("name_locked", true).apply()

    /** Bu isim başka bir kullanıcıda var mı? (büyük/küçük harf duyarsız) */
    fun nameTaken(list: List<Entry>, name: String, myId: String?): Boolean =
        list.any { it.id != myId && it.name.trim().equals(name.trim(), ignoreCase = true) }
    fun myId(c: Context): String? = prefs(c).getString("uid", null)
    fun lastFetch(c: Context): Long = prefs(c).getLong("last_fetch", 0)

    fun cached(c: Context): List<Entry> {
        val s = prefs(c).getString("cache", null) ?: return emptyList()
        return try {
            parseDocs(JSONArray(s))
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------------- Kendi özetini hesapla ----------------

    fun buildSummary(c: Context): JSONObject {
        val now = LocalDate.now()
        val days = (ChronoUnit.DAYS.between(Stats.firstDay(c), now) + 1).toInt().coerceIn(1, 3650)
        var timedVideos = 0L
        var bestDay = 0L
        var sessMax = 0L
        var sessMaxVideos = 0L
        var last7 = 0L
        var last7Time = 0L
        var fast = 0L
        var bucketTotal = 0L
        for (i in 0 until days) {
            val ds = now.minusDays(i.toLong()).toString()
            val v = Stats.day(c, ds).toLong()
            val t = Stats.time(c, ds)
            if (t > 0) timedVideos += v
            if (v > bestDay) bestDay = v
            sessMax = maxOf(sessMax, Stats.sessionMax(c, ds))
            sessMaxVideos = maxOf(sessMaxVideos, Stats.sessionMaxVideos(c, ds).toLong())
            if (i < 7) { last7 += v; last7Time += t }
            fast += Stats.bucket(c, ds, 0)
            for (b in 0 until Stats.BUCKETS) bucketTotal += Stats.bucket(c, ds, b)
        }
        return JSONObject()
            .put("videos", Stats.total(c).toLong())
            .put("time", Stats.timeTotal(c))
            .put("timedVideos", timedVideos)
            .put("days", days.toLong())
            .put("bestDay", bestDay)
            .put("sessMax", sessMax)
            .put("sessMaxVideos", sessMaxVideos)
            .put("last7", last7)
            .put("last7Time", last7Time)
            .put("fastPct", if (bucketTotal > 0) fast * 100 / bucketTotal else 0L)
    }

    // ---------------- Ağ ----------------

    private class Resp(val code: Int, val body: String)

    private fun http(
        method: String,
        url: String,
        body: String? = null,
        contentType: String = "application/json",
        bearer: String? = null
    ): Resp {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection PATCH desteklemez; Google API'leri bu başlığı kabul eder.
            if (method == "PATCH") {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                conn.requestMethod = method
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Resp(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun errMsg(r: Resp): String = try {
        JSONObject(r.body).getJSONObject("error").optString("message", "HTTP ${r.code}")
    } catch (_: Exception) {
        "HTTP ${r.code}"
    }

    private fun saveToken(c: Context, id: String, refresh: String, uid: String, expiresInSec: Long) {
        prefs(c).edit()
            .putString("tok", id)
            .putString("refresh", refresh)
            .putString("uid", uid)
            .putLong("exp", System.currentTimeMillis() + expiresInSec * 1000)
            .apply()
    }

    /** Anonim Firebase oturumu: (idToken, uid) */
    private fun auth(c: Context): Pair<String, String> {
        val p = prefs(c)
        val tok = p.getString("tok", null)
        val uid = p.getString("uid", null)
        if (tok != null && uid != null && System.currentTimeMillis() < p.getLong("exp", 0) - 60_000) {
            return tok to uid
        }
        val key = Config.FIREBASE_API_KEY
        val refresh = p.getString("refresh", null)
        if (refresh != null) {
            val r = http(
                "POST", "https://securetoken.googleapis.com/v1/token?key=$key",
                "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"),
                "application/x-www-form-urlencoded"
            )
            if (r.code == 200) {
                val j = JSONObject(r.body)
                saveToken(
                    c, j.getString("id_token"), j.getString("refresh_token"),
                    j.getString("user_id"), j.optString("expires_in", "3600").toLong()
                )
                return j.getString("id_token") to j.getString("user_id")
            }
        }
        val r = http(
            "POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$key",
            "{\"returnSecureToken\":true}"
        )
        if (r.code != 200) throw IOException("Giriş yapılamadı: ${errMsg(r)}")
        val j = JSONObject(r.body)
        saveToken(
            c, j.getString("idToken"), j.getString("refreshToken"),
            j.getString("localId"), j.optString("expiresIn", "3600").toLong()
        )
        return j.getString("idToken") to j.getString("localId")
    }

    /** Kendi özetini gönderir. Arka plan iş parçacığında çağır. */
    fun upload(c: Context, name: String) {
        val (tok, uid) = auth(c)
        val s = buildSummary(c)
        val fields = JSONObject()
        fields.put("name", JSONObject().put("stringValue", name))
        val keys = s.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            fields.put(k, JSONObject().put("integerValue", s.getLong(k).toString()))
        }
        fields.put("updatedAt", JSONObject().put("timestampValue", Instant.now().toString()))
        val body = JSONObject().put("fields", fields).toString()
        val r = http("PATCH", "$base/users/$uid?key=${Config.FIREBASE_API_KEY}", body, bearer = tok)
        if (r.code !in 200..299) throw IOException("Gönderilemedi: ${errMsg(r)}")
    }

    /** Tüm kullanıcıları çeker ve önbelleğe yazar. Arka plan iş parçacığında çağır. */
    fun fetchAll(c: Context): List<Entry> {
        val all = JSONArray()
        var pageToken: String? = null
        var pages = 0
        do {
            var url = "$base/users?pageSize=300&key=${Config.FIREBASE_API_KEY}"
            if (pageToken != null) url += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8")
            val r = http("GET", url)
            if (r.code !in 200..299) throw IOException("Sıralama alınamadı: ${errMsg(r)}")
            val j = JSONObject(r.body)
            val docs = j.optJSONArray("documents")
            if (docs != null) for (i in 0 until docs.length()) all.put(docs.getJSONObject(i))
            pageToken = j.optString("nextPageToken", "").ifEmpty { null }
            pages++
        } while (pageToken != null && pages < MAX_PAGES)

        prefs(c).edit()
            .putString("cache", all.toString())
            .putLong("last_fetch", System.currentTimeMillis())
            .apply()
        return parseDocs(all)
    }

    private fun parseDocs(arr: JSONArray): List<Entry> {
        val out = ArrayList<Entry>()
        for (i in 0 until arr.length()) {
            try {
                val d = arr.getJSONObject(i)
                val f = d.getJSONObject("fields")
                fun num(k: String): Long {
                    val o = f.optJSONObject(k) ?: return 0L
                    if (o.has("integerValue")) return o.optString("integerValue", "0").toLongOrNull() ?: 0L
                    if (o.has("doubleValue")) return o.optDouble("doubleValue", 0.0).toLong()
                    return 0L
                }
                out.add(
                    Entry(
                        id = d.getString("name").substringAfterLast('/'),
                        name = f.optJSONObject("name")?.optString("stringValue", "?") ?: "?",
                        videos = num("videos"),
                        time = num("time"),
                        timedVideos = num("timedVideos"),
                        days = num("days"),
                        bestDay = num("bestDay"),
                        sessMax = num("sessMax"),
                        sessMaxVideos = num("sessMaxVideos"),
                        last7 = num("last7"),
                        last7Time = num("last7Time"),
                        fastPct = num("fastPct"),
                        updatedAt = f.optJSONObject("updatedAt")?.optString("timestampValue", "") ?: ""
                    )
                )
            } catch (_: Exception) {
            }
        }
        return out
    }

    /** Kategoriye göre sıralı liste (uygun olmayanlar hariç). */
    fun rank(list: List<Entry>, cat: Category): List<Entry> {
        val f = list.filter(cat.eligible)
        return if (cat.ascending) f.sortedBy { cat.value(it) } else f.sortedByDescending { cat.value(it) }
    }
}
