package com.example.floatwin

import android.app.*
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
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
        val apps: List<ResolveInfo> = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val d = resources.displayMetrics.density
        val root = FrameLayout(this).apply { setBackgroundColor(0x88000000.toInt()); setOnClickListener { closePicker() } }
        val list = ListView(this).apply {
            setBackgroundColor(Color.WHITE)
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(i: Int) = apps[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, c: View?, parent: ViewGroup): View {
                    val row = LinearLayout(this@FloatingService).apply { setPadding(24, 16, 24, 16); gravity = Gravity.CENTER_VERTICAL }
                    row.addView(ImageView(this@FloatingService).apply {
                        setImageDrawable(apps[i].loadIcon(pm)); layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
                    })
                    row.addView(TextView(this@FloatingService).apply {
                        text = apps[i].loadLabel(pm); setTextColor(Color.BLACK); textSize = 16f; setPadding(24, 0, 0, 0)
                    })
                    return row
                }
            }
            setOnItemClickListener { _, _, i, _ -> launch(apps[i].activityInfo.packageName); closePicker() }
        }
        root.addView(list, FrameLayout.LayoutParams((300 * d).toInt(), (450 * d).toInt(), Gravity.CENTER))
        wm.addView(root, lp(-1, -1).apply { flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() })
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
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        startActivity(i, opts.toBundle())
    }

    override fun onDestroy() {
        getSharedPreferences("p", MODE_PRIVATE).edit().putBoolean("active", false).apply()
        bubble?.let { wm.removeView(it) }; closePicker(); super.onDestroy()
    }
}
