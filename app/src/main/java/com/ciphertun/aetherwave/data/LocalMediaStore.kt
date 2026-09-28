package com.ciphertun.aetherwave.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.ciphertun.aetherwave.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads music already owned by the device through MediaStore. */
object LocalMediaStore {
    suspend fun scan(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val tracks = mutableListOf<Track>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                val uri = ContentUris.withAppendedId(collection, mediaId).toString()
                tracks += Track(
                    id = "local:$mediaId",
                    title = cursor.getString(title)?.takeIf { it.isNotBlank() } ?: "Unknown track",
                    artist = cursor.getString(artist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                    artworkUrl = null,
                    streamUrl = uri,
                    downloadUrl = null,
                    durationSeconds = cursor.getLong(duration).takeIf { it > 0 }?.div(1000)?.toInt(),
                    source = Track.Source.LOCAL,
                    language = null,
                    licenseNote = "Local audio on this device"
                )
            }
        }
        tracks
    }
}
