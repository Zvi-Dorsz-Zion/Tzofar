package com.tzofar.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** הקלטה מהמיקרופון. קצב 44100 הוא היחיד שמובטח בכל מכשיר. */
class Recorder {
    private val buffer = ShortArray(RATE * MAX_SECONDS)

    @Volatile
    private var running = false
    private var count = 0
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    /** @return false אם המיקרופון לא זמין. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBytes = AudioRecord.getMinBufferSize(RATE, channel, encoding)
        if (minBytes <= 0) return false
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, channel, encoding, minBytes * 2)
        } catch (_: RuntimeException) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        try {
            r.startRecording()
        } catch (_: IllegalStateException) {
            r.release()
            return false
        }
        record = r
        count = 0
        running = true
        thread = Thread {
            while (running && count < buffer.size) {
                val n = r.read(buffer, count, minOf(2048, buffer.size - count))
                if (n <= 0) break
                count += n
            }
        }.also { it.start() }
        return true
    }

    /** עוצר ומחזיר את הקול המעובד, או null אם לא נקלט קול. */
    fun stop(): FloatArray? {
        running = false
        thread?.join()
        thread = null
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        record = null
        return RecordingProcessor.process(buffer, count, RATE)
    }

    companion object {
        const val RATE = 44100
        const val MAX_SECONDS = 5
    }
}
