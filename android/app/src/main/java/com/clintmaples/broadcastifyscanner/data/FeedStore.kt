package com.clintmaples.broadcastifyscanner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class FeedStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadFeeds(): List<Feed> {
        val raw = prefs.getString(KEY_FEEDS, null) ?: return DefaultFeeds.ALL
        return try {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return DefaultFeeds.ALL
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("feedId").filter { it.isDigit() }
                    if (id.isNotEmpty()) {
                        add(Feed(id, obj.optString("name").ifBlank { "Feed $id" }))
                    }
                }
            }.ifEmpty { DefaultFeeds.ALL }
        } catch (_: Exception) {
            DefaultFeeds.ALL
        }
    }

    fun saveFeeds(feeds: List<Feed>) {
        val arr = JSONArray()
        feeds.forEach { feed ->
            arr.put(
                JSONObject()
                    .put("feedId", feed.feedId)
                    .put("name", feed.name),
            )
        }
        prefs.edit().putString(KEY_FEEDS, arr.toString()).apply()
    }

    fun loadMasterVolume(): Float = prefs.getFloat(KEY_MASTER, 0.8f).coerceIn(0f, 1f)

    fun saveMasterVolume(volume: Float) {
        prefs.edit().putFloat(KEY_MASTER, volume.coerceIn(0f, 1f)).apply()
    }

    fun loadKeepAwake(): Boolean = prefs.getBoolean(KEY_AWAKE, false)

    fun saveKeepAwake(value: Boolean) {
        prefs.edit().putBoolean(KEY_AWAKE, value).apply()
    }

    companion object {
        private const val PREFS = "broadcastify-scanner"
        private const val KEY_FEEDS = "feeds-v1"
        private const val KEY_MASTER = "master-volume"
        private const val KEY_AWAKE = "keep-awake"
    }
}
