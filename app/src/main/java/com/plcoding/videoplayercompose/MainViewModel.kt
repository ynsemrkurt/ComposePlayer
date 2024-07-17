package com.plcoding.videoplayercompose

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
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
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val contentResolver: ContentResolver,
    val player: Player,
    private val metaDataReader: MetaDataReader
) : ViewModel() {

    private val _videoUris = MutableStateFlow<List<Uri>>(emptyList())
    private val videoUris: StateFlow<List<Uri>> = _videoUris


    val videoItems = videoUris.map { uris ->
        uris.map { uri ->
            VideoItem(
                contentUri = uri,
                mediaItem = MediaItem.fromUri(uri),
                name = metaDataReader.getMetaDataFromUri(uri)?.fileName ?: "No name"
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
        }
    }

    fun loadAllVideos() {
        viewModelScope.launch {
            val videos = queryAllVideos()
            _videoUris.value = videos
        }
    }

    private suspend fun queryAllVideos(): List<Uri> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID
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
            while (cursor1.moveToNext()) {
                val id = cursor1.getLong(idColumn)
                val contentUri =
                    Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                videos.add(contentUri)
            }
        }

        videos
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}