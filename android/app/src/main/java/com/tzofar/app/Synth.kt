package com.tzofar.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * קול מוכן להשמעה.
 * צופר: [attack] ואחריו [loop] שחוזר כל עוד מחזיקים. פעמון או הקלטה: הכול ב-[attack], ו-[loop] ריק.
 */
class Rendered(
    val attack: FloatArray,
    val loop: FloatArray?,
    val releaseMs: Int,
    /** לחיצה חדשה קוטעת את ההשמעה הקודמת (בהקלטות), במקום להתערבב איתה (בפעמון). */
    val retrigger: Boolean = false,
)

/**
 * מייצר את כל הקולות מתוך נוסחאות, בלי קובצי הקלטה. קוטלין טהור, ולכן נבדק בבדיקות יחידה.
 *
 * הלולאה של צופר נמשכת שנייה אחת בדיוק וכל התדרים בה הם מספרים שלמים בהרץ, ולכן
 * היא נסגרת בלי קפיצה. מסננים רצים עליה פעמיים, והמעבר השני הוא מצב יציב ומחזורי.
 */
object Synth {
    const val SR = 48000

    private val SINE = FloatArray(SR) { sin(2.0 * PI * it / SR).toFloat() }

    private const val TARGET_PEAK = 0.95f

    private class Peak(val hz: Double, val db: Double, val q: Double)

    private class Horn(
        val tones: IntArray,
        val slope: Double,
        val drive: Double,
        val highPass: Double,
        val peaks: List<Peak>,
        val attackMs: Int,
        val releaseMs: Int,
    )

    private val HORNS = mapOf(
        // שני צלילים קרובים של ממברנה, עם תהודה חזקה בשופר
        "car" to Horn(intArrayOf(405, 501), 0.75, 2.2, 250.0, listOf(Peak(2400.0, 9.0, 1.0), Peak(900.0, 4.0, 1.0)), 12, 40),
        // צופר אוויר: שלושה צלילים נמוכים, עלייה איטית
        "truck" to Horn(intArrayOf(185, 233, 277), 0.85, 3.0, 120.0, listOf(Peak(1200.0, 6.0, 0.8), Peak(2800.0, 4.0, 1.0)), 90, 150),
        "bus" to Horn(intArrayOf(277, 349), 0.8, 2.4, 180.0, listOf(Peak(1700.0, 7.0, 1.0)), 25, 70),
        // אקורד של חמישה צלילים
        "train" to Horn(intArrayOf(311, 370, 415, 494, 622), 1.0, 1.6, 200.0, listOf(Peak(1400.0, 5.0, 0.8)), 180, 350),
        // הטון היסודי נמוך מדי לרמקול של טלפון, אז העיצוב נמצא בהרמוניות
        "ship" to Horn(intArrayOf(110, 165), 0.8, 3.5, 70.0, listOf(Peak(700.0, 6.0, 0.7), Peak(1600.0, 3.0, 0.8)), 350, 600),
        "scooter" to Horn(intArrayOf(720), 0.6, 1.8, 300.0, listOf(Peak(3000.0, 6.0, 1.2)), 6, 25),
    )

    fun isBuiltIn(id: String): Boolean = id in HORNS || id in setOf("clown", "bell_classic", "bell_thumb", "bell_rotary")

    fun render(id: String): Rendered {
        HORNS[id]?.let { return renderHorn(it) }
        return when (id) {
            "clown" -> Rendered(renderClown(), null, 0)
            "bell_classic" -> Rendered(renderBell(2450.0, listOf(0.0 to 1f, 0.17 to 0.9f), 1.4, 1.0), null, 0)
            "bell_thumb" -> Rendered(renderBell(3300.0, listOf(0.0 to 1f), 0.9, 0.6), null, 0)
            "bell_rotary" -> Rendered(renderRotary(), null, 0)
            else -> throw IllegalArgumentException(id)
        }
    }

    /** כמה זמן נמשכת השמעה של קול חד-פעמי, במילישניות. */
    fun lengthMs(r: Rendered): Int = r.attack.size * 1000 / SR

