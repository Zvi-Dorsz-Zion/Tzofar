package com.tzofar.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * צפירה אחת עם כל מה שנלווה אליה: עוצמה, רטט והבהוב פנס.
 * כל הקריאות מהחוט הראשי.
 */
object Honk {
    class Press internal constructor(internal val voice: Voice, internal val held: Boolean)

    private const val ONE_SHOT_EFFECT_MS = 350L
    private const val TORCH_TICK_MS = 120L

    /** אחרי הקול האחרון ממתינים רגע: שאר הקול עוד בדרך לרמקול, וייתכן שתבוא לחיצה נוספת. */
    private const val RESTORE_DELAY_MS = 400L

    private val main = Handler(Looper.getMainLooper())
    private val open = LinkedHashSet<Press>()
    private var appContext: Context? = null

    private var savedVolume: Int? = null
    private var heldCount = 0
    private var effectsUntil = 0L
    private var vibrating = false
    private var torchTicking = false
    private var torchLit = false

    private val restore = Runnable {
        // קול חדש התחיל בינתיים: הסיום שלו יזמן את ההחזרה
        if (!AudioEngine.isBusy) restoreVolume()
    }

    init {
        AudioEngine.onIdle = {
            main.post {
                main.removeCallbacks(restore)
                main.postDelayed(restore, RESTORE_DELAY_MS)
            }
        }
    }

    fun press(context: Context, id: String): Press? {
        val app = context.applicationContext
        appContext = app
        val sound = SoundBank.get(app, id) ?: return null
        main.removeCallbacks(restore)
        raiseVolume(app)
        val held = sound.loop != null
        val p = Press(AudioEngine.play(sound), held)
        open.add(p)
        if (held) heldCount++ else effectsUntil = SystemClock.uptimeMillis() + ONE_SHOT_EFFECT_MS
        if (Prefs.vibrate(app)) startVibration(app, held)
        if (Prefs.flash(app) && !torchTicking) {
            torchTicking = true
            main.post(torchTick)
        }
        return p
    }

    fun release(p: Press) {
        if (!open.remove(p)) return
        AudioEngine.release(p.voice)
        if (p.held) {
            heldCount--
            if (heldCount == 0) stopVibration()
        }
    }

    fun releaseAll() {
        for (p in open.toList()) release(p)
    }

    // --- עוצמה ---

    private fun raiseVolume(c: Context) {
        if (savedVolume != null) return
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.isVolumeFixed || headphonesConnected(am)) return
        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = max(1, (maxVolume * Prefs.volumePercent(c) / 100f).roundToInt())
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (target == current) return
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            savedVolume = current
        } catch (_: SecurityException) {
        }
    }

    private fun restoreVolume() {
        val saved = savedVolume ?: return
        savedVolume = null
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
        } catch (_: SecurityException) {
        }
    }

    /** באוזניות לא מעלים עוצמה לבד, כדי לא לפגוע בשמיעה. */
    @Suppress("DEPRECATION")
    private fun headphonesConnected(am: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return am.isWiredHeadsetOn || am.isBluetoothA2dpOn
        }
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    // --- רטט ---

    @Suppress("DEPRECATION")
    private fun startVibration(c: Context, held: Boolean) {
        val v = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!v.hasVibrator()) return
        if (!held) {
            if (vibrating) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(70)
            }
            return
        }
        if (vibrating) return
        vibrating = true
        val pattern = longArrayOf(0, 500)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
        } else {
            v.vibrate(pattern, 0, attrs)
        }
    }

    private fun stopVibration() {
        if (!vibrating) return
        vibrating = false
        (appContext?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    // --- פנס ---

    private val torchTick = object : Runnable {
        override fun run() {
            val active = heldCount > 0 || SystemClock.uptimeMillis() < effectsUntil
            if (active) {
                setTorch(!torchLit)
                main.postDelayed(this, TORCH_TICK_MS)
            } else {
                if (torchLit) setTorch(false)
                torchTicking = false
            }
        }
    }

    private fun setTorch(on: Boolean) {
        val c = appContext ?: return
        val id = torchCamera(c) ?: return
        torchLit = on
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            (c.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, on)
        } catch (_: Exception) {
            // המצלמה תפוסה אצל אפליקציה אחרת
        }
    }

    private fun torchCamera(c: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        if (!c.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return null
        return try {
            val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasTorch(c: Context): Boolean = torchCamera(c) != null
}
