package com.tzofar.app

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

enum class Kind { HORN, RUBBER, BELL, RECORDING }

data class SoundInfo(val id: String, val name: String, val emoji: String, val kind: Kind)

object Catalog {
    val builtIn = listOf(
        SoundInfo("car", "מכונית", "🚗", Kind.HORN),
        SoundInfo("truck", "משאית", "🚚", Kind.HORN),
        SoundInfo("bus", "אוטובוס", "🚌", Kind.HORN),
        SoundInfo("train", "רכבת", "🚂", Kind.HORN),
        SoundInfo("ship", "אונייה", "🚢", Kind.HORN),
        SoundInfo("scooter", "קטנוע", "🛵", Kind.HORN),
        SoundInfo("clown", "צופר גומי", "🤡", Kind.RUBBER),
        SoundInfo("bell_classic", "דינג דינג", "🔔", Kind.BELL),
        SoundInfo("bell_thumb", "פעמון אגודל", "🛎️", Kind.BELL),
        SoundInfo("bell_rotary", "פעמון מסתובב", "🚲", Kind.BELL),
    )

    fun all(c: Context): List<SoundInfo> =
        builtIn + Prefs.recordings(c).map { SoundInfo(it.id, it.name, "🎤", Kind.RECORDING) }
}

/** הקלטות המשתמש: הקול בקובץ בתיקיית האפליקציה, השם בהגדרות. */
object Recordings {
    private const val PREFIX = "rec_"

    fun isRecording(id: String) = id.startsWith(PREFIX)

    private fun file(c: Context, id: String) = File(File(c.filesDir, "recordings"), "$id.pcm")

    fun add(c: Context, name: String, samples: FloatArray) {
        val id = PREFIX + System.currentTimeMillis()
        val f = file(c, id)
        f.parentFile?.mkdirs()
        val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bytes.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        f.writeBytes(bytes.array())
        Prefs.addRecording(c, Prefs.Recording(id, name))
    }

    fun delete(c: Context, id: String) {
        Prefs.removeRecording(c, id)
        file(c, id).delete()
        SoundBank.forget(id)
    }

    fun load(c: Context, id: String): FloatArray? {
        val f = file(c, id)
        if (!f.exists()) return null
        val shorts = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
    }
}

/** הקולות המוכנים בזיכרון, כדי שלחיצה תשמע מיד. */
object SoundBank {
    private val cache = ConcurrentHashMap<String, Rendered>()

    fun get(c: Context, id: String): Rendered? {
        cache[id]?.let { return it }
        val r = when {
            Recordings.isRecording(id) -> Recordings.load(c, id)?.let { Rendered(it, null, 0, retrigger = true) }
            Synth.isBuiltIn(id) -> Synth.render(id)
            else -> null
        } ?: return null
        cache[id] = r
        return r
    }

    fun forget(id: String) {
        cache.remove(id)
    }

    /** הכנה ברקע: קודם מה שנמצא על המסך, ואחר כך כל השאר. */
    fun preload(c: Context) {
        val app = c.applicationContext
        Thread {
            val ids = listOf(Prefs.main(app)) + Prefs.small(app) + Catalog.all(app).map { it.id }
            for (id in ids.distinct()) get(app, id)
        }.start()
    }
}
