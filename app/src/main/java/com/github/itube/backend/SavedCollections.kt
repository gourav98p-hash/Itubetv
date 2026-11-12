package com.github.itube

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Public albums and playlists the user chose to keep in the local Library. */
object SavedCollections {
    private const val PREFS = "saved_public_collections"
    private const val KEY_ITEMS = "items"
    private lateinit var prefs: android.content.SharedPreferences

    var items by mutableStateOf<List<PublicCollection>>(emptyList())
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        items = decode(prefs.getString(KEY_ITEMS, null))
    }

    fun contains(url: String): Boolean = items.any { it.url == url }

    fun toggle(context: Context, collection: PublicCollection): Boolean {
        if (!::prefs.isInitialized) init(context)
        val removing = contains(collection.url)
        items = if (removing) items.filterNot { it.url == collection.url }
        else listOf(collection) + items
        prefs.edit().putString(KEY_ITEMS, JSONArray().apply {
            items.forEach { item ->
                put(JSONObject()
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("subtitle", item.subtitle)
                    .put("thumbnail", item.thumbnail)
                    .put("isAlbum", item.isAlbum))
            }
        }.toString()).apply()
        return !removing
    }

    private fun decode(raw: String?): List<PublicCollection> = runCatching {
        val array = JSONArray(raw ?: return emptyList())
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val url = value.optString("url")
                if (url.isBlank()) continue
                add(PublicCollection(
                    url = url,
                    title = value.optString("title"),
                    subtitle = value.optString("subtitle"),
                    thumbnail = value.optString("thumbnail").ifBlank { null },
                    isAlbum = value.optBoolean("isAlbum"),
                ))
            }
        }
    }.getOrDefault(emptyList())
}