    private fun renderHorn(h: Horn): Rendered {
        val loop = FloatArray(SR)
        val toneGain = 1f / sqrt(h.tones.size.toFloat())
        for (f in h.tones) addHarmonics(loop, f, h.slope, toneGain)

        // רטט קל של הממברנה: מחזורי בשנייה, ולכן לא שובר את הלולאה
        for (t in loop.indices) loop[t] *= 1f + 0.04f * SINE[(37 * t) % SR]

        var peak = 0f
        for (s in loop) peak = max(peak, abs(s))
        for (t in loop.indices) loop[t] = tanh(h.drive * loop[t] / peak).toFloat()

        filterPeriodic(loop, highPass(h.highPass))
        for (p in h.peaks) filterPeriodic(loop, peaking(p.hz, p.db, p.q))
        filterPeriodic(loop, lowPass(11000.0))
        normalize(loop)

        // דחיסה אחרונה: מעלה את העוצמה הממוצעת, וזה מה שנשמע חזק ברמקול קטן
        for (t in loop.indices) loop[t] = tanh(2.0 * loop[t]).toFloat()
        normalize(loop)

        // ההתחלה לקוחה מסוף הלולאה, ולכן הלולאה ממשיכה ממנה ברצף
        val attackLen = SR * h.attackMs / 1000
        val attack = FloatArray(attackLen) { i ->
            val x = (i + 1f) / attackLen
            loop[SR - attackLen + i] * x * x * (3f - 2f * x)
        }
        return Rendered(attack, loop, h.releaseMs)
    }

    private fun addHarmonics(out: FloatArray, f: Int, slope: Double, gain: Float) {
        var k = f
        var n = 1
        while (k < 14000) {
            val a = gain * n.toDouble().pow(-slope).toFloat()
            var idx = 0
            for (t in out.indices) {
                out[t] += a * SINE[idx]
                idx += k
                if (idx >= SR) idx -= SR
            }
            n++
            k += f
        }
    }

    /** צופר גומי: שתי לחיצות על הבלון, והצליל עולה בכל אחת. */
    private fun renderClown(): FloatArray {
        val out = FloatArray((0.62 * SR).toInt())
        squeeze(out, 0.0, 0.22, 290.0, 400.0)
        squeeze(out, 0.27, 0.32, 330.0, 480.0)
        for (t in out.indices) out[t] = tanh(1.8 * out[t]).toFloat()
        filterOnce(out, peaking(1150.0, 10.0, 1.4))
        filterOnce(out, peaking(2600.0, 7.0, 1.6))
        filterOnce(out, highPass(220.0))
        normalize(out)
        fadeEnd(out, 400)
        return out
    }

    private fun squeeze(out: FloatArray, startS: Double, lenS: Double, f0: Double, f1: Double) {
        val start = (startS * SR).toInt()
        val len = (lenS * SR).toInt()
        val phases = DoubleArray(18)
        for (i in 0 until len) {
            val x = i.toDouble() / len
            val f = f0 + (f1 - f0) * (1.0 - (1.0 - x) * (1.0 - x))
            val env = min(1.0, i / (0.012 * SR)) * min(1.0, (len - i) / (0.05 * SR))
            var v = 0f
            for (n in phases.indices) {
                val nf = f * (n + 1)
                if (nf >= 16000) break
                phases[n] = (phases[n] + nf) % SR
                v += SINE[phases[n].toInt()] / (n + 1f).pow(0.7f)
            }
            out[start + i] += (v * env * 0.3).toFloat()
        }
    }

    // מודים של כיפת מתכת: יחס תדר, עוצמה, זמן דעיכה. הצמד הראשון קרוב מאוד ויוצר את הרעד של פעמון.
    private val BELL_MODES = arrayOf(
        doubleArrayOf(1.000, 1.00, 0.90),
        doubleArrayOf(1.003, 0.60, 0.90),
        doubleArrayOf(1.520, 0.25, 0.50),
        doubleArrayOf(2.310, 0.50, 0.45),
        doubleArrayOf(3.180, 0.20, 0.28),
        doubleArrayOf(4.200, 0.12, 0.18),
        doubleArrayOf(5.400, 0.06, 0.10),
    )

