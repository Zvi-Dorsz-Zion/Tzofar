package com.tzofar.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

/** כפתור במסך הבית שמשמיע את הקול שבכפתור הגדול, בלי לפתוח את האפליקציה. */
class HornWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_HONK) honk(context) else super.onReceive(context, intent)
    }

    private fun honk(context: Context) {
        val app = context.applicationContext
        val result = goAsync()
        val main = Handler(Looper.getMainLooper())
        Thread {
            val id = Prefs.main(app)
            // בהפעלה קרה הקול עוד לא מוכן, והכנתו לא צריכה לתקוע את החוט הראשי
            val sound = SoundBank.get(app, id)
            main.post {
                if (sound == null) {
                    result.finish()
                    return@post
                }
                AudioEngine.hold()
                val press = Honk.press(app, id)
                val held = sound.loop != null
                val soundMs = if (held) HOLD_MS else Synth.lengthMs(sound)
                main.postDelayed({ press?.let(Honk::release) }, if (held) HOLD_MS.toLong() else 0L)
                main.postDelayed({
                    AudioEngine.unhold()
                    result.finish()
                }, (soundMs + sound.releaseMs + AFTER_SOUND_MS).toLong())
            }
        }.start()
    }

    companion object {
        private const val ACTION_HONK = "com.tzofar.app.HONK"

        /** לכפתור במסך הבית אין החזקה, אז צופר נשמע לזמן קבוע. */
        private const val HOLD_MS = 700
        private const val AFTER_SOUND_MS = 700

        private fun update(c: Context, manager: AppWidgetManager, ids: IntArray) {
            val info = Catalog.all(c).firstOrNull { it.id == Prefs.main(c) } ?: Catalog.builtIn.first()
            val intent = Intent(c, HornWidget::class.java).setAction(ACTION_HONK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pending = PendingIntent.getBroadcast(c, 0, intent, flags)
            for (id in ids) {
                val views = RemoteViews(c.packageName, R.layout.widget_horn)
                views.setTextViewText(R.id.widget_emoji, info.emoji)
                views.setTextViewText(R.id.widget_name, info.name)
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(id, views)
            }
        }

        /** אחרי החלפת הקול בכפתור הגדול. */
        fun refresh(c: Context) {
            val manager = AppWidgetManager.getInstance(c) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(c, HornWidget::class.java))
            if (ids.isNotEmpty()) update(c, manager, ids)
        }

        fun canPin(c: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                AppWidgetManager.getInstance(c)?.isRequestPinAppWidgetSupported == true

        fun requestPin(c: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AppWidgetManager.getInstance(c)?.requestPinAppWidget(ComponentName(c, HornWidget::class.java), null, null)
            }
        }
    }
}
