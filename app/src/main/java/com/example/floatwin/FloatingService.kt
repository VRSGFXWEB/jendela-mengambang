package com.example.floatwin

import android.app.*
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var picker: View? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("fw", "Jendela Mengambang", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "fw")
            .setSmallIcon(android.R.drawable.ic_menu_more)
            .setContentTitle("Jendela mengambang aktif").build())
        getSharedPreferences("p", MODE_PRIVATE).edit().putBoolean("active", true).apply()
        showBubble()
    }

    private fun lp(w: Int, h: Int, focus: Boolean = false) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        if (focus) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun showBubble() {
        val d = resources.displayMetrics.density
        val size = (56 * d).toInt()
        val v = TextView(this).apply {
            text = "▢"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC3F51B5.toInt()) }
        }
        val p = lp(size, size).apply { x = 20; y = 300 }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    p.x = ox + dx; p.y = oy + dy; wm.updateViewLayout(v, p)
                }
                MotionEvent.ACTION_UP -> if (!moved) showPicker()
            }
            true
        }
        wm.addView(v, p); bubble = v
    }

    private fun showPicker() {
        if (picker != null) return
        val pm = packageManager
        val items = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
        val shown = items.toMutableList()
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val last = getSharedPreferences("p", MODE_PRIVATE).getString("last", null)

        val root = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()); setOnClickListener { closePicker() } }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { setColor(0xF2101820.toInt()); cornerRadius = dp(28).toFloat(); setStroke(dp(1), 0x33FFFFFF) }
            clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND
            isClickable = true
        }
        val search = EditText(this).apply {
            hint = "Cari aplikasi..."; setHintTextColor(0xFF8A97A6.toInt()); setTextColor(Color.WHITE)
            isSingleLine = true; textSize = 15f; setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply { setColor(0xFF0C131C.toInt()); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0x33FFFFFF) }
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, -2))

        if (last != null) {
            val lbl = try { pm.getApplicationLabel(pm.getApplicationInfo(last, 0)).toString() } catch (_: Throwable) { null }
            if (lbl != null) panel.addView(TextView(this).apply {
                text = "↺  Tampilkan lagi: $lbl"; setTextColor(0xFF6FD3FF.toInt()); textSize = 14f
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = GradientDrawable().apply { setColor(0x226FD3FF); cornerRadius = dp(18).toFloat() }
                setOnClickListener { launch(last); closePicker() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, c: View?, parent: ViewGroup): View {
                val row = LinearLayout(this@FloatingService).apply { setPadding(dp(8), dp(8), dp(8), dp(8)); gravity = Gravity.CENTER_VERTICAL }
                row.addView(ImageView(this@FloatingService).apply { setImageDrawable(shown[i].first.loadIcon(pm)) },
                    LinearLayout.LayoutParams(dp(40), dp(40)))
                row.addView(TextView(this@FloatingService).apply {
                    text = shown[i].second; setTextColor(Color.WHITE); textSize = 16f; setPadding(dp(14), 0, 0, 0)
                })
                return row
            }
        }
        val list = ListView(this).apply {
            this.adapter = adapter; divider = null; dividerHeight = 0
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setOnItemClickListener { _, _, i, _ -> launch(shown[i].first.activityInfo.packageName); closePicker() }
        }
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                shown.clear(); shown.addAll(items.filter { it.second.lowercase().contains(q) })
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        root.addView(panel, FrameLayout.LayoutParams(dp(320), dp(500), Gravity.CENTER))
        val p = lp(-1, -1, true).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN }
        wm.addView(root, p)
        picker = root
    }

    private fun closePicker() { picker?.let { wm.removeView(it) }; picker = null }

    private fun launch(pkg: String) {
        val dm = resources.displayMetrics
        val pct = getSharedPreferences("p", MODE_PRIVATE).getInt("size", 60).coerceAtLeast(30)
        val w = dm.widthPixels * pct / 100; val h = dm.heightPixels * pct / 100
        val l = (dm.widthPixels - w) / 2; val t = (dm.heightPixels - h) / 2
        val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h))
        try { // 5 = WINDOWING_MODE_FREEFORM (API tersembunyi, mungkin ditolak sistem)
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.java).invoke(opts, 5)
        } catch (_: Throwable) {}
        getSharedPreferences("p", MODE_PRIVATE).edit().putString("last", pkg).apply()
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i, opts.toBundle())
    }

    override fun onDestroy() {
        getSharedPreferences("p", MODE_PRIVATE).edit().putBoolean("active", false).apply()
        bubble?.let { wm.removeView(it) }; closePicker(); super.onDestroy()
    }
}
