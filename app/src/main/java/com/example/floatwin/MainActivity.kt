package com.example.floatwin

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*

class MainActivity : Activity() {
    private val BG = 0xFF05080D.toInt()
    private val CARD = 0xFF0B1220.toInt()
    private val ACCENT = 0xFF3D8BFF.toInt()
    private val ACCENT_L = 0xFF8FB8FF.toInt()
    private val DIM = 0xFF8A97A6.toInt()
    private val RED = 0xFFFF6B6B.toInt()
    private val ON_COLOR = 0xFFFF3B3B.toInt()   // merah = aktif
    private val OFF_COLOR = 0xFF2ECC71.toInt()  // hijau = nonaktif
    private val prefs by lazy { getSharedPreferences("p", MODE_PRIVATE) }
    private lateinit var statusMain: TextView
    private lateinit var toggleBtn: View
    private lateinit var pinnedSub: TextView
    private lateinit var permOverlay: TextView
    private lateinit var permNotif: TextView
    private lateinit var permBattery: TextView
    private var lastOn: Boolean? = null
    private var btnColor = OFF_COLOR

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun box(color: Int, r: Int, stroke: Int = 0x22FFFFFF) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); setStroke(dp(1), stroke)
    }
    private fun tv(t: String, sp: Float, c: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(c); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun hasOverlay() = Settings.canDrawOverlays(this)
    private fun hasBattery() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    private fun hasNotif() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == 0

    // Pratinjau ukuran jendela relatif ke layar
    inner class PreviewView(c: Context) : View(c) {
        var pct = 60
            set(v) { field = v; invalidate() }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = 0xFF3A4A63.toInt() }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D8BFF.toInt() }
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF070C14.toInt() }
        override fun onDraw(c: Canvas) {
            val r = RectF(dp(2).toFloat(), dp(2).toFloat(), width - dp(2).toFloat(), height - dp(2).toFloat())
            c.drawRoundRect(r, dp(10).toFloat(), dp(10).toFloat(), bg)
            c.drawRoundRect(r, dp(10).toFloat(), dp(10).toFloat(), line)
            val iw = r.width() * pct / 100f; val ih = r.height() * pct / 100f
            c.drawRoundRect(RectF(r.centerX() - iw / 2, r.centerY() - ih / 2, r.centerX() + iw / 2, r.centerY() + ih / 2),
                dp(6).toFloat(), dp(6).toFloat(), fill)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG; window.navigationBarColor = BG
        val dm = resources.displayMetrics
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(36), dp(20), dp(32)) }
        scroll.addView(root)

        // Banner
        val bw = dm.widthPixels - dp(40)
        val banner = ImageView(this).apply {
            setImageResource(R.drawable.banner); scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(24).toFloat()) }
            }
        }
        root.addView(banner, LinearLayout.LayoutParams(bw, bw * 405 / 1080))

        // Kartu utama: tombol polos, merah = aktif, hijau = nonaktif
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF123A78.toInt(), 0xFF0A1424.toInt())).apply {
                cornerRadius = dp(24).toFloat(); setStroke(dp(1), 0x44FFFFFF)
            }
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0) }
        col.addView(tv("Jendela Mengambang", 19f, bold = true))
        statusMain = tv("", 12.5f, ACCENT_L).apply { setPadding(0, dp(4), 0, 0) }
        col.addView(statusMain)
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        toggleBtn = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(btnColor) }
            setOnClickListener { toggle() }; press()
        }
        row.addView(toggleBtn, LinearLayout.LayoutParams(dp(54), dp(54)))
        card.addView(row)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        // Pilih aplikasi untuk tombol bulat
        root.addView(section("A P L I K A S I   D I   T O M B O L"))
        val pickRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = box(CARD, 20)
            setOnClickListener { startActivity(Intent(this@MainActivity, AppSelectActivity::class.java)) }; press()
        }
        val pickCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pickCol.addView(tv("Pilih aplikasi", 15f, bold = true))
        pinnedSub = tv("", 12.5f, DIM).apply { setPadding(0, dp(3), 0, 0) }
        pickCol.addView(pinnedSub)
        pickRow.addView(pickCol, LinearLayout.LayoutParams(0, -2, 1f))
        pickRow.addView(IconView(this, IconView.CHEVRON, DIM), LinearLayout.LayoutParams(dp(18), dp(18)))
        root.addView(pickRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // Ukuran jendela
        root.addView(section("U K U R A N   J E N D E L A"))
        val sizeCard = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = box(CARD, 20) }
        val start = prefs.getInt("size", 60).coerceAtLeast(30)
        val preview = PreviewView(this).apply { pct = start }
        sizeCard.addView(preview, LinearLayout.LayoutParams(dp(54), dp(54) * dm.heightPixels / dm.widthPixels))
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, 0, 0) }
        val sizeRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sizeRow.addView(tv("Besar jendela", 15f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val sizeVal = tv("$start%", 14f, ACCENT_L); sizeRow.addView(sizeVal)
        right.addView(sizeRow)
        val bar = SeekBar(this).apply {
            max = 90; progress = start
            progressTintList = ColorStateList.valueOf(ACCENT); thumbTintList = ColorStateList.valueOf(ACCENT_L)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) {
                    val v = p.coerceAtLeast(30); prefs.edit().putInt("size", v).apply()
                    sizeVal.text = "$v%"; preview.pct = v
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        right.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        right.addView(tv("Berlaku untuk aplikasi yang dibuka berikutnya", 12f, DIM))
        sizeCard.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(sizeCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // Cara pakai
        root.addView(section("C A R A   P A K A I"))
        val how = tv("1.  Pilih aplikasi di menu Aplikasi di Tombol\n2.  Nyalakan tombol hijau, jadi merah saat aktif\n3.  Tap tombol bulat untuk membuka daftar\n4.  Tahan tombol: tampilkan lagi jendela terakhir\n5.  Seret tombol ke ikon tempat sampah untuk mematikan", 13.5f, 0xFFC5CFDB.toInt()).apply {
            setLineSpacing(dp(6).toFloat(), 1f); setPadding(dp(18), dp(16), dp(18), dp(16)); background = box(CARD, 20)
        }
        root.addView(how, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // Perizinan
        root.addView(section("P E R I Z I N A N"))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = box(CARD, 20) }
        permOverlay = permRow(list, "Tampilkan di atas aplikasi lain") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        permNotif = permRow(list, "Notifikasi") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        permBattery = permRow(list, "Abaikan optimasi baterai") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        root.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        root.addView(tv("Aktifkan 'Jendela freeform' di Opsi Pengembang supaya aplikasi bisa tampil sebagai jendela kecil.", 12f, DIM),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18) })
        root.addView(tv("FloatWin by centralvex", 12f, 0xFF4F5B6B.toInt()).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        setContentView(scroll)

        // Animasi masuk bertahap
        for (i in 0 until root.childCount) {
            val c = root.getChildAt(i)
            c.alpha = 0f; c.translationY = dp(24).toFloat()
            c.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(380)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun section(t: String) = tv(t, 11f, DIM).apply { setPadding(dp(4), 0, 0, 0) }.also {
        it.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(26) }
    }

    private fun permRow(parent: LinearLayout, label: String, onClick: () -> Unit): TextView {
        if (parent.childCount > 0) parent.addView(View(this).apply { setBackgroundColor(0x14FFFFFF) }, LinearLayout.LayoutParams(-1, dp(1)))
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); setOnClickListener { onClick() }; press() }
        r.addView(tv(label, 14.5f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val pill = tv("", 12f).apply { setPadding(dp(12), dp(5), dp(12), dp(5)) }
        r.addView(pill, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
        r.addView(IconView(this, IconView.CHEVRON, DIM), LinearLayout.LayoutParams(dp(16), dp(16)))
        parent.addView(r); return pill
    }

    private fun setPill(t: TextView, ok: Boolean) {
        t.text = if (ok) "Diizinkan" else "Belum"
        t.setTextColor(if (ok) ACCENT_L else RED)
        t.background = box(if (ok) 0x223D8BFF else 0x22FF6B6B, 12, 0)
    }

    private fun toggle() {
        if (FloatingService.running) {
            prefs.edit().putBoolean("wanted", false).apply()
            stopService(Intent(this, FloatingService::class.java))
            toggleBtn.postDelayed({ refresh() }, 300); return
        }
        if (!hasOverlay()) {
            Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain' dulu", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        if (!hasNotif()) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        prefs.edit().putBoolean("wanted", true).apply()
        startForegroundService(Intent(this, FloatingService::class.java))
        toggleBtn.postDelayed({ refresh() }, 300)
    }

    private fun refresh() {
        val on = FloatingService.running
        statusMain.text = if (on) "Aktif. Ketuk tombol merah untuk mematikan" else "Nonaktif. Ketuk tombol hijau untuk menyalakan"
        val target = if (on) ON_COLOR else OFF_COLOR
        val bg = toggleBtn.background as GradientDrawable
        if (lastOn != null && lastOn != on) {
            ValueAnimator.ofObject(ArgbEvaluator(), btnColor, target).apply {
                duration = 350
                addUpdateListener { bg.setColor(it.animatedValue as Int) }
            }.start()
        } else bg.setColor(target)
        btnColor = target; lastOn = on
        val n = prefs.getStringSet("pinned", emptySet())?.size ?: 0
        pinnedSub.text = if (n == 0) "Belum dipilih, semua aplikasi akan tampil" else "$n aplikasi dipilih"
        setPill(permOverlay, hasOverlay()); setPill(permNotif, hasNotif()); setPill(permBattery, hasBattery())
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { super.onRequestPermissionsResult(c, p, r); refresh() }
}
