package com.tzofar.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.tanh

/** השמעה אחת שרצה עכשיו. */
class Voice internal constructor(internal val sound: Rendered) {
    internal var pos = 0
    internal var inAttack = true
    internal var played = 0
    internal var fade = 1f
    internal var fadeStep = 0f

    @Volatile
    internal var releaseRequested = false
}

/**
 * מערבל קטן על AudioTrack אחד. הצופרים חוזרים בלולאה עד השחרור, והפעמונים יכולים להתערבב זה בזה.
 * כל עוד יש מי שמחזיק אותו ([hold]) הוא נשאר מוכן, כדי שלחיצה תשמע מיד.
 */
object AudioEngine {
    private const val CHUNK = 240

    /** גם נגיעה קצרה מאוד נשמעת כצפירה קצרה ולא כנקישה. */
    private const val MIN_HELD_FRAMES = Synth.SR / 8

    /** אחרי כמה זמן בלי קול משהים את ההשמעה ולא כותבים שקט. */
    private const val PAUSE_AFTER_IDLE_FRAMES = Synth.SR * 5

    private val lock = Object()
    private val voices = ArrayList<Voice>()
    private var holders = 0
    private var thread: Thread? = null

    /** נקרא מחוט השמע כשהקול האחרון הסתיים. */
    @Volatile
    var onIdle: (() -> Unit)? = null

    val isBusy: Boolean
        get() = synchronized(lock) { voices.isNotEmpty() }

    fun hold() = synchronized(lock) {
        holders++
        ensureThread()
    }

    fun unhold() = synchronized(lock) {
        holders--
        lock.notifyAll()
    }

    fun play(sound: Rendered): Voice = synchronized(lock) {
        if (sound.retrigger) {
            for (v in voices) if (v.sound === sound) startFade(v, 15)
        }
        val v = Voice(sound)
        voices.add(v)
        ensureThread()
        lock.notifyAll()
        v
    }

    fun release(v: Voice) {
        v.releaseRequested = true
    }

    private fun ensureThread() {
        if (thread == null) {
            thread = Thread(::run, "audio-engine").also { it.start() }
        }
    }

    private fun startFade(v: Voice, ms: Int) {
        if (v.fadeStep == 0f) v.fadeStep = 1f / max(1, Synth.SR * ms / 1000)
    }

    private fun createTrack(): AudioTrack? {
        val channel = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBytes = AudioTrack.getMinBufferSize(Synth.SR, channel, encoding)
        if (minBytes <= 0) return null
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(Synth.SR)
                .setChannelMask(channel)
                .setEncoding(encoding)
                .build()
            val t = AudioTrack(attrs, format, minBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
            if (t.state == AudioTrack.STATE_INITIALIZED) {
                t
            } else {
                t.release()
                null
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var track: AudioTrack = createTrack() ?: return giveUp()
        track.play()
        val mix = FloatArray(CHUNK)
        val pcm = ShortArray(CHUNK)
        var idleFrames = 0
        var paused = false

        while (true) {
            var becameIdle = false
            var hadVoices = false
            var waited = false
            synchronized(lock) {
                if (voices.isEmpty()) {
                    if (holders <= 0) {
                        thread = null
                        track.stop()
                        track.release()
                        return
                    }
                    if (idleFrames >= PAUSE_AFTER_IDLE_FRAMES) {
                        if (!paused) {
                            track.pause()
                            paused = true
                        }
                        lock.wait()
                        waited = true
                    }
                }
                if (!waited) {
                    if (paused && voices.isNotEmpty()) {
                        track.play()
                        paused = false
                    }
                    hadVoices = voices.isNotEmpty()
                    mix.fill(0f)
                    val it = voices.iterator()
                    while (it.hasNext()) {
                        if (!mixVoice(it.next(), mix)) it.remove()
                    }
                    becameIdle = hadVoices && voices.isEmpty()
                }
            }
            if (waited) continue

            idleFrames = if (hadVoices) 0 else idleFrames + CHUNK
            if (becameIdle) onIdle?.invoke()

            for (i in 0 until CHUNK) pcm[i] = (limit(mix[i]) * 32767f).toInt().toShort()
            if (track.write(pcm, 0, CHUNK) < 0) {
                // הפלט נפל (למשל החלפת התקן): בונים אותו מחדש
                track.release()
                track = createTrack() ?: return giveUp()
                track.play()
                paused = false
            }
        }
    }

    private fun giveUp() {
        synchronized(lock) {
            voices.clear()
            thread = null
        }
        onIdle?.invoke()
    }

    /** @return false כשהקול הסתיים. */
    private fun mixVoice(v: Voice, mix: FloatArray): Boolean {
        val loop = v.sound.loop
        for (i in 0 until CHUNK) {
            if (v.releaseRequested && loop != null && v.played >= MIN_HELD_FRAMES) {
                startFade(v, v.sound.releaseMs)
            }
            val s: Float
            if (v.inAttack) {
                if (v.pos >= v.sound.attack.size) {
                    if (loop == null) return false
                    v.inAttack = false
                    v.pos = 0
                    s = loop[0]
                    v.pos = 1
                } else {
                    s = v.sound.attack[v.pos++]
                }
            } else {
                s = loop!![v.pos]
                v.pos = (v.pos + 1) % loop.size
            }
            v.played++
            if (v.fadeStep > 0f) {
                v.fade -= v.fadeStep
                if (v.fade <= 0f) return false
            }
            mix[i] += s * v.fade * v.fade
        }
        return true
    }

    /** כמה קולות יחד לא נחתכים בגסות. */
    private fun limit(x: Float): Float {
        val a = abs(x)
        if (a <= 0.9f) return x
        return sign(x) * (0.9f + 0.1f * tanh((a - 0.9f) / 0.1f))
    }
}
