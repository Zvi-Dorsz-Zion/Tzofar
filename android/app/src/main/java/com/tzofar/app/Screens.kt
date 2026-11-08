package com.tzofar.app

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

private val INK = Color(0xFF1A1A1A)

private fun colorOf(kind: Kind): Color = when (kind) {
    Kind.HORN -> Color(0xFFF5A623)
    Kind.RUBBER -> Color(0xFFF06292)
    Kind.BELL -> Color(0xFF4FC3F7)
    Kind.RECORDING -> Color(0xFFBA68C8)
}

/** צופר נשמע כל עוד מחזיקים, ומתחיל ברגע הנגיעה ולא בהרמת האצבע. */
@Composable
private fun Modifier.honkPress(id: String, onPressed: (Boolean) -> Unit = {}): Modifier {
    val context = LocalContext.current
    return pointerInput(id) {
        detectTapGestures(onPress = {
            onPressed(true)
            val press = Honk.press(context, id)
            try {
                tryAwaitRelease()
            } finally {
                press?.let(Honk::release)
                onPressed(false)
            }
        })
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// --- מסך ראשי ---

@Composable
fun MainScreen(state: AppState, go: (Screen) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { go(Screen.SOUNDS) }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "בחירת קולות")
            }
            Text(
                "צופר",
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { go(Screen.SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "הגדרות")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            BigButton(state.info(state.main))
            // בפינות שמחוץ לעיגול. הם גם קודמים למגע, ולכן לא צופרים בהחלפת קול.
            IconButton(onClick = { state.step(-1) }, Modifier.align(Alignment.TopStart)) {
                // הסמלים מתהפכים לבד בעברית, ולכן כל חץ פונה החוצה
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "הקול הקודם")
            }
            IconButton(onClick = { state.step(1) }, Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "הקול הבא")
            }
        }

        if (state.small.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (id in state.small) SmallButton(state.info(id), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BigButton(info: SoundInfo) {
    var pressed by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "לצפור: ${info.name}"
            }
            .honkPress(info.id) { pressed = it },
        contentAlignment = Alignment.Center,
    ) {
        val diameter = min(maxWidth.value, maxHeight.value) * 0.94f
        val base = colorOf(info.kind)
        val density = LocalDensity.current
        Column(
            Modifier
                .size(diameter.dp)
                .graphicsLayer {
                    val s = if (pressed) 0.95f else 1f
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(if (pressed) base.copy(alpha = 0.72f) else base),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val emojiSize = with(density) { (diameter * 0.36f).dp.toSp() }
            val nameSize = with(density) { (diameter * 0.095f).dp.toSp() }
            Text(info.emoji, fontSize = emojiSize, lineHeight = emojiSize * 1.2f)
            Text(
                info.name,
                Modifier.padding(horizontal = 24.dp),
                color = INK,
                fontSize = nameSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmallButton(info: SoundInfo, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val base = colorOf(info.kind)
    Column(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) base.copy(alpha = 0.72f) else base)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "לצפור: ${info.name}"
            }
            .honkPress(info.id) { pressed = it }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(info.emoji, fontSize = 24.sp, lineHeight = 30.sp)
        Text(
            info.name,
            color = INK,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- בחירת קולות ---

@Composable
fun SoundsScreen(state: AppState, go: (Screen) -> Unit) {
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<SoundInfo?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 4.dp),
    ) {
        TopBar("בחירת קולות") { go(Screen.MAIN) }
        Text(
            "לחיצה על קול מעבירה אותו לכפתור הגדול. כוכב מוסיף אותו לכפתורים הקטנים, עד שלושה.",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { go(Screen.RECORD) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("הקלטת קול חדש", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(state.sounds, key = { it.id }) { info ->
                SoundRow(
                    info = info,
                    isMain = info.id == state.main,
                    isSmall = info.id in state.small,
                    onSelect = { state.select(info.id) },
                    onStar = {
                        if (!state.toggleSmall(info.id)) {
                            Toast.makeText(context, "אפשר עד שלושה כפתורים קטנים. קודם יש להסיר כוכב.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { toDelete = info },
                )
            }
        }
    }

    toDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text("למחוק את ההקלטה \"${info.name}\"? אי אפשר לשחזר אותה.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteRecording(info.id)
                    toDelete = null
                }) { Text("מחיקה") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("ביטול") }
            },
        )
    }
}

@Composable
private fun SoundRow(
    info: SoundInfo,
    isMain: Boolean,
    isSmall: Boolean,
    onSelect: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(info.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            info.name,
            Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = if (isMain) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (info.kind == Kind.RECORDING) {
            IconButton(onClick = onDelete, Modifier.size(38.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "מחיקה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier
                .size(38.dp)
                .semantics { contentDescription = "השמעה" }
                .honkPress(info.id),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
        }
        if (isMain) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = "בכפתור הגדול", tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            IconButton(onClick = onStar, Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (isSmall) "הסרה מהכפתורים הקטנים" else "הוספה לכפתורים הקטנים",
                    tint = if (isSmall) MaterialTheme.colorScheme.primary else Color(0xFF55595F),
                )
            }
        }
    }
}

// --- הגדרות ---

@Composable
fun SettingsScreen(onKeepScreenOnChanged: () -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current
    var volume by remember { mutableFloatStateOf(Prefs.volumePercent(c).toFloat()) }
    var volumeKeys by remember { mutableStateOf(Prefs.volumeKeys(c)) }
    var vibrate by remember { mutableStateOf(Prefs.vibrate(c)) }
    var flash by remember { mutableStateOf(Prefs.flash(c)) }
    var keepOn by remember { mutableStateOf(Prefs.keepScreenOn(c)) }
    val hasTorch = remember { Honk.hasTorch(c) }
    val canPin = remember { HornWidget.canPin(c) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 4.dp),
    ) {
        TopBar("הגדרות", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Text("עוצמת הצפירה: ${volume.toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { Prefs.setVolumePercent(c, volume.toInt()) },
                valueRange = 10f..100f,
                steps = 8,
            )
            Hint("בזמן הצפירה עוצמת המדיה עוברת לרמה הזו, ואחר כך חוזרת למה שהייתה. כשמחוברות אוזניות היא לא משתנה.")

            SwitchRow("צפירה בכפתורי העוצמה", "רק כשהאפליקציה פתוחה, במסך הראשי.", volumeKeys) {
                volumeKeys = it
                Prefs.setVolumeKeys(c, it)
            }
            SwitchRow("רטט בזמן צפירה", null, vibrate) {
                vibrate = it
                Prefs.setVibrate(c, it)
            }
            if (hasTorch) {
                SwitchRow("הבהוב פנס בזמן צפירה", null, flash) {
                    flash = it
                    Prefs.setFlash(c, it)
                }
            }
            SwitchRow("מסך דלוק כשהאפליקציה פתוחה", null, keepOn) {
                keepOn = it
                Prefs.setKeepScreenOn(c, it)
                onKeepScreenOnChanged()
            }

            if (canPin) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { HornWidget.requestPin(c) }, Modifier.fillMaxWidth()) {
                    Text("הוספת כפתור למסך הבית")
                }
                Hint("הכפתור משמיע את הקול שנמצא בכפתור הגדול.")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        Modifier.padding(top = 2.dp),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 16.sp, lineHeight = 20.sp)
            if (hint != null) Hint(hint)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// --- הקלטה ---

@Composable
fun RecordScreen(state: AppState, onDone: () -> Unit) {
    val c = LocalContext.current
    val hasPermission = {
        ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(hasPermission()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val recorder = remember { Recorder() }
    var recording by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var take by remember { mutableStateOf<Rendered?>(null) }
    var message by remember { mutableStateOf("") }
    val defaultName = "הקלטה ${Prefs.recordings(c).size + 1}"
    var name by remember { mutableStateOf(defaultName) }

    LaunchedEffect(recording) {
        progress = 0f
        val startedAt = System.currentTimeMillis()
        while (recording) {
            progress = (System.currentTimeMillis() - startedAt) / (Recorder.MAX_SECONDS * 1000f)
            delay(50)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 4.dp),
    ) {
        TopBar("הקלטת קול", onDone)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!granted) {
                Text("כדי להקליט צריך הרשאה למיקרופון.", textAlign = TextAlign.Center)
                if (asked) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("מתן הרשאה") }
                }
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    // אחרי הקלטה העיגול קטן, כדי ששמירה תיכנס גם במסך קטן
                    .size(if (take != null) 92.dp else 140.dp)
                    .clip(CircleShape)
                    .background(if (recording) Color(0xFFE53935) else Color(0xFFBA68C8))
                    .semantics { contentDescription = "החזקה מקליטה" }
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            take = null
                            message = ""
                            if (!recorder.start()) {
                                message = "המיקרופון לא זמין כרגע."
                                return@detectTapGestures
                            }
                            recording = true
                            var result: FloatArray? = null
                            try {
                                withTimeoutOrNull(Recorder.MAX_SECONDS * 1000L) { tryAwaitRelease() }
                            } finally {
                                result = recorder.stop()
                                recording = false
                            }
                            val samples = result
                            if (samples == null) {
                                message = "לא נקלט קול. נסו שוב, קרוב יותר למיקרופון."
                            } else {
                                take = Rendered(samples, null, 0, retrigger = true)
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("🎤", fontSize = if (take != null) 38.sp else 56.sp, lineHeight = if (take != null) 46.sp else 66.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (recording) {
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("מקליט...")
            } else {
                Text("מחזיקים ומקליטים, עד ${Recorder.MAX_SECONDS} שניות", textAlign = TextAlign.Center, fontSize = 14.sp)
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            }

            take?.let { sound ->
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { AudioEngine.play(sound) }, Modifier.fillMaxWidth()) {
                    Text("השמעה")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("שם") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        Recordings.add(c, name.trim().ifEmpty { defaultName }, sound.attack)
                        state.refresh()
                        onDone()
                    },
                    Modifier.fillMaxWidth(),
                ) { Text("שמירה") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
