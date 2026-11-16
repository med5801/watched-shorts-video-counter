package com.med.shortscounter

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : Activity() {

    private val tr = Locale("tr")

    private lateinit var tvStatus: TextView
    private lateinit var btnEnable: Button
    private lateinit var tvToday: TextView
    private lateinit var tvSplit: TextView
    private lateinit var tvTodayTime: TextView
    private lateinit var tvStats: TextView
    private lateinit var chart30: BarChartView
    private lateinit var chartHours: BarChartView
    private lateinit var tvPeak: TextView
    private lateinit var tvHistory: TextView
    private lateinit var btnHistoryMore: Button
    private lateinit var swOverlay: Switch
    private lateinit var swDebug: Switch
    private lateinit var debugBox: LinearLayout
    private lateinit var tvDebug: TextView

    private var showAllHistory = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnEnable = findViewById(R.id.btnEnable)
        tvToday = findViewById(R.id.tvToday)
        tvSplit = findViewById(R.id.tvSplit)
        tvTodayTime = findViewById(R.id.tvTodayTime)
        tvStats = findViewById(R.id.tvStats)
        chart30 = findViewById(R.id.chart30)
        chartHours = findViewById(R.id.chartHours)
        tvPeak = findViewById(R.id.tvPeak)
        tvHistory = findViewById(R.id.tvHistory)
        btnHistoryMore = findViewById(R.id.btnHistoryMore)
        swOverlay = findViewById(R.id.swOverlay)
        swDebug = findViewById(R.id.swDebug)
        debugBox = findViewById(R.id.debugBox)
        tvDebug = findViewById(R.id.tvDebug)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnHistoryMore.setOnClickListener {
            showAllHistory = !showAllHistory
            refresh()
        }

        swOverlay.isChecked = Stats.overlayOn(this)
        swOverlay.setOnCheckedChangeListener { _, on -> Stats.setOverlay(this, on) }

        swDebug.isChecked = Stats.debugOn(this)
        swDebug.setOnCheckedChangeListener { _, on ->
            Stats.setDebug(this, on)
            refresh()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Bugünü sıfırla")
                .setMessage("Bugünkü sayım ve süre silinsin mi?")
                .setPositiveButton("Sıfırla") { _, _ -> Stats.resetToday(this); refresh() }
                .setNegativeButton("Vazgeç", null)
                .show()
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            Stats.clearLog(this)
            refresh()
        }
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

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, CounterService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    private fun shortDate(d: LocalDate) =
        "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, tr)}"

    private fun refresh() {
        refreshStatus()
        refreshToday()
        refreshStats()
        refresh30Days()
        refreshHours()
        refreshHistory()

        val dbg = Stats.debugOn(this)
        debugBox.visibility = if (dbg) View.VISIBLE else View.GONE
        if (dbg) tvDebug.text = Stats.readLog(this).ifEmpty { "(henüz kayıt yok — Reels/Shorts'ta kaydır)" }
    }

    private fun refreshStatus() {
        if (isServiceEnabled()) {
            tvStatus.text = "✅ Sayaç açık. Reels / Shorts kaydırmaların sayılıyor."
            tvStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
            btnEnable.visibility = View.GONE
        } else {
            tvStatus.text = "⚠️ Sayaç kapalı.\n\nAyarlar → Erişilebilirlik → Yüklü uygulamalar → " +
                "\"Shorts Sayacı\"nı aç.\n\nGri/kilitliyse: Ayarlar → Uygulamalar → Shorts Sayacı → " +
                "sağ üst ⋮ → \"Kısıtlanmış ayarlara izin ver\", sonra tekrar dene."
            tvStatus.setBackgroundColor(Color.parseColor("#FFF3E0"))
            btnEnable.visibility = View.VISIBLE
        }
    }

    private fun refreshToday() {
        val today = Stats.today()
        tvToday.text = Stats.day(this, today).toString()
        tvSplit.text = "Instagram: ${Stats.day(this, today, Stats.APP_IG)}   •   " +
            "YouTube: ${Stats.day(this, today, Stats.APP_YT)}"
        val t = Stats.time(this, today)
        tvTodayTime.text = "⏱ Bugün izleme süresi: ${if (t == 0L) "0 dk" else Stats.fmtTime(t)}"
    }

    private fun refreshStats() {
        val now = LocalDate.now()
        val first = Stats.firstDay(this)
        val days = (ChronoUnit.DAYS.between(first, now) + 1).coerceIn(1, 3650)

        var best = 0
        var bestDay: LocalDate? = null
        var week = 0
        var month = 0
        val monday = now.with(DayOfWeek.MONDAY)
        for (i in 0 until days) {
            val d = now.minusDays(i)
            val c = Stats.day(this, d.toString())
            if (c > best) { best = c; bestDay = d }
            if (!d.isBefore(monday)) week += c
            if (d.month == now.month && d.year == now.year) month += c
        }

        val total = Stats.total(this)
        val timeTotal = Stats.timeTotal(this)
        val avg = total.toDouble() / days
        val perVideo = if (total > 0) timeTotal / total else 0L

        val sb = StringBuilder()
        sb.append(String.format(tr, "Günlük ortalama : %.1f video\n", avg))
        sb.append("Günlük ort. süre: ${Stats.fmtTime(timeTotal / days).let { if (timeTotal == 0L) "0 dk" else it }}\n")
        sb.append("En çok          : ")
            .append(if (bestDay != null) "$best video (${shortDate(bestDay)})" else "-").append('\n')
        sb.append("Bu hafta        : $week video\n")
        sb.append("Bu ay           : $month video\n")
        sb.append("Toplam          : $total video\n")
        sb.append("                  (IG ${Stats.total(this, Stats.APP_IG)} / YT ${Stats.total(this, Stats.APP_YT)})\n")
        sb.append("Toplam süre     : ${if (timeTotal == 0L) "0 dk" else Stats.fmtTime(timeTotal)}\n")
        sb.append("Video başına    : ${if (perVideo > 0) "$perVideo sn" else "-"}\n")
        sb.append("Takip edilen gün: $days")
        tvStats.text = sb.toString()
    }

    private fun refresh30Days() {
        val now = LocalDate.now()
        val n = 30
        val values = IntArray(n)
        val labels = Array(n) { "" }
        val tips = Array(n) { "" }
        for (i in 0 until n) {
            val d = now.minusDays((n - 1 - i).toLong())
            val ds = d.toString()
            values[i] = Stats.day(this, ds)
            if (i % 5 == 4 || i == 0) labels[i] = d.dayOfMonth.toString()
            val t = Stats.time(this, ds)
            tips[i] = "${shortDate(d)}: ${values[i]} video" + if (t > 0) " • ${Stats.fmtTime(t)}" else ""
        }
        chart30.highlight = n - 1
        chart30.setData(values, labels, tips)
    }

    private fun refreshHours() {
        val now = LocalDate.now()
        val values = IntArray(24)
        for (i in 0 until 30) {
            val ds = now.minusDays(i.toLong()).toString()
            for (h in 0..23) values[h] += Stats.hour(this, ds, h)
        }
        val labels = Array(24) { h -> if (h % 3 == 0) String.format(tr, "%02d", h) else "" }
        val sum = values.sum()
        val tips = Array(24) { h ->
            val pct = if (sum > 0) values[h] * 100 / sum else 0
            String.format(tr, "%02d:00–%02d:00: %d video (%%%d)", h, (h + 1) % 24, values[h], pct)
        }
        chartHours.highlight = LocalTime.now().hour
        chartHours.setData(values, labels, tips)

        if (sum == 0) {
            tvPeak.text = "Henüz veri yok."
        } else {
            var peak = 0
            for (h in 1..23) if (values[h] > values[peak]) peak = h
            // En yoğun 3 saatlik dilim
            var best3 = 0
            var best3Start = 0
            for (h in 0..23) {
                val s = values[h] + values[(h + 1) % 24] + values[(h + 2) % 24]
                if (s > best3) { best3 = s; best3Start = h }
            }
            tvPeak.text = String.format(
                tr,
                "En yoğun saat: %02d:00–%02d:00\nEn yoğun 3 saat: %02d:00–%02d:00 (videoların %%%d'i)",
                peak, (peak + 1) % 24, best3Start, (best3Start + 3) % 24, best3 * 100 / sum
            )
        }
    }

    private fun refreshHistory() {
        val now = LocalDate.now()
        val first = Stats.firstDay(this)
        val allDays = (ChronoUnit.DAYS.between(first, now) + 1).coerceIn(1, 3650).toInt()
        val shown = if (showAllHistory) allDays else minOf(allDays, 14)

        val sb = StringBuilder()
        var curMonth = -1
        var curYear = -1
        for (i in 0 until shown) {
            val d = now.minusDays(i.toLong())
            if (d.monthValue != curMonth || d.year != curYear) {
                curMonth = d.monthValue
                curYear = d.year
                // Ay başlığı + o ayın toplamı
                var mTotal = 0
                var mTime = 0L
                var x = d.withDayOfMonth(1)
                while (!x.isAfter(d) && x.month == d.month) {
                    mTotal += Stats.day(this, x.toString())
                    mTime += Stats.time(this, x.toString())
                    x = x.plusDays(1)
                }
                if (sb.isNotEmpty()) sb.append('\n')
                val mName = d.month.getDisplayName(TextStyle.FULL, tr)
                sb.append("── $mName ${d.year}: $mTotal video")
                if (mTime > 0) sb.append(" • ${Stats.fmtTime(mTime)}")
                sb.append(" ──\n")
            }
            val ds = d.toString()
            val c = Stats.day(this, ds)
            val ig = Stats.day(this, ds, Stats.APP_IG)
            val yt = Stats.day(this, ds, Stats.APP_YT)
            val t = Stats.time(this, ds)
            val name = if (i == 0) "Bugün" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, tr)
            sb.append(String.format(tr, "%2d %-5s %5d  IG %-4d YT %-4d", d.dayOfMonth, name, c, ig, yt))
            if (t > 0) sb.append(" ").append(Stats.fmtTime(t))
            sb.append('\n')
        }
        tvHistory.text = sb.toString().trimEnd()

        if (allDays <= 14) {
            btnHistoryMore.visibility = View.GONE
        } else {
            btnHistoryMore.visibility = View.VISIBLE
            btnHistoryMore.text = if (showAllHistory) "Daha az göster" else "Tüm geçmişi göster ($allDays gün)"
        }
    }
}
