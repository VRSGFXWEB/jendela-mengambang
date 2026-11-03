package com.example.floatwin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView

class SplashActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var done = false
    private var web: WebView? = null

    private fun go() {
        if (done) return
        done = true
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Sfx.init(this)
        window.statusBarColor = 0xFF05080D.toInt(); window.navigationBarColor = 0xFF05080D.toInt()
        // Logo statis langsung tampil, animasi three.js menyusul begitu siap (terasa lebih cepat)
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF05080D.toInt()) }
        val logo = ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }
        val s = (140 * resources.displayMetrics.density).toInt()
        root.addView(logo, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        setContentView(root)
        try {
            val wv = WebView(this)
            wv.setBackgroundColor(0xFF05080D.toInt()); wv.alpha = 0f
            wv.settings.javaScriptEnabled = true
            wv.addJavascriptInterface(object {
                @JavascriptInterface fun done() { h.post { go() } }
                @JavascriptInterface fun woosh() { h.post { Sfx.woosh() } }
            }, "Android")
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, u: String?) {
                    wv.animate().alpha(1f).setDuration(200).start()
                    logo.animate().alpha(0f).setDuration(200).start()
                }
            }
            root.addView(wv, FrameLayout.LayoutParams(-1, -1))
            wv.loadUrl("file:///android_asset/splash.html")
            web = wv
        } catch (e: Throwable) {
            h.postDelayed({ go() }, 1200); return
        }
        h.postDelayed({ go() }, 4200) // cadangan kalau animasi gagal
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        web?.destroy()
        super.onDestroy()
    }
}
