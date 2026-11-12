package com.github.itube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent store for locally liked music tracks.
 * Allows songs to be stored and accessed offline in the Library without requiring account sign-in.
 */
object LikedSongsStore {
    private const val PREFS = "itube_liked_songs"
    private const val KEY_TRACKS = "liked_tracks"

    private var prefs: SharedPreferences? = null

    val likedTracks = mutableStateListOf<MusicTrack>()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = sp
        val jsonStr = sp.getString(KEY_TRACKS, null)
        likedTracks.clear()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    likedTracks.add(
                        MusicTrack(
                            id = obj.getString("id"),
                            title = obj.optString("title", ""),
                            artists = obj.optString("artists", ""),
                            artistId = obj.optString("artistId").takeIf { it.isNotEmpty() },
                            album = obj.optString("album").takeIf { it.isNotEmpty() },
                            albumId = obj.optString("albumId").takeIf { it.isNotEmpty() },
                            thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotEmpty() },
                            durationSec = obj.optLong("durationSec", 0L),
                            liked = true,
                            isVideo = obj.optBoolean("isVideo", false)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isLiked(trackId: String): Boolean {
        return likedTracks.any { it.id == trackId }
    }

    fun add(track: MusicTrack) {
        val existingIndex = likedTracks.indexOfFirst { it.id == track.id }
        if (existingIndex >= 0) return
        likedTracks.add(0, track.copy(liked = true))
        save()
    }

    fun remove(trackId: String) {
        val existingIndex = likedTracks.indexOfFirst { it.id == trackId }
        if (existingIndex >= 0) {
            likedTracks.removeAt(existingIndex)
            save()
        }
    }

    fun toggle(track: MusicTrack): Boolean {
        return if (isLiked(track.id)) {
            remove(track.id)
            false
        } else {
            add(track)
            true
        }
    }

    private fun save() {
        val sp = prefs ?: return
        try {
            val array = JSONArray()
            for (track in likedTracks) {
                val obj = JSONObject()
                    .put("id", track.id)
                    .put("title", track.title)
                    .put("artists", track.artists)
                    .put("artistId", track.artistId ?: "")
                    .put("album", track.album ?: "")
                    .put("albumId", track.albumId ?: "")
                    .put("thumbnailUrl", track.thumbnailUrl ?: "")
                    .put("durationSec", track.durationSec)
                    .put("isVideo", track.isVideo)
                array.put(obj)
            }
            sp.edit().putString(KEY_TRACKS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
