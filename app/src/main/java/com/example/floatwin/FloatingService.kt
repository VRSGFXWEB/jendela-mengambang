package com.example.floatwin

import android.app.*
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import kotlin.math.abs
import kotlin.math.hypot

class FloatingService : Service() {
    companion object { @Volatile var running = false }

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var trash: TextView? = null
    private var picker: View? = null
    @Volatile private var cached: List<Pair<ResolveInfo, String>>? = null
    private val icons = HashMap<String, Drawable>()
    private val ui = Handler(Looper.getMainLooper())
    private val density get() = resources.displayMetrics.density

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("fw", "Jendela Mengambang", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(this, 0,
            Intent(this, FloatingService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "fw")
            .setSmallIcon(android.R.drawable.ic_menu_more)
            .setContentTitle("Jendela mengambang aktif")
            .setContentText("Tap Matikan untuk berhenti")
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_delete), "Matikan", stopPi).build())
            .setOngoing(true).build()
        try { startForeground(1, n) } catch (e: Exception) { stopSelf(); return }
        running = true
        getSharedPreferences("p", MODE_PRIVATE).edit().putBoolean("active", true).apply()
        Thread { cached = loadApps() }.start()
        showBubble()
    }

    private fun loadApps(): List<Pair<ResolveInfo, String>> {
        val pm = packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun screen(): Point {
        val p = Point()
        getSystemService(DisplayManager::class.java).getDisplay(0).getRealSize(p)
        return p
    }

    private fun lp(w: Int, h: Int, focus: Boolean = false) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        if (focus) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    // ---------- Tombol bulat ----------
    private fun bubbleSize() = (56 * density).toInt()

    private fun showBubble() {
        val size = bubbleSize()
        val v = TextView(this).apply {
            text = "▢"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC3F51B5.toInt()) }
        }
        val p = lp(size, size).apply { x = 20; y = 300 }
        bubbleLp = p
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (!moved && (abs(dx) > 12 || abs(dy) > 12)) { moved = true; showTrash() }
                    if (moved) {
                        val s = screen()
                        p.x = (ox + dx).coerceIn(0, (s.x - size).coerceAtLeast(0))
                        p.y = (oy + dy).coerceIn(0, (s.y - size).coerceAtLeast(0))
                        wm.updateViewLayout(v, p)
                        setTrashActive(overTrash(p))
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showPicker()
                    else { val over = overTrash(p); hideTrash(); if (over) stopSelf() }
                }
                MotionEvent.ACTION_CANCEL -> hideTrash()
            }
            true
        }
        wm.addView(v, p); bubble = v
    }

    // ---------- Ikon sampah ----------
    private fun trashCenter(): PointF { val s = screen(); return PointF(s.x / 2f, s.y - 100 * density) }

    private fun overTrash(p: WindowManager.LayoutParams): Boolean {
        val t = trashCenter(); val half = bubbleSize() / 2f
        return hypot(p.x + half - t.x, p.y + half - t.y) < 80 * density
    }

    private fun showTrash() {
        if (trash != null) return
        val size = (64 * density).toInt(); val c = trashCenter()
        val v = TextView(this).apply {
            text = "🗑"; textSize = 26f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC222B36.toInt()); setStroke((1 * density).toInt(), 0x55FFFFFF) }
        }
        val p = lp(size, size).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            x = (c.x - size / 2).toInt(); y = (c.y - size / 2).toInt()
        }
        wm.addView(v, p); trash = v
    }

    private fun setTrashActive(on: Boolean) {
        trash?.apply {
            (background as GradientDrawable).setColor(if (on) 0xFFE53935.toInt() else 0xCC222B36.toInt())
            scaleX = if (on) 1.3f else 1f; scaleY = scaleX
        }
    }

    private fun hideTrash() { trash?.let { try { wm.removeView(it) } catch (_: Throwable) {} }; trash = null }

    // ---------- Pilih aplikasi ----------
    private fun showPicker() {
        if (picker != null) return
        val pm = packageManager
        val items = cached ?: loadApps().also { cached = it }
        val shown = items.toMutableList()
        val d = density
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
                val row = (c as? LinearLayout) ?: LinearLayout(this@FloatingService).apply {
                    setPadding(dp(8), dp(8), dp(8), dp(8)); gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(this@FloatingService), LinearLayout.LayoutParams(dp(40), dp(40)))
                    addView(TextView(this@FloatingService).apply { setTextColor(Color.WHITE); textSize = 16f; setPadding(dp(14), 0, 0, 0) })
                }
                val (ri, label) = shown[i]
                (row.getChildAt(0) as ImageView).setImageDrawable(icons.getOrPut(ri.activityInfo.packageName) { ri.loadIcon(pm) })
                (row.getChildAt(1) as TextView).text = label
                return row
            }
        }
        val list = ListView(this).apply {
            this.adapter = adapter; divider = null; dividerHeight = 0
            selector = ColorDrawable(Color.TRANSPARENT)
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

        val sc = screen()
        val w = minOf(dp(320), sc.x - dp(24)); val h = minOf(dp(500), sc.y - dp(24))
        root.addView(panel, FrameLayout.LayoutParams(w, h, Gravity.CENTER))
        wm.addView(root, lp(-1, -1, true).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN })
        picker = root
    }

    private fun closePicker() { picker?.let { try { wm.removeView(it) } catch (_: Throwable) {} }; picker = null }

    private fun launch(pkg: String) {
        val s = screen()
        val pct = getSharedPreferences("p", MODE_PRIVATE).getInt("size", 60).coerceAtLeast(30)
        val w = s.x * pct / 100; val h = s.y * pct / 100
        val l = (s.x - w) / 2; val t = (s.y - h) / 2
        val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h))
        try { // 5 = WINDOWING_MODE_FREEFORM (API tersembunyi, mungkin ditolak sistem)
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.java).invoke(opts, 5)
        } catch (_: Throwable) {}
        getSharedPreferences("p", MODE_PRIVATE).edit().putString("last", pkg).apply()
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i, opts.toBundle())
    }

    // Layar diputar (mis. main game): tombol dikembalikan ke dalam layar
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        closePicker(); hideTrash()
        ui.postDelayed({
            val p = bubbleLp ?: return@postDelayed; val v = bubble ?: return@postDelayed
            val s = screen(); val size = bubbleSize()
            p.x = p.x.coerceIn(0, (s.x - size).coerceAtLeast(0))
            p.y = p.y.coerceIn(0, (s.y - size).coerceAtLeast(0))
            try { wm.updateViewLayout(v, p) } catch (_: Throwable) {}
        }, 400)
    }

    override fun onDestroy() {
        running = false
        getSharedPreferences("p", MODE_PRIVATE).edit().putBoolean("active", false).apply()
        hideTrash(); closePicker()
        bubble?.let { try { wm.removeView(it) } catch (_: Throwable) {} }; bubble = null
        super.onDestroy()
    }
}
