package com.example.floatwin

import android.animation.ValueAnimator
import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class FloatingService : Service() {
    companion object { @Volatile var running = false }

    private data class AppItem(val pkg: String, val label: String)

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var trash: FrameLayout? = null
    private var picker: View? = null
    private var fpsView: TextView? = null
    private var fpsLp: WindowManager.LayoutParams? = null
    private var fpsOn = false
    @Volatile private var cached: List<AppItem>? = null
    private val icons = ConcurrentHashMap<String, Drawable>()
    private val ui = Handler(Looper.getMainLooper())
    private val scr = Point()
    private val density get() = resources.displayMetrics.density
    private val prefs get() = getSharedPreferences("p", MODE_PRIVATE)

    // ---- pantau rotasi layar lewat DisplayManager (tetap jalan walau game di depan) ----
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(id: Int) { if (id == 0) ui.post { onScreenChanged() } }
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "fps") ui.post { setFps(prefs.getBoolean("fps", false)) }
        if (key == "sound") Sfx.enabled = prefs.getBoolean("sound", true)
    }

    // ---- penghitung frame (FPS tampilan sistem) ----
    private var frames = 0
    private var lastNs = 0L
    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(ns: Long) {
            if (!fpsOn) return
            if (lastNs == 0L) lastNs = ns
            frames++
            val d = ns - lastNs
            if (d >= 1_000_000_000L) {
                fpsView?.text = "FPS " + (frames * 1e9 / d).roundToInt()
                frames = 0; lastNs = ns
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { prefs.edit().putBoolean("wanted", false).apply(); stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Sfx.init(this)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val now = System.currentTimeMillis()
                if (prefs.getBoolean("wanted", false) && now - prefs.getLong("crash", 0L) > 15000) {
                    prefs.edit().putLong("crash", now).commit()
                    scheduleRestart()
                }
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("fw", "FloatWin", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(this, 0,
            Intent(this, FloatingService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "fw")
            .setSmallIcon(android.R.drawable.ic_menu_more)
            .setContentTitle("FloatWin aktif")
            .setContentText("Tap Matikan untuk berhenti")
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_delete), "Matikan", stopPi).build())
            .setOngoing(true).build()
        try { startForeground(1, n) } catch (e: Exception) { stopSelf(); return }
        running = true
        prefs.edit().putBoolean("active", true).apply()
        refreshScreen()
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, ui)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        try { showBubble() } catch (e: Exception) { stopSelf(); return }
        setFps(prefs.getBoolean("fps", false))
        // Muat daftar aplikasi pelan-pelan di belakang layar supaya tidak membebani game
        Thread {
            Thread.currentThread().priority = Thread.MIN_PRIORITY
            try { Thread.sleep(1500) } catch (_: Throwable) {}
            cached = buildItems()
        }.start()
    }

    private fun scheduleRestart() {
        val pi = PendingIntent.getForegroundService(this, 7, Intent(this, FloatingService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 2000, pi)
    }

    // Kalau ada aplikasi yang dipilih, cukup ambil itu saja (cepat dan ringan)
    private fun buildItems(): List<AppItem> {
        val pm = packageManager
        val pinned = prefs.getStringSet("pinned", emptySet()) ?: emptySet()
        if (pinned.isNotEmpty()) {
            val l = pinned.mapNotNull {
                try { AppItem(it, pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()) } catch (_: Throwable) { null }
            }.sortedBy { it.label.lowercase() }
            if (l.isNotEmpty()) return l
        }
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun refreshScreen() {
        getSystemService(DisplayManager::class.java).getDisplay(0).getRealSize(scr)
    }

    private fun lp(w: Int, h: Int, focus: Boolean = false) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        if (focus) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    // ---------- Tombol bulat ----------
    private fun bubbleSize() = (60 * density).toInt()

    private fun showBubble() {
        val size = bubbleSize()
        val v = ImageView(this).apply { setImageResource(R.drawable.bubble); scaleType = ImageView.ScaleType.FIT_CENTER }
        val p = lp(size, size).apply {
            x = prefs.getInt("bx", 8).coerceIn(0, (scr.x - size).coerceAtLeast(0))
            y = prefs.getInt("by", 300).coerceIn(0, (scr.y - size).coerceAtLeast(0))
        }
        bubbleLp = p
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        var moved = false; var longFired = false
        val longPress = Runnable {
            if (!moved) { longFired = true; v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); Sfx.pop(); restoreLast() }
        }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    refreshScreen() // ukuran layar selalu diambil ulang (aman saat layar miring)
                    sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false; longFired = false
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start()
                    ui.postDelayed(longPress, 450)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (!moved && (abs(dx) > 12 || abs(dy) > 12)) { moved = true; ui.removeCallbacks(longPress); showTrash() }
                    if (moved) {
                        p.x = (ox + dx).coerceIn(0, (scr.x - size).coerceAtLeast(0))
                        p.y = (oy + dy).coerceIn(0, (scr.y - size).coerceAtLeast(0))
                        wm.updateViewLayout(v, p)
                        setTrashActive(overTrash(p))
                    }
                }
                MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(longPress)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (moved) {
                        val over = overTrash(p); hideTrash()
                        if (over) { Sfx.pop(); prefs.edit().putBoolean("wanted", false).apply(); stopSelf() } else snap(v, p, size)
                    } else if (!longFired) { Sfx.pop(); showPicker() }
                }
                MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(longPress)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); hideTrash()
                }
            }
            true
        }
        v.scaleX = 0f; v.scaleY = 0f
        wm.addView(v, p); bubble = v
        v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator()).start()
    }

    private fun snap(v: View, p: WindowManager.LayoutParams, size: Int) {
        val m = (4 * density).toInt()
        val target = if (p.x + size / 2 < scr.x / 2) m else (scr.x - size - m).coerceAtLeast(0)
        prefs.edit().putInt("bx", target).putInt("by", p.y).apply()
        ValueAnimator.ofInt(p.x, target).apply {
            duration = 220; interpolator = DecelerateInterpolator()
            addUpdateListener {
                p.x = it.animatedValue as Int
                try { wm.updateViewLayout(v, p) } catch (_: Throwable) {}
            }
        }.start()
    }

    private fun restoreLast() {
        val last = prefs.getString("last", null)
        if (last == null) Toast.makeText(this, "Belum ada aplikasi yang dibuka", Toast.LENGTH_SHORT).show()
        else launch(last)
    }

    // ---------- Penghitung FPS ----------
    private fun setFps(on: Boolean) {
        if (on == fpsOn && (fpsView != null) == on) return
        fpsOn = on
        if (on) {
            val v = TextView(this).apply {
                text = "FPS --"; textSize = 12f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                setPadding((10 * density).toInt(), (5 * density).toInt(), (10 * density).toInt(), (5 * density).toInt())
                background = GradientDrawable().apply { setColor(0xAA05080D.toInt()); cornerRadius = 40f; setStroke((1 * density).toInt(), 0x663D8BFF) }
            }
            val p = lp(-2, -2).apply {
                x = prefs.getInt("fx", (12 * density).toInt()).coerceIn(0, scr.x.coerceAtLeast(1))
                y = prefs.getInt("fy", (40 * density).toInt()).coerceIn(0, scr.y.coerceAtLeast(1))
            }
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
            v.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = (ox + (e.rawX - sx).toInt()).coerceIn(0, (scr.x - v.width).coerceAtLeast(0))
                        p.y = (oy + (e.rawY - sy).toInt()).coerceIn(0, (scr.y - v.height).coerceAtLeast(0))
                        wm.updateViewLayout(v, p)
                    }
                    MotionEvent.ACTION_UP -> prefs.edit().putInt("fx", p.x).putInt("fy", p.y).apply()
                }
                true
            }
            try { wm.addView(v, p) } catch (_: Throwable) { fpsOn = false; return }
            fpsView = v; fpsLp = p
            frames = 0; lastNs = 0L
            Choreographer.getInstance().postFrameCallback(frameCb)
        } else {
            Choreographer.getInstance().removeFrameCallback(frameCb)
            fpsView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
            fpsView = null; fpsLp = null
        }
    }

    // ---------- Ikon sampah ----------
    private fun trashCenter() = PointF(scr.x / 2f, scr.y - 100 * density)

    private fun overTrash(p: WindowManager.LayoutParams): Boolean {
        val t = trashCenter(); val half = bubbleSize() / 2f
        return hypot(p.x + half - t.x, p.y + half - t.y) < 80 * density
    }

    private fun showTrash() {
        if (trash != null) return
        val size = (64 * density).toInt(); val c = trashCenter(); val ic = (28 * density).toInt()
        val v = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC222B36.toInt()); setStroke((1 * density).toInt(), 0x55FFFFFF) }
            alpha = 0f
            addView(IconView(this@FloatingService, IconView.TRASH), FrameLayout.LayoutParams(ic, ic, Gravity.CENTER))
        }
        val p = lp(size, size).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            x = (c.x - size / 2).toInt(); y = (c.y - size / 2).toInt()
        }
        wm.addView(v, p); trash = v
        v.animate().alpha(1f).setDuration(150).start()
    }

    private fun setTrashActive(on: Boolean) {
        trash?.apply {
            (background as GradientDrawable).setColor(if (on) 0xFFE53935.toInt() else 0xCC222B36.toInt())
            animate().scaleX(if (on) 1.25f else 1f).scaleY(if (on) 1.25f else 1f).setDuration(100).start()
        }
    }

    private fun hideTrash() { trash?.let { try { wm.removeView(it) } catch (_: Throwable) {} }; trash = null }

    // ---------- Pilih aplikasi ----------
    private fun showPicker() {
        if (picker != null) return
        refreshScreen()
        val pm = packageManager
        val items = cached ?: buildItems().also { cached = it }
        val shown = items.toMutableList()
        val d = density
        fun dp(v: Int) = (v * d).toInt()
        val last = prefs.getString("last", null)

        val root = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()); setOnClickListener { closePicker() } }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { setColor(0xF20B1220.toInt()); cornerRadius = dp(28).toFloat(); setStroke(dp(1), 0x33FFFFFF) }
            clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND
            isClickable = true
        }
        val search = EditText(this).apply {
            hint = "Cari aplikasi..."; setHintTextColor(0xFF8A97A6.toInt()); setTextColor(Color.WHITE)
            isSingleLine = true; textSize = 15f; setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply { setColor(0xFF0F1A2C.toInt()); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0x33FFFFFF) }
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, -2))

        if (last != null) {
            val lbl = try { pm.getApplicationLabel(pm.getApplicationInfo(last, 0)).toString() } catch (_: Throwable) { null }
            if (lbl != null) panel.addView(TextView(this).apply {
                text = "Tampilkan lagi: $lbl"; setTextColor(0xFF8FB8FF.toInt()); textSize = 14f
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = GradientDrawable().apply { setColor(0x223D8BFF); cornerRadius = dp(18).toFloat() }
                setOnClickListener { Sfx.pop(); launch(last); closePicker() }
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
                val app = shown[i]
                val ic = icons[app.pkg] ?: try { pm.getApplicationIcon(app.pkg).also { d2 -> icons[app.pkg] = d2 } } catch (_: Throwable) { null }
                (row.getChildAt(0) as ImageView).setImageDrawable(ic)
                (row.getChildAt(1) as TextView).text = app.label
                return row
            }
        }
        val list = ListView(this).apply {
            this.adapter = adapter; divider = null; dividerHeight = 0
            selector = ColorDrawable(Color.TRANSPARENT); overScrollMode = View.OVER_SCROLL_NEVER
            setOnItemClickListener { _, _, i, _ -> Sfx.pop(); launch(shown[i].pkg); closePicker() }
        }
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })

        // Baris saklar FPS
        val fpsRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(10), dp(6), dp(2)) }
        fpsRow.addView(TextView(this).apply { text = "Tampilkan FPS"; setTextColor(Color.WHITE); textSize = 14f },
            LinearLayout.LayoutParams(0, -2, 1f))
        fpsRow.addView(SwitchView(this, prefs.getBoolean("fps", false)) { on ->
            prefs.edit().putBoolean("fps", on).apply()
        }, LinearLayout.LayoutParams(dp(46), dp(26)))
        panel.addView(fpsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                shown.clear(); shown.addAll(items.filter { it.label.lowercase().contains(q) })
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        val w = minOf(dp(320), scr.x - dp(24)); val h = minOf(dp(520), scr.y - dp(24))
        root.addView(panel, FrameLayout.LayoutParams(w, h, Gravity.CENTER))
        root.alpha = 0f; panel.scaleX = 0.92f; panel.scaleY = 0.92f
        wm.addView(root, lp(-1, -1, true).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN })
        picker = root
        root.animate().alpha(1f).setDuration(160).start()
        panel.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun closePicker() {
        val v = picker ?: return; picker = null
        v.animate().alpha(0f).setDuration(120).withEndAction { try { wm.removeView(v) } catch (_: Throwable) {} }.start()
    }

    private fun closePickerNow() { picker?.let { try { wm.removeView(it) } catch (_: Throwable) {} }; picker = null }

    private fun launch(pkg: String) {
        refreshScreen()
        val pct = prefs.getInt("size", 60).coerceAtLeast(30)
        val w = scr.x * pct / 100; val h = scr.y * pct / 100
        val l = (scr.x - w) / 2; val t = (scr.y - h) / 2
        val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h))
        try { // 5 = WINDOWING_MODE_FREEFORM (API tersembunyi, mungkin ditolak sistem)
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.java).invoke(opts, 5)
        } catch (_: Throwable) {}
        prefs.edit().putString("last", pkg).apply()
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i, opts.toBundle())
    }

    // Layar berputar (mis. main game): ukuran layar diambil ulang, tombol dikembalikan ke dalam layar
    private fun onScreenChanged() {
        refreshScreen(); closePickerNow(); hideTrash()
        val size = bubbleSize()
        bubbleLp?.let { p ->
            p.x = p.x.coerceIn(0, (scr.x - size).coerceAtLeast(0))
            p.y = p.y.coerceIn(0, (scr.y - size).coerceAtLeast(0))
            bubble?.let { try { wm.updateViewLayout(it, p) } catch (_: Throwable) {} }
        }
        fpsLp?.let { p ->
            p.x = p.x.coerceIn(0, scr.x.coerceAtLeast(1)); p.y = p.y.coerceIn(0, scr.y.coerceAtLeast(1))
            fpsView?.let { try { wm.updateViewLayout(it, p) } catch (_: Throwable) {} }
        }
    }

    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); onScreenChanged() }

    override fun onDestroy() {
        running = false
        prefs.edit().putBoolean("active", false).apply()
        try { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener) } catch (_: Throwable) {}
        try { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Throwable) {}
        Choreographer.getInstance().removeFrameCallback(frameCb)
        ui.removeCallbacksAndMessages(null)
        hideTrash(); closePickerNow()
        fpsView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }; fpsView = null
        bubble?.let { try { wm.removeView(it) } catch (_: Throwable) {} }; bubble = null
        super.onDestroy()
    }
}
