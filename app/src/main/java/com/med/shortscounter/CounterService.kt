package com.med.shortscounter

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Instagram Reels ve YouTube Shorts'taki dikey video kaydırmalarını sayar.
 *
 * Yöntem:
 *  - Sadece ekranın büyük kısmını kaplayan, dikey kaydırılan listeleri dinler.
 *  - Her kaydırma olayında zamanlayıcıyı yeniler; kaydırma ~220 ms durunca
 *    "oturdu" kabul edilir ve o anki video sırası (fromIndex) ile son oturulan
 *    sıra arasındaki fark sayaca eklenir. Böylece:
 *      * hızlı art arda kaydırmalar kaçmaz (fark toplu eklenir),
 *      * yarıda bırakılan kaydırmalar sayılmaz (sıra değişmez).
 */
class CounterService : AccessibilityService() {

    companion object {
        const val PKG_IG = "com.instagram.android"
        const val PKG_YT = "com.google.android.youtube"

        /** View ID'sinde bunlar geçiyorsa kesin Reels/Shorts listesidir. */
        private val REEL_HINTS = listOf("clips", "reel", "shorts")

        /** Normal akış (feed) listeleri — sayılmaz. */
        private val BLOCKED_IDS = setOf("android:id/list")

        private const val SETTLE_MS = 220L

        /** Tek oturuşta bundan büyük sıçrama = liste yenilendi, 1 say. */
        private const val MAX_JUMP = 5

        /** Ignore when foreground changes to these (overlay'i gizlemesin). */
        private val IGNORE_FG = setOf("com.android.systemui")
    }

    private class Track(val pkg: String, var id: String) {
        var settledIndex = -1
        var lastFrom = -1
        var lastTo = -1
        var accumDy = 0
        var height = 1
        var idMatch = false
        var pending: Runnable? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tracks = HashMap<String, Track>()

    private var wm: WindowManager? = null
    private var overlay: TextView? = null
    private var screenH = 0
    private var foregroundPkg: String? = null

    // İzleme süresi takibi: Reels/Shorts'taki iki kaydırma arası süre
    // (en fazla MAX_GAP_SEC) izleme süresi olarak eklenir.
    private var lastActiveAt = 0L
    private var lastActivePkg: String? = null
    private val MAX_GAP_SEC = 120L

