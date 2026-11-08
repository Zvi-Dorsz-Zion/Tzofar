package com.tzofar.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** מכין הקלטה מהמיקרופון לשימוש כקול: חיתוך שקט בקצוות, השוואת עוצמה והמרת קצב דגימה. */
object RecordingProcessor {
    /** מתחת לעוצמה הזו ההקלטה נחשבת שקט (יחסית לעוצמה המרבית של 1). */
    private const val MIN_PEAK = 0.02f
    private const val TARGET_PEAK = 0.95f

    /** @return הקול המעובד, או null אם לא נקלט קול. */
    fun process(pcm: ShortArray, count: Int, inRate: Int, outRate: Int = Synth.SR): FloatArray? {
        if (count <= 0) return null
        var peak = 0f
        for (i in 0 until count) peak = max(peak, abs(pcm[i] / 32768f))
        if (peak < MIN_PEAK) return null

        val threshold = peak * 0.06f
        var first = 0
        while (first < count && abs(pcm[first] / 32768f) < threshold) first++
        var last = count - 1
        while (last > first && abs(pcm[last] / 32768f) < threshold) last--

        val pad = inRate / 50
        first = max(0, first - pad)
        last = min(count - 1, last + pad)

        val inLen = last - first + 1
        val outLen = ((inLen.toLong() * outRate) / inRate).toInt()
        if (outLen < outRate / 50) return null

        val step = inRate.toDouble() / outRate
        val gain = TARGET_PEAK / peak
        val out = FloatArray(outLen) { i ->
            val pos = first + i * step
            val a = pos.toInt().coerceAtMost(last)
            val b = (a + 1).coerceAtMost(last)
            val frac = (pos - a).toFloat()
            (pcm[a] / 32768f * (1f - frac) + pcm[b] / 32768f * frac) * gain
        }

        val fadeIn = min(outRate / 200, outLen / 2)
        for (i in 0 until fadeIn) out[i] *= i.toFloat() / fadeIn
        val fadeOut = min(outRate / 50, outLen / 2)
        for (i in 0 until fadeOut) out[outLen - 1 - i] *= i.toFloat() / fadeOut
        return out
    }
}
