package com.ciphertun.aetherwave.data

import android.app.DownloadManager
import android.content.Context
import com.ciphertun.aetherwave.model.DownloadStatus
import com.ciphertun.aetherwave.model.LibraryEntry
import com.ciphertun.aetherwave.model.LibrarySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the downloads library + favorites as one small JSON file under
 * the app's private storage. Deliberately not Room/SQLite — a few hundred
 * entries at most, read/written as a whole, and it keeps the dependency
 * list short. Swap for Room if the library ever grows into the thousands.
 *
 * The actual audio bytes never live here — [LibraryEntry.localUri] just
 * points at the file DownloadManager already saved into the public
 * Music/Podcasts folder (see Downloader.kt).
 */
object LibraryStore {

    private lateinit var appContext: Context
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _snapshot = MutableStateFlow(LibrarySnapshot())
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _snapshot.value = readFromDisk()
    }

    private fun file(): File = File(appContext.filesDir, "library.json")

    private fun readFromDisk(): LibrarySnapshot = runCatching {
        val f = file()
        if (!f.exists()) return LibrarySnapshot()
        json.decodeFromString(LibrarySnapshot.serializer(), f.readText())
    }.getOrDefault(LibrarySnapshot())

    private suspend fun persist(next: LibrarySnapshot) = mutex.withLock {
        _snapshot.value = next
        withContext(Dispatchers.IO) {
            runCatching { file().writeText(json.encodeToString(LibrarySnapshot.serializer(), next)) }
        }
    }

    suspend fun addPending(entry: LibraryEntry) {
        persist(_snapshot.value.let { it.copy(entries = it.entries + entry) })
    }

    /** Polls DownloadManager for real progress/status of every unfinished entry. */
    suspend fun reconcile() {
        val current = _snapshot.value
        val unfinished = current.entries.filter {
            it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RUNNING
        }
        if (unfinished.isEmpty()) return

        val updates = withContext(Dispatchers.IO) {
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(*unfinished.map { it.downloadId }.toLongArray())
            val result = mutableMapOf<Long, LibraryEntry>()

            manager.query(query)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val original = unfinished.firstOrNull { it.downloadId == id } ?: continue

                    val statusCol = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = if (localUriCol >= 0) cursor.getString(localUriCol) else null

                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else original.progressPercent
                    val newStatus = when (statusCol) {
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETE
                        DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                        DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                        else -> DownloadStatus.PENDING
                    }
                    result[id] = original.copy(
                        status = newStatus,
                        progressPercent = percent,
                        localUri = localUri ?: original.localUri
                    )
                }
            }
            result
        }
        if (updates.isEmpty()) return

        val merged = current.entries.map { updates[it.downloadId] ?: it }
        persist(current.copy(entries = merged))
    }

    suspend fun remove(id: String) {
        val current = _snapshot.value
        val target = current.entries.firstOrNull { it.id == id } ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                (appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(target.downloadId)
            }
        }
        persist(
            current.copy(
                entries = current.entries.filterNot { it.id == id },
                favoriteIds = current.favoriteIds - id
            )
        )
    }

    suspend fun toggleFavorite(id: String) {
        val current = _snapshot.value
        val next = if (id in current.favoriteIds) current.favoriteIds - id else current.favoriteIds + id
        persist(current.copy(favoriteIds = next))
    }

}