    // Oturum takibi: 3 dakikadan uzun ara verilince yeni oturum başlar
    private val SESSION_GAP_MS = 180_000L
    private var sessionEndAt = 0L
    private var sessionSec = 0L
    private var sessionVideos = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WindowManager::class.java)
        screenH = resources.displayMetrics.heightPixels
        Stats.log(this, "Servis bağlandı (ekran yüksekliği=$screenH)")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onForeground(pkg)
            AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                if (pkg == PKG_IG || pkg == PKG_YT) onScroll(e, pkg)
        }
    }

    private fun onForeground(pkg: String) {
        if (pkg in IGNORE_FG || pkg == packageName) return
        if (pkg != foregroundPkg) {
            // Reels/Shorts'tan çıkıldı: son videonun izlenme süresini ekle
            if (lastActivePkg != null && pkg != lastActivePkg) {
                finishVideo(lastActivePkg!!)
                Stats.updateSessionMax(this, sessionSec, sessionVideos)
                sessionEndAt = SystemClock.elapsedRealtime()
                lastActiveAt = 0L
                lastActivePkg = null
            }
            foregroundPkg = pkg
            updateOverlay()
        }
    }

    private fun onScroll(e: AccessibilityEvent, pkg: String) {
        if (foregroundPkg != pkg) {
            foregroundPkg = pkg
            updateOverlay()
        }

        val src = e.source ?: return
        val id = src.viewIdResourceName ?: ""
        val bounds = Rect()
        src.getBoundsInScreen(bounds)
        val h = bounds.height()

        // Küçük listeler (yorumlar, yatay raflar vs.) değil, tam ekran liste olmalı
        if (screenH > 0 && h < screenH * 0.6) return

        // Yatay kaydırmaları at
        if (Build.VERSION.SDK_INT >= 28 && e.scrollDeltaX != 0 && e.scrollDeltaY == 0) return

        if (id in BLOCKED_IDS) return

        val key = "$pkg/${e.windowId}/${src.hashCode()}"
        val t = tracks.getOrPut(key) { Track(pkg, id) }
        t.id = id
        t.lastFrom = e.fromIndex
        t.lastTo = e.toIndex
        t.height = h.coerceAtLeast(1)
        t.idMatch = REEL_HINTS.any { id.contains(it, ignoreCase = true) }
        if (Build.VERSION.SDK_INT >= 28) t.accumDy += e.scrollDeltaY

        t.pending?.let(handler::removeCallbacks)
        val r = Runnable { settle(t) }
        t.pending = r
        handler.postDelayed(r, SETTLE_MS)

        // Çok fazla eski liste birikmesin
        if (tracks.size > 30) {
            tracks.entries.removeAll { it.value !== t && it.value.pending == null }
        }
    }

    private fun settle(t: Track) {
        t.pending = null
        val from = t.lastFrom
        val to = t.lastTo
        val dy = t.accumDy
        t.accumDy = 0
        var n = 0
        val shortPkg = if (t.pkg == PKG_IG) "IG" else "YT"

        if (from >= 0) {
            // Reels/Shorts oturduğunda ekranda tek video görünür (from == to).
            // Normal akışta genelde birden çok öğe görünür -> sayma.
            val singleVisible = to < 0 || to == from
            if (!t.idMatch && !singleVisible) {
                Stats.log(this, "$shortPkg ATLA (akış?) id=${t.id} from=$from to=$to")
                t.settledIndex = from
                return
            }
            n = if (t.settledIndex >= 0) {
                abs(from - t.settledIndex)
            } else {
                // Bu listede ilk kaydırma: önceki sırayı bilmiyoruz
                if (Build.VERSION.SDK_INT >= 28) {
                    if (abs(dy) > t.height / 3) 1 else 0
                } else {
                    if (from > 0) 1 else 0
                }
            }
            if (n > MAX_JUMP) n = 1
            t.settledIndex = from
        } else if (t.idMatch && Build.VERSION.SDK_INT >= 28) {
            // Sıra bilgisi gelmiyorsa kaydırma mesafesinden hesapla
            n = (abs(dy).toFloat() / t.height).roundToInt().coerceAtMost(MAX_JUMP)
        }

        Stats.log(this, "$shortPkg id=${t.id.ifEmpty { "?" }} from=$from to=$to dy=$dy -> +$n")

        if (n > 0) {
            Stats.add(this, appKey(t.pkg), n)
            markActive(t.pkg, n)
            updateOverlay()
        }
    }

    private fun appKey(pkg: String) = if (pkg == PKG_IG) Stats.APP_IG else Stats.APP_YT

    /**
     * Yeni videoya geçildi: önceki videoda geçen süreyi ve süre dilimini kaydet,
     * oturumu güncelle, süreyi yeniden başlat.
     */
    private fun markActive(pkg: String, n: Int) {
        val now = SystemClock.elapsedRealtime()
        var newSession: Boolean
        if (lastActivePkg == pkg && lastActiveAt > 0) {
            newSession = now - lastActiveAt > SESSION_GAP_MS
            finishVideo(pkg)
        } else {
            newSession = sessionEndAt == 0L || now - sessionEndAt > SESSION_GAP_MS
        }
        if (newSession) {
            Stats.updateSessionMax(this, sessionSec, sessionVideos)
            Stats.startSession(this)
            sessionSec = 0
            sessionVideos = 0
        }
        sessionVideos += n
        Stats.updateSessionMax(this, sessionSec, sessionVideos)
        sessionEndAt = now
        lastActiveAt = now
        lastActivePkg = pkg
    }

    /** Ekrandaki videonun izlenme süresini ekle (en fazla MAX_GAP_SEC). */
    private fun finishVideo(pkg: String) {
        if (lastActiveAt <= 0) return
        val raw = (SystemClock.elapsedRealtime() - lastActiveAt) / 1000
        val sec = raw.coerceIn(0, MAX_GAP_SEC)
        Stats.addTime(this, appKey(pkg), sec)
        Stats.addBucket(this, Stats.bucketOf(raw))
        sessionSec += sec
    }

    // ---------------- Ekrandaki küçük sayaç ----------------

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).roundToInt()

    private fun updateOverlay() {
        val show = Stats.overlayOn(this) && (foregroundPkg == PKG_IG || foregroundPkg == PKG_YT)
        if (!show) {
            removeOverlay()
            return
        }
        val tv = overlay ?: createOverlay() ?: return
        tv.text = "▶ ${Stats.day(this, Stats.today())}"
    }

    private fun createOverlay(): TextView? {
        val w = wm ?: return null
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0x99000000.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(90)
        }
        return try {
            w.addView(tv, lp)
            overlay = tv
            tv
        } catch (ex: Exception) {
            Stats.log(this, "Overlay eklenemedi: ${ex.message}")
            null
        }
    }

    private fun removeOverlay() {
        val tv = overlay ?: return
        try {
            wm?.removeView(tv)
        } catch (_: Exception) {
        }
        overlay = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }
}
