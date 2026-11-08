package com.tzofar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class SynthTest {
    private val horns = listOf("car", "truck", "bus", "train", "ship", "scooter")
    private val oneShots = listOf("clown", "bell_classic", "bell_thumb", "bell_rotary")

    private fun peak(a: FloatArray) = a.maxOf { abs(it) }

    private fun rms(a: FloatArray, from: Int = 0, to: Int = a.size): Float {
        var sum = 0.0
        for (i in from until to) sum += a[i] * a[i]
        return sqrt(sum / (to - from)).toFloat()
    }

    /** עוצמת תדר אחד (התמרת פורייה בנקודה בודדת). */
    private fun magnitude(a: FloatArray, hz: Double): Double {
        var re = 0.0
        var im = 0.0
        for (i in a.indices) {
            val w = 2 * PI * hz * i / Synth.SR
            re += a[i] * cos(w)
            im += a[i] * sin(w)
        }
        return sqrt(re * re + im * im) / a.size
    }

    @Test fun everySoundIsFiniteAndWithinRange() {
        for (id in horns + oneShots) {
            val r = Synth.render(id)
            for (buf in listOfNotNull(r.attack, r.loop)) {
                assertTrue(id, buf.all { it.isFinite() })
                assertTrue("$id peak ${peak(buf)}", peak(buf) <= 1f)
            }
        }
    }

    @Test fun hornsLoopForOneSecondAndBellsDoNot() {
        for (id in horns) assertEquals(id, Synth.SR, Synth.render(id).loop?.size)
        for (id in oneShots) assertNull(id, Synth.render(id).loop)
    }

    @Test fun hornsAreLoud() {
        for (id in horns) {
            val loop = Synth.render(id).loop!!
            assertTrue("$id rms ${rms(loop)}", rms(loop) > 0.25f)
        }
    }

    @Test fun loopSeamIsNoBiggerThanAnyOtherStep() {
        for (id in horns) {
            val loop = Synth.render(id).loop!!
            var maxStep = 0f
            for (i in 1 until loop.size) maxStep = max(maxStep, abs(loop[i] - loop[i - 1]))
            val seam = abs(loop[0] - loop[loop.size - 1])
            assertTrue("$id seam $seam > max step $maxStep", seam <= maxStep * 1.001f)
        }
    }

    @Test fun attackFlowsIntoLoop() {
        for (id in horns) {
            val r = Synth.render(id)
            val loop = r.loop!!
            var maxStep = 0f
            for (i in 1 until loop.size) maxStep = max(maxStep, abs(loop[i] - loop[i - 1]))
            val join = abs(loop[0] - r.attack.last())
            assertTrue("$id join $join > max step $maxStep", join <= maxStep * 1.05f)
            assertTrue("$id starts at ${r.attack[0]}", abs(r.attack[0]) < 0.05f)
        }
    }

    @Test fun carHornContainsItsTwoTones() {
        val loop = Synth.render("car").loop!!
        val tone1 = magnitude(loop, 405.0)
        val tone2 = magnitude(loop, 501.0)
        val between = magnitude(loop, 450.0)
        assertTrue("405=$tone1 vs 450=$between", tone1 > between * 20)
        assertTrue("501=$tone2 vs 450=$between", tone2 > between * 20)
    }

    @Test fun bellsDieAway() {
        for (id in listOf("bell_classic", "bell_thumb", "bell_rotary")) {
            val a = Synth.render(id).attack
            val rate = Synth.SR
            val tail = rms(a, a.size - rate / 10, a.size)
            assertTrue("$id tail $tail", tail < 0.08f)
            assertTrue("$id ends at ${a.last()}", abs(a.last()) < 0.001f)
        }
    }

    @Test fun classicBellRingsTwice() {
        val a = Synth.render("bell_classic").attack
        val rate = Synth.SR
        val from1 = (0.15 * rate).toInt()
        val from2 = (0.172 * rate).toInt()
        val len = rate / 50
        val beforeSecond = rms(a, from1, from1 + len)
        val afterSecond = rms(a, from2, from2 + len)
        assertTrue("before=$beforeSecond after=$afterSecond", afterSecond > beforeSecond)

        // הטונים הגבוהים דועכים מהר, ולכן חזרתם היא הסימן לפגיעה חדשה
        val highMode = 2450.0 * 4.2
        val highBefore = magnitude(a.copyOfRange(from1, from1 + len), highMode)
        val highAfter = magnitude(a.copyOfRange(from2, from2 + len), highMode)
        assertTrue("high before=$highBefore after=$highAfter", highAfter > highBefore * 1.5)
    }

    @Test fun recordingOfSilenceIsRejected() {
        val pcm = ShortArray(44100) { (if (it % 2 == 0) 20 else -20).toShort() }
        assertNull(RecordingProcessor.process(pcm, pcm.size, 44100))
    }

    @Test fun recordingIsTrimmedNormalizedAndResampled() {
        val rate = 44100
        // חצי שנייה שקט, חצי שנייה קול, חצי שנייה שקט
        val pcm = ShortArray(rate + rate / 2)
        for (i in rate / 2 until rate) pcm[i] = (8000 * sin(2 * PI * 440.0 * i / rate)).toInt().toShort()
        val out = RecordingProcessor.process(pcm, pcm.size, rate)
        assertNotNull(out)
        out!!
        val seconds = out.size.toDouble() / Synth.SR
        assertTrue("length $seconds", seconds in 0.48..0.56)
        assertEquals(0.95f, peak(out), 0.03f)
        assertEquals(0f, out.last(), 0.001f)
    }

    @Test fun recordingCountLimitsWhatIsRead() {
        val rate = 44100
        val pcm = ShortArray(rate) { if (it < rate / 4) 10000 else 30000 }
        val out = RecordingProcessor.process(pcm, rate / 4, rate)!!
        assertEquals(0.95f, peak(out), 0.03f)
    }
}
