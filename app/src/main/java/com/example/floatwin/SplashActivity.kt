package com.example.floatwin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

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
        window.statusBarColor = 0xFF05080D.toInt(); window.navigationBarColor = 0xFF05080D.toInt()
        try {
            val wv = WebView(this)
            wv.setBackgroundColor(0xFF05080D.toInt())
            wv.settings.javaScriptEnabled = true
            wv.addJavascriptInterface(object { @JavascriptInterface fun done() { h.post { go() } } }, "Android")
            wv.webViewClient = WebViewClient()
            setContentView(wv)
            wv.loadUrl("file:///android_asset/splash.html")
            web = wv
        } catch (e: Throwable) { go(); return }
        h.postDelayed({ go() }, 4500) // cadangan kalau animasi gagal
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        web?.destroy()
        super.onDestroy()
    }
}
