package com.example.floatwin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.*
import java.util.concurrent.ConcurrentHashMap

class AppSelectActivity : Activity() {
    private val BG = 0xFF05080D.toInt()
    private val ACCENT = 0xFF3D8BFF.toInt()
    private val ACCENT_L = 0xFF8FB8FF.toInt()
    private val DIM = 0xFF8A97A6.toInt()
    private val prefs by lazy { getSharedPreferences("p", MODE_PRIVATE) }
    private val pinned = HashSet<String>()
    private val icons = ConcurrentHashMap<String, Drawable>()
    private var all: List<Pair<ResolveInfo, String>> = emptyList()
    private val shown = ArrayList<Pair<ResolveInfo, String>>()
    private lateinit var countTv: TextView
    private lateinit var adapter: BaseAdapter
    private var query = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(t: String, sp: Float, c: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(c); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun updateCount() { countTv.text = if (pinned.isEmpty()) "Belum ada yang dipilih (semua aplikasi akan tampil)" else "${pinned.size} aplikasi dipilih" }
    private fun applyFilter() {
        shown.clear(); shown.addAll(all.filter { it.second.lowercase().contains(query) })
        adapter.notifyDataSetChanged()
    }

    override fun finish() { super.finish(); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Sfx.init(this)
        window.statusBarColor = BG; window.navigationBarColor = BG
        pinned.addAll(prefs.getStringSet("pinned", emptySet()) ?: emptySet())
        val pm = packageManager

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); setPadding(dp(20), dp(36), dp(20), dp(8)) }
        root.addView(tv("Aplikasi di Tombol", 26f, bold = true))
        root.addView(tv("Hanya aplikasi yang dipilih yang muncul saat tombol bulat di-tap. Makin sedikit makin ringan.", 13f, DIM),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        countTv = tv("", 12f, ACCENT_L)
        root.addView(countTv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        val search = EditText(this).apply {
            hint = "Cari aplikasi..."; setHintTextColor(DIM); setTextColor(Color.WHITE)
            isSingleLine = true; textSize = 15f; setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply { setColor(0xFF0B1220.toInt()); cornerRadius = dp(24).toFloat(); setStroke(dp(1), 0x33FFFFFF) }
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, c: View?, parent: ViewGroup): View {
                val row = (c as? LinearLayout) ?: LinearLayout(this@AppSelectActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(10), dp(6), dp(10))
                    addView(ImageView(this@AppSelectActivity), LinearLayout.LayoutParams(dp(42), dp(42)))
                    addView(tv("", 16f).apply { setPadding(dp(14), 0, dp(8), 0) }, LinearLayout.LayoutParams(0, -2, 1f))
                    val box = FrameLayout(this@AppSelectActivity)
                    box.addView(IconView(this@AppSelectActivity, IconView.CHECK), FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
                    addView(box, LinearLayout.LayoutParams(dp(26), dp(26)))
                }
                val (ri, label) = shown[i]
                val pkg = ri.activityInfo.packageName
                (row.getChildAt(0) as ImageView).setImageDrawable(icons[pkg] ?: ri.loadIcon(pm).also { icons[pkg] = it })
                (row.getChildAt(1) as TextView).text = label
                val on = pinned.contains(pkg)
                val box = row.getChildAt(2) as FrameLayout
                box.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (on) setColor(ACCENT) else { setColor(Color.TRANSPARENT); setStroke(dp(2), 0x55FFFFFF) }
                }
                box.getChildAt(0).visibility = if (on) View.VISIBLE else View.INVISIBLE
                return row
            }
        }
        val list = ListView(this).apply {
            adapter = this@AppSelectActivity.adapter; divider = null; dividerHeight = 0
            selector = ColorDrawable(Color.TRANSPARENT); overScrollMode = View.OVER_SCROLL_NEVER
            setOnItemClickListener { _, v, i, _ ->
                val pkg = shown[i].first.activityInfo.packageName
                Sfx.pop()
                if (!pinned.remove(pkg)) pinned.add(pkg)
                prefs.edit().putStringSet("pinned", HashSet(pinned)).apply()
                updateCount(); this@AppSelectActivity.adapter.notifyDataSetChanged()
                val box = (v as? LinearLayout)?.getChildAt(2)
                box?.apply { scaleX = 0.6f; scaleY = 0.6f; animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator()).start() }
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        setContentView(root)
        updateCount()

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s.toString().trim().lowercase(); applyFilter() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        Thread {
            val l = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { it to it.loadLabel(pm).toString() }
                .sortedWith(compareByDescending<Pair<ResolveInfo, String>> { pinned.contains(it.first.activityInfo.packageName) }
                    .thenBy { it.second.lowercase() })
            all = l
            runOnUiThread { applyFilter() }
            for ((ri, _) in l) icons.getOrPut(ri.activityInfo.packageName) { ri.loadIcon(pm) }
        }.start()
    }
}
