package com.example.floatwin

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.concurrent.CopyOnWriteArraySet

/** Efek suara ringan (pop saat tap, woosh saat splash). */
object Sfx {
    private var sp: SoundPool? = null
    private var popId = 0
    private var wooshId = 0
    private val loaded = CopyOnWriteArraySet<Int>()
    @Volatile var enabled = true

    fun init(ctx: Context) {
        enabled = ctx.applicationContext.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean("sound", true)
        if (sp != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val s = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
        s.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        popId = s.load(ctx.applicationContext, R.raw.pop, 1)
        wooshId = s.load(ctx.applicationContext, R.raw.woosh, 1)
        sp = s
    }

    fun pop() { if (enabled && loaded.contains(popId)) sp?.play(popId, 0.7f, 0.7f, 1, 0, 1f) }
    fun woosh() { if (enabled && loaded.contains(wooshId)) sp?.play(wooshId, 0.9f, 0.9f, 1, 0, 1f) }
}
