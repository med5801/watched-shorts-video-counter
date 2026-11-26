package com.med.shortscounter

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** "Sıralama" sekmesi. Sadece butona basınca sunucuya gider. */
class LeaderboardPage(private val act: Activity, private val ui: Ui) {

    val root: LinearLayout = ui.vbox()

    private lateinit var nameInput: EditText
    private lateinit var nameNote: TextView
    private lateinit var nameLocked: LinearLayout
    private lateinit var nameLockedText: TextView
    private lateinit var syncBtn: TextView
    private lateinit var status: TextView
    private lateinit var awardsBox: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private lateinit var listTitle: TextView
    private lateinit var listDesc: TextView
    private lateinit var listBox: LinearLayout

    private val chips = ArrayList<TextView>()
    private var selected = 0
    private var busy = false
    private var entries: List<Entry> = emptyList()

    init {
        if (!Leaderboard.configured()) {
            val c = ui.card()
            c.addView(ui.cardTitle("Sıralama henüz hazır değil", "Sunucu ayarları bir sonraki güncellemeyle gelecek."))
            root.addView(c, ui.matchWrap(16))
        } else {
            buildProfile()
            buildAwards()
            buildList()
            entries = Leaderboard.cached(act)
        }
    }

    // ---------------- Kurulum ----------------

    private fun buildProfile() {
        val card = ui.card()
        card.addView(ui.cardTitle("Sıralamaya katıl", "Sadece özet gönderilir: takma ad, toplamlar ve rekorlar."))

        nameInput = EditText(act).apply {
            hint = "Takma adın (2–20 karakter)"
            setText(Leaderboard.name(act))
            setTextColor(C.TEXT)
            setHintTextColor(C.SUB)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(20))
            background = ui.round(C.CARD2, 14)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
        }
        card.addView(nameInput)
        nameNote = ui.text(12f, C.AMBER, false, "⚠️ İsim bir kez seçilir, sonradan değiştirilemez.")
            .apply { setPadding(0, ui.dp(6), 0, 0) }
        card.addView(nameNote)

        // Kilitliyken gösterilen isim satırı
        nameLocked = ui.hbox().apply {
            background = ui.round(C.CARD2, 14)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
        }
        nameLockedText = ui.text(16f, C.TEXT, true, "")
        nameLocked.addView(nameLockedText, ui.weight1())
        nameLocked.addView(ui.text(12f, C.SUB, false, "🔒 kalıcı"))
        card.addView(nameLocked)

        syncBtn = ui.button("Verilerimi gönder ve sıralamayı güncelle", C.TEXT, C.ACCENT).apply {
            setOnClickListener { onSyncClick() }
        }
        card.addView(syncBtn, ui.matchWrap(12))

