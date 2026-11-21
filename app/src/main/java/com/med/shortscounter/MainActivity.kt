package com.med.shortscounter

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private val tr = Locale("tr")
    private lateinit var ui: Ui

    // Üst kısım
    private lateinit var pill: TextView
    private lateinit var warnCard: LinearLayout

    // Bugün kartı
    private lateinit var heroCount: TextView
    private lateinit var heroCompare: TextView
    private lateinit var heroSplit: SegmentBar
    private lateinit var heroLegend: TextView
    private lateinit var heroTime: TextView
    private lateinit var heroSpeed: TextView
    private lateinit var heroSessions: TextView

    // Aralık seçici + kutular
    private val ranges = listOf(7 to "7 gün", 30 to "30 gün", 90 to "90 gün", 0 to "Tümü")
    private val rangeTabs = ArrayList<TextView>()
    private val tiles = ArrayList<Tile>()

    // Grafikler
    private lateinit var dailyChart: BarChartView
    private lateinit var dailySub: TextView
    private lateinit var hourChart: BarChartView
    private lateinit var hourText: TextView
    private lateinit var weekChart: BarChartView
    private lateinit var weekText: TextView
    private lateinit var distBox: LinearLayout
    private lateinit var insightsBox: LinearLayout

    // Geçmiş / ayarlar
    private lateinit var historyText: TextView
    private lateinit var historyBtn: TextView
    private lateinit var swOverlay: Switch
    private lateinit var swDebug: Switch
    private lateinit var debugCard: LinearLayout
    private lateinit var debugText: TextView

    private var showAllHistory = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refresh() }
    }

    // =====================================================================
    // Kurulum
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        colorSystemBars()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(C.BG)
            isFillViewport = true
        }
        val content = ui.vbox().apply {
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        // Android 15+ kenardan kenara: sistem çubuklarının altına girmesin
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars())
                top = i.top; bottom = i.bottom
            } else {
                top = insets.systemWindowInsetTop
                bottom = insets.systemWindowInsetBottom
            }
            content.setPadding(ui.dp(16), top + ui.dp(12), ui.dp(16), bottom + ui.dp(28))
            insets
        }

        buildHeader(content)
        buildWarn(content)
        buildHero(content)
        buildRange(content)
        buildTiles(content)
        buildDaily(content)
        buildHours(content)
        buildWeek(content)
        buildDist(content)
        buildInsights(content)
        buildHistory(content)
        buildSettings(content)
        buildDebug(content)
    }

    @Suppress("DEPRECATION")
    private fun colorSystemBars() {
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG
    }

    private fun buildHeader(parent: LinearLayout) {
        val row = ui.hbox()
        val titles = ui.vbox()
        titles.addView(ui.text(24f, C.TEXT, true, "Shorts Sayacı"))
        titles.addView(ui.text(13f, C.SUB, false, "Reels & Shorts kaydırma istatistiklerin"))
        row.addView(titles, ui.weight1())
        pill = ui.text(12f, C.BG, true, "").apply {
            setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
            setOnClickListener { if (!isServiceEnabled()) openA11y() }
        }
        row.addView(pill)
        parent.addView(row, ui.matchWrap(4))
    }

    private fun buildWarn(parent: LinearLayout) {
        warnCard = ui.card().apply { background = ui.round(0xFF2A2116.toInt(), 22) }
        warnCard.addView(ui.text(15f, C.AMBER, true, "Sayaç kapalı"))
        warnCard.addView(
            ui.text(
                13f, C.TEXT, false,
                "Ayarlar → Erişilebilirlik → Yüklü uygulamalar → Shorts Sayacı'nı aç.\n\n" +
                    "Gri/kilitliyse önce açmayı dene, sonra Ayarlar → Uygulamalar → Shorts Sayacı → " +
                    "sağ üst ⋮ → \"Kısıtlanmış ayarlara izin ver\"."
            ).apply { setPadding(0, ui.dp(6), 0, ui.dp(12)) }
        )
        warnCard.addView(ui.button("Erişilebilirlik ayarlarını aç", C.BG, C.AMBER).apply {
            setOnClickListener { openA11y() }
        })
        parent.addView(warnCard)
    }

    private fun buildHero(parent: LinearLayout) {
        val hero = ui.vbox().apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(C.HERO_A, C.HERO_B)
            ).apply { cornerRadius = ui.dp(26).toFloat() }
            setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18))
        }
        hero.addView(ui.text(12f, 0xCCFFFFFF.toInt(), true, "BUGÜN"))
        val numRow = ui.hbox().apply { gravity = Gravity.BOTTOM }
        heroCount = ui.text(60f, C.TEXT, true, "0")
        numRow.addView(heroCount)
        numRow.addView(ui.text(15f, 0xCCFFFFFF.toInt(), false, "  video").apply {
            setPadding(0, 0, 0, ui.dp(14))
        })
        hero.addView(numRow)
        heroCompare = ui.text(13f, 0xDDFFFFFF.toInt(), false, "")
        hero.addView(heroCompare)

        heroSplit = SegmentBar(this, 8).apply { trackColor = 0x33FFFFFF }
        hero.addView(heroSplit, ui.matchWrap(14))
        heroLegend = ui.text(12f, 0xDDFFFFFF.toInt(), false, "").apply { setPadding(0, ui.dp(6), 0, 0) }
        hero.addView(heroLegend)

        val minis = ui.hbox().apply { setPadding(0, ui.dp(14), 0, 0) }
        heroTime = miniStat(minis, "izleme süresi")
        heroSpeed = miniStat(minis, "video başına")
        heroSessions = miniStat(minis, "oturum")
        hero.addView(minis)

        parent.addView(hero, ui.matchWrap(14))
    }

    private fun miniStat(row: LinearLayout, label: String): TextView {
        val box = ui.vbox().apply {
            background = ui.round(0x26FFFFFF, 14)
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
        }
        val v = ui.text(16f, C.TEXT, true, "—")
        box.addView(v)
        box.addView(ui.text(11f, 0xCCFFFFFF.toInt(), false, label))
        val lp = ui.weight1().apply { marginEnd = if (row.childCount < 2) ui.dp(8) else 0 }
        row.addView(box, lp)
        return v
    }

    private fun buildRange(parent: LinearLayout) {
        val bar = ui.hbox().apply {
            background = ui.round(C.CARD, 16)
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        }
        for ((days, label) in ranges) {
            val t = ui.text(13f, C.SUB, true, label).apply {
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(9), 0, ui.dp(9))
                setOnClickListener {
                    Stats.settings(this@MainActivity).edit().putInt("range", days).apply()
                    refresh()
                }
            }
            rangeTabs.add(t)
            bar.addView(t, ui.weight1())
        }
        parent.addView(bar, ui.matchWrap(18))
    }

    private fun buildTiles(parent: LinearLayout) {
        val labels = listOf(
            "Toplam video", "İzleme süresi",
            "Günlük ortalama", "Günlük ort. süre",
            "Video başına", "Kaydırma hızı",
            "En çok gün", "Oturumlar",
            "En uzun oturum", "Hızlı geçiş"
        )
        for (r in labels.indices step 2) {
            val row = ui.hbox()
            parent.addView(row, ui.matchWrap(10))
            for (i in r..minOf(r + 1, labels.size - 1)) {
                val tile = Tile(ui, labels[i])
                tiles.add(tile)
                val lp = ui.weight1().apply { if (i % 2 == 0) marginEnd = ui.dp(10) }
                row.addView(tile.root, lp)
            }
        }
    }

    private fun buildDaily(parent: LinearLayout) {
        val card = ui.card()
        card.addView(ui.cardTitle("Günlük kaydırma"))
        dailySub = ui.text(12f, C.SUB, false, "")
        dailyChart = BarChartView(this)
        card.addView(dailyChart)
        card.addView(dailySub.apply { setPadding(0, ui.dp(6), 0, 0) })
        parent.addView(card, ui.matchWrap(16))
    }

    private fun buildHours(parent: LinearLayout) {
        val card = ui.card()
        card.addView(ui.cardTitle("Saatlere göre", "En aktif olduğun saatler"))
        hourChart = BarChartView(this).apply { setBarColors(0xFF2F4A5E.toInt(), 0xFF4FC3F7.toInt()) }
        card.addView(hourChart)
        hourText = ui.text(13f, C.TEXT, false, "").apply { setLineSpacing(ui.dp(3).toFloat(), 1f) }
        card.addView(hourText, ui.matchWrap(8))
        parent.addView(card)
    }

    private fun buildWeek(parent: LinearLayout) {
        val card = ui.card()
        card.addView(ui.cardTitle("Haftanın günleri", "Gün başına ortalama video"))
        weekChart = BarChartView(this).apply { setBarColors(0xFF2E5244.toInt(), C.GREEN) }
        card.addView(weekChart)
        weekText = ui.text(13f, C.TEXT, false, "")
        card.addView(weekText, ui.matchWrap(8))
        parent.addView(card)
    }

    private fun buildDist(parent: LinearLayout) {
        val card = ui.card()
        card.addView(ui.cardTitle("Bir videoda ne kadar kalıyorsun?", "Videoları izleme süresine göre dağılım"))
        distBox = ui.vbox()
        card.addView(distBox)
        parent.addView(card)
    }

    private fun buildInsights(parent: LinearLayout) {
        val card = ui.card()
        card.addView(ui.cardTitle("Öne çıkanlar"))
        insightsBox = ui.vbox()
        card.addView(insightsBox)
        parent.addView(card)
    }

    private fun buildHistory(parent: LinearLayout) {
        val card = ui.card()
        card.addView(ui.cardTitle("Geçmiş"))
        historyText = ui.text(12.5f, C.TEXT, false, "").apply {
            typeface = Typeface.MONOSPACE
            setLineSpacing(ui.dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
        }
        card.addView(historyText)
        historyBtn = ui.button("Tüm geçmişi göster").apply {
            setOnClickListener { showAllHistory = !showAllHistory; refresh() }
        }
        card.addView(historyBtn, ui.matchWrap(12))
        parent.addView(card)
    }

    private fun buildSettings(parent: LinearLayout) {
        val card = ui.card()
        card.addView(ui.cardTitle("Ayarlar"))
        swOverlay = Switch(this).apply {
            text = "Instagram/YouTube'da ekranda sayaç"
            setTextColor(C.TEXT)
            isChecked = Stats.overlayOn(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Stats.setOverlay(this@MainActivity, on) }
        }
        card.addView(swOverlay, ui.matchWrap(0))
        swDebug = Switch(this).apply {
            text = "Hata ayıklama günlüğü"
            setTextColor(C.TEXT)
            isChecked = Stats.debugOn(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Stats.setDebug(this@MainActivity, on); refresh() }
        }
        card.addView(swDebug, ui.matchWrap(14))

        val row = ui.hbox()
        row.addView(ui.button("Erişilebilirlik").apply { setOnClickListener { openA11y() } },
            ui.weight1().apply { marginEnd = ui.dp(10) })
        row.addView(ui.button("Bugünü sıfırla", C.RED).apply {
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Bugünü sıfırla")
                    .setMessage("Sadece bugünkü sayım ve süre silinir. Geçmiş günler korunur.")
                    .setPositiveButton("Sıfırla") { _, _ -> Stats.resetToday(this@MainActivity); refresh() }
                    .setNegativeButton("Vazgeç", null)
                    .show()
            }
        }, ui.weight1())
        card.addView(row, ui.matchWrap(16))

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { "" }
        card.addView(ui.text(11f, C.SUB, false, "Sürüm $ver").apply { setPadding(0, ui.dp(12), 0, 0) })
        parent.addView(card)
    }

    private fun buildDebug(parent: LinearLayout) {
        debugCard = ui.card()
        debugCard.addView(ui.cardTitle("Hata ayıklama günlüğü", "Yanlış sayıyorsa buradaki satırları gönder"))
        debugCard.addView(ui.button("Günlüğü temizle").apply {
            setOnClickListener { Stats.clearLog(this@MainActivity); refresh() }
        })
        debugText = ui.text(10.5f, C.SUB, false, "").apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        debugCard.addView(debugText, ui.matchWrap(10))
        parent.addView(debugCard)
    }

    override fun onResume() {
        super.onResume()
        Stats.stats(this).registerOnSharedPreferenceChangeListener(listener)
        Stats.debugPrefsFor(this).registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    override fun onPause() {
        Stats.stats(this).unregisterOnSharedPreferenceChangeListener(listener)
        Stats.debugPrefsFor(this).unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun openA11y() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, CounterService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    // =====================================================================
    // Veri toplama
    // =====================================================================

    /** Seçili aralıktaki günlerin toplu verisi. */
    private class Agg {
        var days = 0
        var videos = 0
        var ig = 0
        var yt = 0
        var time = 0L
        var timedVideos = 0      // süre ölçülen günlerdeki video sayısı
        var sessions = 0
        var sessionTime = 0L     // oturum tutulan günlerdeki süre
        var sessMax = 0L
        var sessMaxVideos = 0
        var best = 0
        var bestDay: LocalDate? = null
        val hours = IntArray(24)
        val hourTime = LongArray(24)
        val wdSum = IntArray(7)
        val wdCnt = IntArray(7)
        val buckets = IntArray(Stats.BUCKETS)
    }

    private fun trackedDays(): Int {
        val first = Stats.firstDay(this)
        return (ChronoUnit.DAYS.between(first, LocalDate.now()) + 1).toInt().coerceIn(1, 3650)
    }

    /** Bugünden geriye [n] günün verisini topla. */
    private fun agg(n: Int, skipToday: Boolean = false): Agg {
        val a = Agg()
        val now = LocalDate.now()
        val start = if (skipToday) 1 else 0
        for (i in start until n) {
            val d = now.minusDays(i.toLong())
            val ds = d.toString()
            val c = Stats.day(this, ds)
            val t = Stats.time(this, ds)
            val s = Stats.sessions(this, ds)
            a.days++
            a.videos += c
            a.ig += Stats.day(this, ds, Stats.APP_IG)
            a.yt += Stats.day(this, ds, Stats.APP_YT)
            a.time += t
            if (t > 0) a.timedVideos += c
            a.sessions += s
            if (s > 0) a.sessionTime += t
            a.sessMax = maxOf(a.sessMax, Stats.sessionMax(this, ds))
            a.sessMaxVideos = maxOf(a.sessMaxVideos, Stats.sessionMaxVideos(this, ds))
            if (c > a.best) { a.best = c; a.bestDay = d }
            for (h in 0..23) {
                a.hours[h] += Stats.hour(this, ds, h)
                a.hourTime[h] += Stats.hourTime(this, ds, h)
            }
            val wd = d.dayOfWeek.value - 1
            a.wdSum[wd] += c
            a.wdCnt[wd]++
            for (b in 0 until Stats.BUCKETS) a.buckets[b] += Stats.bucket(this, ds, b)
        }
        return a
    }

    private fun shortDate(d: LocalDate) =
        "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, tr)}"

    private fun f1(v: Double) = String.format(tr, "%.1f", v)
    private fun f0(v: Double) = v.roundToInt().toString()
    private fun pct(part: Number, whole: Number): Int =
        if (whole.toDouble() <= 0) 0 else (part.toDouble() * 100 / whole.toDouble()).roundToInt()

    private fun selectedRange(): Int = Stats.settings(this).getInt("range", 30)

    // =====================================================================
    // Ekranı güncelle
    // =====================================================================

    private fun refresh() {
        val on = isServiceEnabled()
        pill.text = if (on) "● AKTİF" else "● KAPALI"
        pill.background = ui.round(if (on) C.GREEN else C.AMBER, 20)
        warnCard.visibility = if (on) View.GONE else View.VISIBLE

        val tracked = trackedDays()
        val all = agg(tracked)
        refreshHero(all)

        val range = selectedRange()
        ranges.forEachIndexed { i, (days, _) ->
            val sel = days == range
            rangeTabs[i].setTextColor(if (sel) C.TEXT else C.SUB)
            rangeTabs[i].background = if (sel) ui.round(C.ACCENT, 12) else null
        }
        val n = if (range == 0) tracked else minOf(range, tracked)
        val a = if (n == tracked) all else agg(n)

        refreshTiles(a)
        refreshDaily(range, tracked)
        refreshHours(a)
        refreshWeek(a)
        refreshDist(a)
        refreshInsights(a, all, tracked)
        refreshHistory(tracked)

        val dbg = Stats.debugOn(this)
        debugCard.visibility = if (dbg) View.VISIBLE else View.GONE
        if (dbg) debugText.text = Stats.readLog(this).ifEmpty { "(henüz kayıt yok — Reels/Shorts'ta kaydır)" }
    }

    private fun refreshHero(all: Agg) {
        val today = Stats.today()
        val c = Stats.day(this, today)
        val ig = Stats.day(this, today, Stats.APP_IG)
        val yt = Stats.day(this, today, Stats.APP_YT)
        val t = Stats.time(this, today)
        heroCount.text = c.toString()

        val yesterday = Stats.day(this, LocalDate.now().minusDays(1).toString())
        val past = agg(trackedDays(), skipToday = true)
        val sb = StringBuilder("Dün $yesterday")
        if (past.days > 0) {
            val avg = past.videos.toDouble() / past.days
            sb.append("  ·  Ortalama ${f0(avg)}")
            if (avg >= 1 && c > 0) {
                val diff = pct(c - avg, avg)
                sb.append(if (diff >= 0) "  ·  ▲ %$diff" else "  ·  ▼ %${abs(diff)}")
            }
        }
        heroCompare.text = sb

        heroSplit.set(floatArrayOf(ig.toFloat(), yt.toFloat()), intArrayOf(C.IG, C.YT))
        val leg = SpannableStringBuilder()
        appendDot(leg, C.IG); leg.append(" Instagram $ig    ")
        appendDot(leg, C.YT); leg.append(" YouTube $yt")
        heroLegend.text = leg

        heroTime.text = Stats.fmtTime(t)
        heroSpeed.text = if (c > 0 && t > 0) "${f1(t.toDouble() / c)} sn" else "—"
        heroSessions.text = Stats.sessions(this, today).toString()
    }

    private fun appendDot(sb: SpannableStringBuilder, color: Int) {
        val s = sb.length
        sb.append("●")
        sb.setSpan(ForegroundColorSpan(color), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun refreshTiles(a: Agg) {
        val d = a.days.coerceAtLeast(1)
        tiles[0].set(a.videos.toString(), "IG ${a.ig} · YT ${a.yt}")
        tiles[1].set(Stats.fmtTime(a.time), "${a.days} günde")
        tiles[2].set(f0(a.videos.toDouble() / d), "video / gün")
        tiles[3].set(Stats.fmtTime(a.time / d), "süre / gün")
        if (a.timedVideos > 0 && a.time > 0) {
            tiles[4].set("${f1(a.time.toDouble() / a.timedVideos)} sn", "ortalama izleme")
            tiles[5].set(f1(a.timedVideos / (a.time / 60.0)), "video / dakika")
        } else {
            tiles[4].set("—", "henüz veri yok")
            tiles[5].set("—", "henüz veri yok")
        }
        tiles[6].set(a.best.toString(), a.bestDay?.let { shortDate(it) } ?: "—")
        if (a.sessions > 0) {
            tiles[7].set(a.sessions.toString(), "ort. ${Stats.fmtTime(a.sessionTime / a.sessions)} · ${f1(a.sessions.toDouble() / d)}/gün")
            tiles[8].set(Stats.fmtTime(a.sessMax), "en fazla ${a.sessMaxVideos} video")
        } else {
            tiles[7].set("—", "henüz veri yok")
            tiles[8].set("—", "henüz veri yok")
        }
        val bTotal = a.buckets.sum()
        if (bTotal > 0) {
            tiles[9].set("%${pct(a.buckets[0] + a.buckets[1], bTotal)}", "5 sn'den kısa izlenen")
        } else {
            tiles[9].set("—", "henüz veri yok")
        }
    }

    private fun refreshDaily(range: Int, tracked: Int) {
        val n = when (range) {
            0 -> tracked.coerceIn(7, 120)
            else -> range
        }
        val now = LocalDate.now()
        val values = IntArray(n)
        val labels = Array(n) { "" }
        val tips = Array(n) { "" }
        val step = when {
            n <= 7 -> 1
            n <= 31 -> 5
            n <= 92 -> 15
            else -> 30
        }
        for (i in 0 until n) {
            val d = now.minusDays((n - 1 - i).toLong())
            val ds = d.toString()
            values[i] = Stats.day(this, ds)
            labels[i] = when {
                n <= 7 -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, tr)
                (n - 1 - i) % step == 0 -> d.dayOfMonth.toString()
                else -> ""
            }
            val t = Stats.time(this, ds)
            tips[i] = "${shortDate(d)}: ${values[i]} video" + if (t > 0) " · ${Stats.fmtTime(t)}" else ""
        }
        dailyChart.highlight = n - 1
        val avg = values.sum().toDouble() / n
        dailyChart.defaultTip = "ortalama ${f0(avg)} · en yüksek ${values.maxOrNull() ?: 0}"
        dailyChart.setData(values, labels, tips)
        dailySub.text = "Bir çubuğa dokun ya da parmağını kaydır."
    }

    private fun refreshHours(a: Agg) {
        val sum = a.hours.sum()
        val labels = Array(24) { h -> if (h % 3 == 0) String.format(tr, "%02d", h) else "" }
        val tips = Array(24) { h ->
            val tt = a.hourTime[h]
            String.format(tr, "%02d:00–%02d:00 · %d video · %%%d", h, (h + 1) % 24, a.hours[h], pct(a.hours[h], sum)) +
                if (tt > 0) " · ${Stats.fmtTime(tt)}" else ""
        }
        hourChart.highlight = LocalTime.now().hour
        hourChart.defaultTip = "çubuğa dokun"
        hourChart.setData(a.hours, labels, tips)

        if (sum == 0) {
            hourText.text = "Henüz veri yok."
            return
        }
        var peak = 0
        for (h in 1..23) if (a.hours[h] > a.hours[peak]) peak = h
        var best3 = 0
        var best3Start = 0
        for (h in 0..23) {
            val s = a.hours[h] + a.hours[(h + 1) % 24] + a.hours[(h + 2) % 24]
            if (s > best3) { best3 = s; best3Start = h }
        }
        val night = (0..5).sumOf { a.hours[it] }
        val activeHours = a.hours.count { it > 0 }
        hourText.text = String.format(
            tr,
            "🔥 En aktif saat: %02d:00–%02d:00 (%%%d)\n" +
                "⏰ En yoğun 3 saat: %02d:00–%02d:00 (%%%d)\n" +
                "🌙 Gece 00–06 arası: %%%d\n" +
                "📊 Kaydırma yapılan farklı saat: %d / 24",
            peak, (peak + 1) % 24, pct(a.hours[peak], sum),
            best3Start, (best3Start + 3) % 24, pct(best3, sum),
            pct(night, sum), activeHours
        )
    }

    private fun refreshWeek(a: Agg) {
        val names = Array(7) { java.time.DayOfWeek.of(it + 1).getDisplayName(TextStyle.SHORT, tr) }
        val full = Array(7) { java.time.DayOfWeek.of(it + 1).getDisplayName(TextStyle.FULL, tr) }
        val avgs = DoubleArray(7) { if (a.wdCnt[it] > 0) a.wdSum[it].toDouble() / a.wdCnt[it] else 0.0 }
        val values = IntArray(7) { avgs[it].roundToInt() }
        val tips = Array(7) { "${full[it]}: ort. ${f1(avgs[it])} video (${a.wdCnt[it]} gün)" }
        weekChart.highlight = LocalDate.now().dayOfWeek.value - 1
        weekChart.defaultTip = "çubuğa dokun"
        weekChart.setData(values, names, tips)

        if (a.videos == 0) {
            weekText.text = "Henüz veri yok."
            return
        }
        var top = 0
        for (i in 1..6) if (avgs[i] > avgs[top]) top = i
        val wkCnt = a.wdCnt[5] + a.wdCnt[6]
        val wdCnt = (0..4).sumOf { a.wdCnt[it] }
        val wkAvg = if (wkCnt > 0) (a.wdSum[5] + a.wdSum[6]).toDouble() / wkCnt else 0.0
        val wdAvg = if (wdCnt > 0) (0..4).sumOf { a.wdSum[it] }.toDouble() / wdCnt else 0.0
        val sb = StringBuilder("📅 En çok: ${full[top]} (ort. ${f0(avgs[top])})")
        if (wkCnt > 0 && wdCnt > 0) {
            sb.append("\n🗓 Hafta içi ort. ${f0(wdAvg)} · hafta sonu ort. ${f0(wkAvg)}")
        }
        weekText.text = sb
    }

    private fun refreshDist(a: Agg) {
        distBox.removeAllViews()
        val total = a.buckets.sum()
        if (total == 0) {
            distBox.addView(
                ui.text(
                    13f, C.SUB, false,
                    "Bu veri yeni sürümle tutulmaya başladı. Biraz kaydırınca burada görünecek."
                )
            )
            return
        }
        val max = a.buckets.maxOrNull() ?: 1
        val colors = intArrayOf(C.RED, C.AMBER, 0xFFE6D45A.toInt(), C.GREEN, 0xFF4FC3F7.toInt(), C.ACCENT)
        for (b in 0 until Stats.BUCKETS) {
            val row = ui.hbox()
            row.addView(ui.text(12f, C.SUB, false, Stats.BUCKET_LABELS[b]).apply {
                setWidth(ui.dp(64))
            })
            val bar = SegmentBar(this, 10)
            bar.set(floatArrayOf(a.buckets[b].toFloat()), intArrayOf(colors[b]), max.toFloat())
            row.addView(bar, ui.weight1())
            row.addView(ui.text(12f, C.TEXT, true, "%${pct(a.buckets[b], total)}").apply {
                setWidth(ui.dp(48))
                gravity = Gravity.END
            })
            distBox.addView(row, ui.matchWrap(if (b == 0) 0 else 10))
        }
        distBox.addView(
            ui.text(12f, C.SUB, false, "$total video ölçüldü").apply { setPadding(0, ui.dp(10), 0, 0) }
        )
    }

    private fun refreshInsights(a: Agg, all: Agg, tracked: Int) {
        insightsBox.removeAllViews()
        val lines = ArrayList<String>()
        val now = LocalDate.now()

        // Son 7 gün vs önceki 7 gün
        if (tracked >= 8) {
            var last7 = 0
            var prev7 = 0
            for (i in 0 until 7) last7 += Stats.day(this, now.minusDays(i.toLong()).toString())
            for (i in 7 until 14) prev7 += Stats.day(this, now.minusDays(i.toLong()).toString())
            if (prev7 > 0) {
                val p = pct(last7 - prev7, prev7)
                lines.add(
                    if (p >= 0) "📈 Son 7 günde önceki haftaya göre %$p daha fazla kaydırdın ($last7 vs $prev7)."
                    else "📉 Son 7 günde önceki haftaya göre %${abs(p)} daha az kaydırdın ($last7 vs $prev7). 👏"
                )
            }
        }

        // Yıllık projeksiyon
        if (all.time > 0) {
            val timedDays = (0 until tracked).count { Stats.time(this, now.minusDays(it.toLong()).toString()) > 0 }
            if (timedDays > 0) {
                val perDay = all.time.toDouble() / timedDays
                val yearHours = perDay * 365 / 3600
                val yearVideos = all.videos.toDouble() / tracked * 365
                lines.add(
                    "⏳ Bu tempoyla yılda ~${f0(yearHours)} saat (${f1(yearHours / 24)} gün) ve " +
                        "~${f0(yearVideos)} video."
                )
            }
        }

        // Hızlı geçiş
        val bt = a.buckets.sum()
        if (bt >= 20) {
            val fast = pct(a.buckets[0], bt)
            val long = pct(a.buckets[4] + a.buckets[5], bt)
            lines.add("⚡ Videoların %$fast'ini 2 saniyeden kısa sürede geçiyorsun, %$long'ini 30 sn'den fazla izliyorsun.")
        }

        // Uygulama payı
        if (a.videos > 0) {
            val igP = pct(a.ig, a.videos)
            lines.add("📱 Kaydırmalarının %$igP'i Instagram, %${100 - igP}'i YouTube.")
        }

        // En uzun oturum
        if (a.sessMax > 0) {
            lines.add("🎯 En uzun oturumun ${Stats.fmtTime(a.sessMax)} sürdü; tek oturumda en fazla ${a.sessMaxVideos} video.")
        }

        // Tam gün eşdeğeri
        if (all.videos > 0) {
            val noScroll = (0 until tracked).count { Stats.day(this, now.minusDays(it.toLong()).toString()) == 0 }
            if (noScroll > 0) lines.add("🌱 Takip ettiğin $tracked günün $noScroll gününde hiç kaydırmadın.")
        }

        if (lines.isEmpty()) lines.add("Biraz daha veri toplanınca burada özetler görünecek.")
        lines.forEachIndexed { i, l ->
            insightsBox.addView(
                ui.text(13.5f, C.TEXT, false, l).apply { setLineSpacing(ui.dp(2).toFloat(), 1f) },
                ui.matchWrap(if (i == 0) 0 else 10)
            )
        }
    }

    private fun refreshHistory(tracked: Int) {
        val now = LocalDate.now()
        val shown = if (showAllHistory) tracked else minOf(tracked, 14)
        val sb = SpannableStringBuilder()
        var curMonth = -1
        var curYear = -1
        for (i in 0 until shown) {
            val d = now.minusDays(i.toLong())
            if (d.monthValue != curMonth || d.year != curYear) {
                curMonth = d.monthValue
                curYear = d.year
                var mTotal = 0
                var mTime = 0L
                var x = d.withDayOfMonth(1)
                while (!x.isAfter(d) && x.month == d.month) {
                    mTotal += Stats.day(this, x.toString())
                    mTime += Stats.time(this, x.toString())
                    x = x.plusDays(1)
                }
                if (sb.isNotEmpty()) sb.append('\n')
                val start = sb.length
                val mName = d.month.getDisplayName(TextStyle.FULL, tr)
                sb.append("$mName ${d.year} — $mTotal video")
                if (mTime > 0) sb.append(" · ${Stats.fmtTime(mTime)}")
                sb.setSpan(ForegroundColorSpan(C.ACCENT), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append('\n')
            }
            val ds = d.toString()
            val c = Stats.day(this, ds)
            val ig = Stats.day(this, ds, Stats.APP_IG)
            val yt = Stats.day(this, ds, Stats.APP_YT)
            val t = Stats.time(this, ds)
            val name = if (i == 0) "Bugün" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, tr)
            val start = sb.length
            sb.append(String.format(tr, "%2d %-5s %5d  IG %-4d YT %-4d", d.dayOfMonth, name, c, ig, yt))
            if (t > 0) sb.append(" ").append(Stats.fmtTime(t))
            if (c == 0) sb.setSpan(ForegroundColorSpan(C.SUB), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append('\n')
        }
        while (sb.isNotEmpty() && sb[sb.length - 1] == '\n') sb.delete(sb.length - 1, sb.length)
        historyText.text = sb

        if (tracked <= 14) {
            historyBtn.visibility = View.GONE
        } else {
            historyBtn.visibility = View.VISIBLE
            historyBtn.text = if (showAllHistory) "Daha az göster" else "Tüm geçmişi göster ($tracked gün)"
        }
    }
}
