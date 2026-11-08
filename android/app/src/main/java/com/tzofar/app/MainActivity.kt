package com.tzofar.app

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

enum class Screen { MAIN, SOUNDS, SETTINGS, RECORD }

/** מה שעל המסך, מסונכרן עם ההגדרות ועם הכפתור שבמסך הבית. */
class AppState(private val c: Context) {
    var main by mutableStateOf(Prefs.main(c))
        private set
    var small by mutableStateOf(Prefs.small(c))
        private set
    var sounds by mutableStateOf(Catalog.all(c))
        private set

    fun info(id: String): SoundInfo = sounds.firstOrNull { it.id == id } ?: Catalog.builtIn.first()

    fun refresh() {
        main = Prefs.main(c)
        small = Prefs.small(c)
        sounds = Catalog.all(c)
        HornWidget.refresh(c)
    }

    fun select(id: String) {
        Prefs.selectMain(c, id)
        refresh()
    }

    fun step(delta: Int) {
        val i = sounds.indexOfFirst { it.id == main }
        select(sounds[(i + delta + sounds.size) % sounds.size].id)
    }

    /** @return false אם כל הכפתורים הקטנים תפוסים. */
    fun toggleSmall(id: String): Boolean {
        val ok = Prefs.toggleSmall(c, id)
        refresh()
        return ok
    }

    fun deleteRecording(id: String) {
        Recordings.delete(c, id)
        refresh()
    }
}

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.MAIN)
    private var volumeKeyPress: Honk.Press? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SoundBank.preload(this)
        applyKeepScreenOn()
        val state = AppState(this)
        val colors = darkColorScheme(
            primary = Color(0xFFF5A623),
            onPrimary = Color(0xFF1A1A1A),
            background = Color(0xFF121418),
            surface = Color(0xFF121418),
        )
        setContent {
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        BackHandler(enabled = screen != Screen.MAIN) {
                            screen = if (screen == Screen.RECORD) Screen.SOUNDS else Screen.MAIN
                        }
                        when (screen) {
                            Screen.MAIN -> MainScreen(state) { screen = it }
                            Screen.SOUNDS -> SoundsScreen(state) { screen = it }
                            Screen.SETTINGS -> SettingsScreen(onKeepScreenOnChanged = ::applyKeepScreenOn) { screen = Screen.MAIN }
                            Screen.RECORD -> RecordScreen(state) { screen = Screen.SOUNDS }
                        }
                    }
                }
            }
        }
    }

    private fun applyKeepScreenOn() {
        if (Prefs.keepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onStart() {
        super.onStart()
        AudioEngine.hold()
    }

    override fun onStop() {
        // האפליקציה ברקע: אף צופר לא נשאר תקוע
        Honk.releaseAll()
        volumeKeyPress = null
        AudioEngine.unhold()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolumeKey && screen == Screen.MAIN && Prefs.volumeKeys(this)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && volumeKeyPress == null) {
                volumeKeyPress = Honk.press(this, Prefs.main(this))
            } else if (event.action == KeyEvent.ACTION_UP) {
                volumeKeyPress?.let(Honk::release)
                volumeKeyPress = null
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
