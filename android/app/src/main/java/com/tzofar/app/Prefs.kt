package com.tzofar.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** ההגדרות של המשתמש. */
object Prefs {
    const val MAX_SMALL = 3

    private const val FILE = "settings"
    private const val DEFAULT_MAIN = "car"
    private val DEFAULT_SMALL = listOf("bell_classic", "truck", "clown")

    data class Recording(val id: String, val name: String)

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun main(c: Context): String = sp(c).getString("main", DEFAULT_MAIN) ?: DEFAULT_MAIN

    fun small(c: Context): List<String> {
        val raw = sp(c).getString("small", null) ?: return DEFAULT_SMALL
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    private fun save(c: Context, main: String, small: List<String>) {
        sp(c).edit().putString("main", main).putString("small", small.joinToString(",")).apply()
    }

    /** הקול הזה עובר לכפתור הגדול. אם היה בכפתור קטן, הקודם תופס את מקומו. */
    fun selectMain(c: Context, id: String) {
        val oldMain = main(c)
        if (id == oldMain) return
        save(c, id, small(c).map { if (it == id) oldMain else it })
    }

    /** @return false אם כל הכפתורים הקטנים תפוסים. */
    fun toggleSmall(c: Context, id: String): Boolean {
        val current = small(c)
        val mainId = main(c)
        when {
            id == mainId -> return true
            id in current -> save(c, mainId, current - id)
            current.size >= MAX_SMALL -> return false
            else -> save(c, mainId, current + id)
        }
        return true
    }

    fun volumePercent(c: Context): Int = sp(c).getInt("volume", 100)
    fun setVolumePercent(c: Context, v: Int) = sp(c).edit().putInt("volume", v).apply()

    fun volumeKeys(c: Context): Boolean = sp(c).getBoolean("volume_keys", false)
    fun setVolumeKeys(c: Context, v: Boolean) = sp(c).edit().putBoolean("volume_keys", v).apply()

    fun vibrate(c: Context): Boolean = sp(c).getBoolean("vibrate", false)
    fun setVibrate(c: Context, v: Boolean) = sp(c).edit().putBoolean("vibrate", v).apply()

    fun flash(c: Context): Boolean = sp(c).getBoolean("flash", false)
    fun setFlash(c: Context, v: Boolean) = sp(c).edit().putBoolean("flash", v).apply()

    fun keepScreenOn(c: Context): Boolean = sp(c).getBoolean("keep_on", true)
    fun setKeepScreenOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("keep_on", v).apply()

    fun recordings(c: Context): List<Recording> {
        val raw = sp(c).getString("recordings", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Recording(o.getString("id"), o.getString("name"))
        }
    }

    private fun saveRecordings(c: Context, list: List<Recording>) {
        val arr = JSONArray()
        for (r in list) arr.put(JSONObject().put("id", r.id).put("name", r.name))
        sp(c).edit().putString("recordings", arr.toString()).apply()
    }

    fun addRecording(c: Context, r: Recording) = saveRecordings(c, recordings(c) + r)

    fun removeRecording(c: Context, id: String) {
        saveRecordings(c, recordings(c).filter { it.id != id })
        val rest = small(c) - id
        val mainId = main(c)
        if (mainId == id) {
            // הכפתור הגדול לא נשאר ריק
            if (rest.isNotEmpty()) save(c, rest.first(), rest.drop(1)) else save(c, DEFAULT_MAIN, rest)
        } else {
            save(c, mainId, rest)
        }
    }
}
