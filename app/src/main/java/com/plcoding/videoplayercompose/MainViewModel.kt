package com.plcoding.videoplayercompose

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    val player: Player,
    private val metaDataReader: MetaDataReader
) : AndroidViewModel(application) {

    private val contentResolver: ContentResolver = application.contentResolver

    private val _videoUris = MutableStateFlow<List<Uri>>(emptyList())
    private val videoUris: StateFlow<List<Uri>> = _videoUris

    private val _currentVideoThumbnail = MutableStateFlow<String?>(null)
    val currentVideoThumbnail: StateFlow<String?> = _currentVideoThumbnail

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentContentUri = MutableStateFlow<Uri?>(null)
    val currentContentUri: StateFlow<Uri?> = _currentContentUri

    val videoItems = videoUris.map { uris ->
        uris.map { uri ->
            VideoItem(
                contentUri = uri,
                mediaItem = MediaItem.fromUri(uri),
                name = metaDataReader.getMetaDataFromUri(uri)?.fileName ?: "No name",
                thumbnailUri = loadThumbnail(uri)
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        player.prepare()
    }

    fun playVideo(uri: Uri) {
        val mediaItemToPlay = videoItems.value.find { it.contentUri == uri }?.mediaItem
        if (mediaItemToPlay != null) {
            player.setMediaItem(mediaItemToPlay)
            player.prepare()
            player.play()
            _isPlaying.value = true
            _currentVideoThumbnail.value = null // Oynatmaya başladığında thumbnail'ı gizle
        }
    }

    fun showThumbnail(uri: Uri) {
        val thumbnailUri = videoItems.value.find { it.contentUri == uri }?.thumbnailUri
        _currentVideoThumbnail.value = thumbnailUri
        _currentContentUri.value = uri // Tıklanan videonun URI'sini güncelle
        _isPlaying.value = false
    }

    fun loadAllVideos() {
        viewModelScope.launch {
            val videos = queryAllVideos()
            _videoUris.value = videos.map { it.contentUri } // Set _videoUris with List<Uri>
        }
    }

    private suspend fun queryAllVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )

        val cursor = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use { cursor1 ->
            val idColumn = cursor1.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor1.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            while (cursor1.moveToNext()) {
                val id = cursor1.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                val name = cursor1.getString(nameColumn)

                videos.add(
                    VideoItem(
                        contentUri = contentUri,
                        mediaItem = MediaItem.fromUri(contentUri),
                        name = name,
                        thumbnailUri = loadThumbnail(contentUri)
                    )
                )
            }
        }
        videos
    }

    private fun loadThumbnail(uri: Uri): String? {
        return try {
            val size = Size(400, 400)
            val bitmap: Bitmap = contentResolver.loadThumbnail(uri, size, null)
            val cacheDir = getApplication<Application>().cacheDir
            val tempFile = File.createTempFile("thumb_", ".jpg", cacheDir)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            Uri.fromFile(tempFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}