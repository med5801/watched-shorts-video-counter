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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnEnable: Button
    private lateinit var tvToday: TextView
    private lateinit var tvSplit: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvWeek: TextView
    private lateinit var swOverlay: Switch
    private lateinit var swDebug: Switch
    private lateinit var debugBox: LinearLayout
    private lateinit var tvDebug: TextView

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
        tvTotal = findViewById(R.id.tvTotal)
        tvWeek = findViewById(R.id.tvWeek)
        swOverlay = findViewById(R.id.swOverlay)
        swDebug = findViewById(R.id.swDebug)
        debugBox = findViewById(R.id.debugBox)
        tvDebug = findViewById(R.id.tvDebug)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
                .setMessage("Bugünkü sayım silinsin mi?")
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

    private fun refresh() {
        val on = isServiceEnabled()
        if (on) {
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

        val today = Stats.today()
        tvToday.text = Stats.day(this, today).toString()
        tvSplit.text = "Instagram: ${Stats.day(this, today, Stats.APP_IG)}   •   " +
            "YouTube: ${Stats.day(this, today, Stats.APP_YT)}"
        tvTotal.text = "Tüm zamanlar: ${Stats.total(this)}"

        val tr = Locale("tr")
        val sb = StringBuilder()
        for (i in 0 until 7) {
            val d = LocalDate.now().minusDays(i.toLong())
            val name = if (i == 0) "Bugün" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, tr)
            val c = Stats.day(this, d.toString())
            val ig = Stats.day(this, d.toString(), Stats.APP_IG)
            val yt = Stats.day(this, d.toString(), Stats.APP_YT)
            sb.append(String.format(tr, "%-6s %5d   (IG %d / YT %d)\n", name, c, ig, yt))
        }
        tvWeek.text = sb.toString().trimEnd()

        val dbg = Stats.debugOn(this)
        debugBox.visibility = if (dbg) View.VISIBLE else View.GONE
        if (dbg) tvDebug.text = Stats.readLog(this).ifEmpty { "(henüz kayıt yok — Reels/Shorts'ta kaydır)" }
    }
}