        status = ui.text(12f, C.SUB, false, "").apply { setPadding(0, ui.dp(10), 0, 0) }
        card.addView(status)
        root.addView(card, ui.matchWrap(16))
        updateNameUi()
    }

    private fun updateNameUi() {
        val locked = Leaderboard.nameLocked(act)
        nameInput.visibility = if (locked) View.GONE else View.VISIBLE
        nameNote.visibility = if (locked) View.GONE else View.VISIBLE
        nameLocked.visibility = if (locked) View.VISIBLE else View.GONE
        nameLockedText.text = Leaderboard.name(act)
        syncBtn.text = if (locked) "Verilerimi gönder ve sıralamayı güncelle" else "Sıralamaya katıl"
    }

    private fun buildAwards() {
        val card = ui.card()
        card.addView(ui.cardTitle("Ödüllerin", "Her kategoride kaçıncı olduğun"))
        awardsBox = ui.vbox()
        card.addView(awardsBox)
        root.addView(card)
    }

    private fun buildList() {
        val scroll = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false }
        chipsRow = ui.hbox()
        Leaderboard.categories.forEachIndexed { i, cat ->
            val chip = ui.text(13f, C.SUB, true, "${cat.emoji} ${cat.title}").apply {
                setPadding(ui.dp(14), ui.dp(9), ui.dp(14), ui.dp(9))
                setOnClickListener { selected = i; render() }
            }
            chips.add(chip)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = ui.dp(8) }
            chipsRow.addView(chip, lp)
        }
        scroll.addView(chipsRow)
        root.addView(scroll, ui.matchWrap(18))

        val card = ui.card()
        listTitle = ui.text(18f, C.TEXT, true, "")
        listDesc = ui.text(12f, C.SUB, false, "").apply { setPadding(0, ui.dp(2), 0, ui.dp(12)) }
        card.addView(listTitle)
        card.addView(listDesc)
        listBox = ui.vbox()
        card.addView(listBox)
        root.addView(card)
    }

    // ---------------- Eylemler ----------------

    private fun onSyncClick() {
        if (busy) return
        if (Leaderboard.nameLocked(act)) {
            sync(Leaderboard.name(act), firstJoin = false)
            return
        }
        val name = nameInput.text.toString().trim().replace(Regex("\\s+"), " ")
        if (name.length < 2) {
            status.setTextColor(C.AMBER)
            status.text = "Önce en az 2 karakterlik bir takma ad yaz."
            return
        }
        (act.getSystemService(InputMethodManager::class.java))
            ?.hideSoftInputFromWindow(nameInput.windowToken, 0)
        AlertDialog.Builder(act)
            .setTitle("İsmin \"$name\" olacak")
            .setMessage("Sıralamada bu isimle görüneceksin ve isim sonradan değiştirilemez. Onaylıyor musun?")
            .setPositiveButton("Onayla") { _, _ -> sync(name, firstJoin = true) }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun sync(name: String, firstJoin: Boolean) {
        if (busy) return
        busy = true
        syncBtn.alpha = 0.5f
        status.setTextColor(C.SUB)
        status.text = "Gönderiliyor…"
        Thread {
            try {
                Leaderboard.upload(act, name)
                if (firstJoin) {
                    Leaderboard.setName(act, name)
                    Leaderboard.lockName(act)
                }
                val list = Leaderboard.fetchAll(act)
                act.runOnUiThread {
                    entries = list
                    busy = false
                    syncBtn.alpha = 1f
                    updateNameUi()
                    render()
                }
            } catch (e: Exception) {
                act.runOnUiThread {
                    busy = false
                    syncBtn.alpha = 1f
                    status.setTextColor(C.RED)
                    status.text = "Hata: ${e.message ?: "bağlantı kurulamadı"}"
                }
            }
        }.start()
    }

    // ---------------- Çizim ----------------

    fun render() {
        if (!Leaderboard.configured()) return
        val me = Leaderboard.myId(act)
        val last = Leaderboard.lastFetch(act)

        if (!busy) {
            status.setTextColor(C.SUB)
            status.text = if (last == 0L) {
                "Henüz katılmadın. Butona bastığında özetin gönderilir ve liste bir kez çekilir."
            } else {
                "Son güncelleme: ${ago(last)} · ${entries.size} kişi"
            }
        }

        renderAwards(me)

        chips.forEachIndexed { i, chip ->
            val sel = i == selected
            chip.setTextColor(if (sel) C.TEXT else C.SUB)
            chip.background = ui.round(if (sel) C.ACCENT else C.CARD, 14)
        }

        val cat = Leaderboard.categories[selected]
        listTitle.text = "${cat.emoji} ${cat.title}"
        listDesc.text = cat.desc + (cat.requirement?.let { " · $it" } ?: "")
        listBox.removeAllViews()

        val ranked = Leaderboard.rank(entries, cat)
        if (ranked.isEmpty()) {
            listBox.addView(ui.text(13f, C.SUB, false,
                if (entries.isEmpty()) "Liste boş. Güncelle'ye basınca burada görünecek."
                else "Bu kategoride henüz uygun kimse yok."))
            return
        }
        val top = 50
        ranked.take(top).forEachIndexed { i, e -> listBox.addView(row(i + 1, e, cat, e.id == me), ui.matchWrap(if (i == 0) 0 else 6)) }
        val myIdx = ranked.indexOfFirst { it.id == me }
        if (myIdx >= top) {
            listBox.addView(ui.text(12f, C.SUB, false, "⋯").apply { gravity = Gravity.CENTER }, ui.matchWrap(6))
            listBox.addView(row(myIdx + 1, ranked[myIdx], cat, true), ui.matchWrap(6))
        }
    }

    private fun renderAwards(me: String?) {
        awardsBox.removeAllViews()
        val mine = entries.firstOrNull { it.id == me }
        if (mine == null) {
            awardsBox.addView(ui.text(13f, C.SUB, false, "Sıralamaya katılınca kazandığın ödüller burada görünür."))
            return
        }
        Leaderboard.categories.forEachIndexed { i, cat ->
            val ranked = Leaderboard.rank(entries, cat)
            val pos = ranked.indexOfFirst { it.id == me }
            val r = ui.hbox().apply {
                setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                background = ui.ripple(ui.round(if (pos in 0..2) 0xFF2B2648.toInt() else C.CARD2, 14), 14)
                isClickable = true
                setOnClickListener { selected = i; render() }
            }
            r.addView(ui.text(18f, C.TEXT, false, cat.emoji).apply { setPadding(0, 0, ui.dp(10), 0) })
            val mid = ui.vbox()
            mid.addView(ui.text(14f, C.TEXT, true, cat.title))
            mid.addView(ui.text(12f, C.SUB, false,
                if (pos >= 0) cat.fmt(mine) else (cat.requirement ?: "Henüz veri yok")))
            r.addView(mid, ui.weight1())
            val badge = when {
                pos < 0 -> "—"
                pos == 0 -> "🥇 1."
                pos == 1 -> "🥈 2."
                pos == 2 -> "🥉 3."
                else -> "${pos + 1}. / ${ranked.size}"
            }
            r.addView(ui.text(14f, if (pos in 0..2) C.AMBER else C.TEXT, true, badge))
            awardsBox.addView(r, ui.matchWrap(if (i == 0) 0 else 8))
        }
    }

    private fun row(rank: Int, e: Entry, cat: Category, isMe: Boolean): View {
        val r = ui.hbox().apply {
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
            background = ui.round(if (isMe) C.ACCENT_DIM else C.CARD2, 14)
        }
        val medal = when (rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "$rank."
        }
        r.addView(ui.text(if (rank <= 3) 20f else 14f, C.SUB, true, medal).apply {
            setWidth(ui.dp(44))
        })
        val mid = ui.vbox()
        mid.addView(ui.text(15f, C.TEXT, true, e.name + if (isMe) "  (sen)" else ""))
        mid.addView(ui.text(11f, C.SUB, false, "${e.videos} video · ${Stats.fmtTime(e.time)} · güncelleme ${agoIso(e.updatedAt)}"))
        r.addView(mid, ui.weight1())
        r.addView(ui.text(14f, if (rank <= 3) C.AMBER else C.TEXT, true, cat.fmt(e)).apply {
            gravity = Gravity.END
            setPadding(ui.dp(8), 0, 0, 0)
        })
        return r
    }

    // ---------------- Zaman yardımcıları ----------------

    private fun ago(millis: Long): String {
        val s = (System.currentTimeMillis() - millis) / 1000
        return when {
            s < 60 -> "az önce"
            s < 3600 -> "${s / 60} dk önce"
            s < 86400 -> "${s / 3600} sa önce"
            else -> "${s / 86400} gün önce"
        }
    }

    private fun agoIso(iso: String): String = try {
        ago(java.time.Instant.parse(iso).toEpochMilli())
    } catch (_: Exception) {
        "?"
    }
}
