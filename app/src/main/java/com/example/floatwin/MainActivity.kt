package com.example.floatwin

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val BG = 0xFF05080D.toInt()
    private val CARD = 0xFF0C131C.toInt()
    private val CYAN = 0xFF6FD3FF.toInt()
    private val DIM = 0xFF8A97A6.toInt()
    private val prefs by lazy { getSharedPreferences("p", MODE_PRIVATE) }
    private lateinit var statusMain: TextView
    private lateinit var arrowBtn: TextView
    private lateinit var permOverlay: TextView
    private lateinit var permNotif: TextView
    private lateinit var chip: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun box(color: Int, r: Int, stroke: Int = 0x22FFFFFF) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); setStroke(dp(1), stroke)
    }
    private fun tv(t: String, sp: Float, c: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(c); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun hasOverlay() = Settings.canDrawOverlays(this)
    private fun hasNotif() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG; window.navigationBarColor = BG
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(40), dp(20), dp(32)) }
        scroll.addView(root)

        // Header
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv("Home", 32f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        chip = tv("", 12f, CYAN).apply { setPadding(dp(14), dp(8), dp(14), dp(8)); background = box(CARD, 20) }
        head.addView(chip); root.addView(head)

        // Brand
        root.addView(tv("F L O A T I N G   W I N D O W", 11f, CYAN).apply { setPadding(0, dp(28), 0, 0) })
        root.addView(tv("FLOATWIN", 40f, bold = true).apply { letterSpacing = 0.25f })
        root.addView(tv("v1.0", 13f, DIM))

        // Main card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF10304A.toInt(), 0xFF0A1622.toInt())).apply {
                cornerRadius = dp(24).toFloat(); setStroke(dp(1), 0x44FFFFFF)
            }
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(tv("🚀", 26f).apply { gravity = Gravity.CENTER; background = box(0x33FFFFFF, 16) },
            LinearLayout.LayoutParams(dp(60), dp(60)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        statusMain = tv("", 13f, DIM)
        col.addView(statusMain); col.addView(tv("Jendela Mengambang", 20f, bold = true))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        arrowBtn = tv("→", 24f, Color.BLACK, true).apply {
            gravity = Gravity.CENTER; background = box(Color.WHITE, 30); setOnClickListener { toggle() }
        }
        row.addView(arrowBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        card.addView(row)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })

        // Size
        val sizeRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sizeRow.addView(tv("Ukuran Jendela", 20f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val sizeVal = tv("${prefs.getInt("size", 60)}%", 14f, CYAN)
        sizeRow.addView(sizeVal)
        root.addView(sizeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        val bar = SeekBar(this).apply {
            max = 90; progress = prefs.getInt("size", 60)
            progressTintList = ColorStateList.valueOf(CYAN); thumbTintList = ColorStateList.valueOf(CYAN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) {
                    val v = p.coerceAtLeast(30); prefs.edit().putInt("size", v).apply(); sizeVal.text = "$v%"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // Permissions
        root.addView(tv("P E R I Z I N A N", 12f, DIM), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(32) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = box(CARD, 18) }
        permOverlay = permRow(list, "Tampilkan di atas aplikasi lain") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        permNotif = permRow(list, "Notifikasi") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        root.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        root.addView(tv("Aktifkan 'Jendela freeform' di Opsi Pengembang supaya aplikasi bisa tampil sebagai jendela kecil.", 12f, DIM),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) })
        setContentView(scroll)
    }

    private fun permRow(parent: LinearLayout, label: String, onClick: () -> Unit): TextView {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); setOnClickListener { onClick() } }
        r.addView(tv(label, 15f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val st = tv("", 13f, DIM); r.addView(st); r.addView(tv("  ›", 18f, DIM))
        parent.addView(r); return st
    }

    private fun toggle() {
        if (prefs.getBoolean("active", false)) { stopService(Intent(this, FloatingService::class.java)); refresh(); return }
        if (!hasOverlay()) {
            Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain' dulu", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        if (!hasNotif()) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, FloatingService::class.java))
        arrowBtn.postDelayed({ refresh() }, 300)
    }

    private fun refresh() {
        val on = prefs.getBoolean("active", false)
        statusMain.text = if (on) "Sedang aktif" else "Aktifkan"
        arrowBtn.text = if (on) "■" else "→"
        chip.text = if (on) "● ON" else "○ OFF"; chip.setTextColor(if (on) CYAN else DIM)
        permOverlay.text = if (hasOverlay()) "Diizinkan" else "Belum"
        permOverlay.setTextColor(if (hasOverlay()) CYAN else 0xFFFF7A7A.toInt())
        permNotif.text = if (hasNotif()) "Diizinkan" else "Belum"
        permNotif.setTextColor(if (hasNotif()) CYAN else 0xFFFF7A7A.toInt())
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { super.onRequestPermissionsResult(c, p, r); refresh() }
}