    private fun renderBell(f0: Double, hits: List<Pair<Double, Float>>, lengthS: Double, decayScale: Double): FloatArray {
        val out = FloatArray((lengthS * SR).toInt())
        val rnd = Random(7)
        for ((at, gain) in hits) {
            val start = (at * SR).toInt()
            damp(out, start, 0.3f)
            strike(out, start, f0, gain, decayScale, rnd)
        }
        normalize(out)
        fadeEnd(out, 2000)
        return out
    }

    /** פעמון מסתובב: הלשון פוגעת שוב ושוב בכיפה, בקצב מהיר ובעוצמות משתנות. */
    private fun renderRotary(): FloatArray {
        val out = FloatArray((1.4 * SR).toInt())
        val rnd = Random(11)
        for (i in 0 until 16) {
            val at = (i * 0.048 * SR).toInt() + rnd.nextInt(120)
            val gain = (if (i % 2 == 0) 1f else 0.7f) * (1f - 0.03f * i)
            damp(out, at, 0.6f)
            strike(out, at, 2700.0, gain, 0.35, rnd)
        }
        normalize(out)
        fadeEnd(out, 2000)
        return out
    }

    /** הלשון נוגעת בכיפה ומשתיקה לרגע את מה שעוד מצלצל, ובלי זה הפגיעה הבאה מתאבכת איתו. */
    private fun damp(out: FloatArray, start: Int, keep: Float) {
        val ramp = SR / 1000
        for (t in start until out.size) {
            val x = min(1f, (t - start).toFloat() / ramp)
            out[t] *= 1f + (keep - 1f) * x
        }
    }

    private fun strike(out: FloatArray, start: Int, f0: Double, gain: Float, decayScale: Double, rnd: Random) {
        for (mode in BELL_MODES) {
            val hz = f0 * mode[0]
            if (hz >= 20000) continue
            val decayPerSample = exp(-1.0 / (mode[2] * decayScale * SR)).toFloat()
            var amp = gain * mode[1].toFloat()
            var phase = 0.0
            var t = start
            while (t < out.size && amp > 0.0003f) {
                out[t] += amp * SINE[phase.toInt()]
                amp *= decayPerSample
                phase += hz
                if (phase >= SR) phase -= SR
                t++
            }
        }
        // צליל המכה עצמה: 1.5 מילישניות של רעש
        val clickLen = SR * 3 / 2000
        for (i in 0 until clickLen) {
            val t = start + i
            if (t >= out.size) break
            out[t] += gain * 0.5f * (rnd.nextFloat() * 2f - 1f) * (1f - i.toFloat() / clickLen)
        }
    }

    private fun normalize(buf: FloatArray) {
        var peak = 0f
        for (s in buf) peak = max(peak, abs(s))
        if (peak == 0f) return
        val g = TARGET_PEAK / peak
        for (i in buf.indices) buf[i] *= g
    }

    private fun fadeEnd(buf: FloatArray, frames: Int) {
        val n = min(frames, buf.size)
        for (i in 0 until n) buf[buf.size - 1 - i] *= i.toFloat() / n
    }

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        fun step(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }

    /** פעם ראשונה כדי להגיע למצב יציב, ורק המעבר השני נכתב. */
    private fun filterPeriodic(buf: FloatArray, f: Biquad) {
        for (s in buf) f.step(s.toDouble())
        for (i in buf.indices) buf[i] = f.step(buf[i].toDouble()).toFloat()
    }

    private fun filterOnce(buf: FloatArray, f: Biquad) {
        for (i in buf.indices) buf[i] = f.step(buf[i].toDouble()).toFloat()
    }

    private fun peaking(hz: Double, db: Double, q: Double): Biquad {
        val a = 10.0.pow(db / 40)
        val w = 2 * PI * hz / SR
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha / a
        return Biquad(
            (1 + alpha * a) / a0, -2 * cos(w) / a0, (1 - alpha * a) / a0,
            -2 * cos(w) / a0, (1 - alpha / a) / a0,
        )
    }

    private fun highPass(hz: Double): Biquad {
        val w = 2 * PI * hz / SR
        val alpha = sin(w) / (2 * 0.707)
        val c = cos(w)
        val a0 = 1 + alpha
        return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
    }

    private fun lowPass(hz: Double): Biquad {
        val w = 2 * PI * hz / SR
        val alpha = sin(w) / (2 * 0.707)
        val c = cos(w)
        val a0 = 1 + alpha
        return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
    }
}
