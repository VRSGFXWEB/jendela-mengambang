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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
}
