package com.example.floatwin

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Ikon vektor yang digambar langsung (tanpa emoji / font). */
class IconView(ctx: Context, private val kind: Int, tint: Int = Color.WHITE) : View(ctx) {
    companion object { const val TRASH = 0; const val CLOSE = 1; const val CHEVRON = 2; const val CHECK = 3 }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = tint
    }
    var tint: Int = tint
        set(v) { field = v; p.color = v; invalidate() }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        p.strokeWidth = s * 0.09f
        c.save(); c.translate((width - s) / 2f, (height - s) / 2f)
        val path = Path()
        when (kind) {
            TRASH -> {
                c.drawLine(s * .20f, s * .30f, s * .80f, s * .30f, p)
                path.moveTo(s * .40f, s * .30f); path.lineTo(s * .40f, s * .20f)
                path.lineTo(s * .60f, s * .20f); path.lineTo(s * .60f, s * .30f)
                c.drawPath(path, p); path.reset()
                path.moveTo(s * .28f, s * .30f); path.lineTo(s * .33f, s * .82f)
                path.lineTo(s * .67f, s * .82f); path.lineTo(s * .72f, s * .30f)
                c.drawPath(path, p)
                c.drawLine(s * .44f, s * .45f, s * .44f, s * .70f, p)
                c.drawLine(s * .56f, s * .45f, s * .56f, s * .70f, p)
            }
            CLOSE -> {
                c.drawLine(s * .25f, s * .25f, s * .75f, s * .75f, p)
                c.drawLine(s * .75f, s * .25f, s * .25f, s * .75f, p)
            }
            CHEVRON -> {
                path.moveTo(s * .38f, s * .22f); path.lineTo(s * .66f, s * .50f); path.lineTo(s * .38f, s * .78f)
                c.drawPath(path, p)
            }
            CHECK -> {
                path.moveTo(s * .22f, s * .53f); path.lineTo(s * .43f, s * .72f); path.lineTo(s * .78f, s * .30f)
                c.drawPath(path, p)
            }
        }
        c.restore()
    }
}

/** Efek tekan: mengecil sedikit saat disentuh, kembali halus saat dilepas. */
fun View.press() {
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
            MotionEvent.ACTION_UP -> { v.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); Sfx.pop() }
            MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
}

/** Saklar gambar sendiri dengan animasi geser halus. */
class SwitchView(ctx: Context, on: Boolean = false, private val onChange: (Boolean) -> Unit = {}) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var pos = if (on) 1f else 0f
    var checked = on
        private set

    init {
        setOnClickListener { set(!checked, true) }
        press()
    }

    fun set(v: Boolean, notify: Boolean) {
        if (v == checked && !notify) return
        checked = v
        android.animation.ValueAnimator.ofFloat(pos, if (v) 1f else 0f).apply {
            duration = 180
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
        }.start()
        if (notify) onChange(v)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val off = 0xFF2A3547.toInt(); val onC = 0xFF3D8BFF.toInt()
        track.color = android.animation.ArgbEvaluator().evaluate(pos, off, onC) as Int
        c.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, track)
        val r = h / 2 - h * 0.14f
        val cx = h / 2 + (w - h) * pos
        c.drawCircle(cx, h / 2, r, knob)
    }
}
