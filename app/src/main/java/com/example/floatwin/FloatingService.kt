package com.example.floatwin

import android.animation.ValueAnimator
import android.app.*
import android.os.SystemClock
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

class FloatingService : Service() {
    companion object { @Volatile var running = false }

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var trash: FrameLayout? = null
    private var picker: View? = null
    @Volatile private var cached: List<Pair<ResolveInfo, String>>? = null
    private val icons = ConcurrentHashMap<String, Drawable>()
    private val ui = Handler(Looper.getMainLooper())
    private val scr = Point()
    private val density get() = resources.displayMetrics.density
    private val prefs get() = getSharedPreferences("p", MODE_PRIVATE)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { prefs.edit().putBoolean("wanted", false).apply(); stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
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
        Thread {
            val l = loadApps(); cached = l
            for ((ri, _) in l) icons.getOrPut(ri.activityInfo.packageName) { ri.loadIcon(packageManager) }
        }.start()
        try { showBubble() } catch (e: Exception) { stopSelf() }
    }

    private fun scheduleRestart() {
        val pi = PendingIntent.getForegroundService(this, 7, Intent(this, FloatingService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 2000, pi)
    }

    private fun loadApps(): List<Pair<ResolveInfo, String>> {
        val pm = packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
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
            if (!moved) { longFired = true; v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); restoreLast() }
        }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
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
                        if (over) { prefs.edit().putBoolean("wanted", false).apply(); stopSelf() } else snap(v, p, size)
                    } else if (!longFired) showPicker()
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

    // Tempel ke tepi layar dengan animasi halus
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
        val pm = packageManager
        val items = cached ?: loadApps().also { cached = it }
        val pinned = prefs.getStringSet("pinned", emptySet()) ?: emptySet()
        val base = if (pinned.isEmpty()) items
            else items.filter { it.first.activityInfo.packageName in pinned }.ifEmpty { items }
        val shown = base.toMutableList()
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
                shown.clear(); shown.addAll(base.filter { it.second.lowercase().contains(q) })
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        val w = minOf(dp(320), scr.x - dp(24)); val h = minOf(dp(500), scr.y - dp(24))
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

    // Layar diputar (mis. main game): tombol dikembalikan ke dalam layar
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        refreshScreen(); closePickerNow(); hideTrash()
        ui.postDelayed({
            refreshScreen()
            val p = bubbleLp ?: return@postDelayed; val v = bubble ?: return@postDelayed
            val size = bubbleSize()
            p.x = p.x.coerceIn(0, (scr.x - size).coerceAtLeast(0))
            p.y = p.y.coerceIn(0, (scr.y - size).coerceAtLeast(0))
            try { wm.updateViewLayout(v, p) } catch (_: Throwable) {}
        }, 400)
    }

    override fun onDestroy() {
        running = false
        prefs.edit().putBoolean("active", false).apply()
        ui.removeCallbacksAndMessages(null)
        hideTrash(); closePickerNow()
        bubble?.let { try { wm.removeView(it) } catch (_: Throwable) {} }; bubble = null
        super.onDestroy()
    }
}
