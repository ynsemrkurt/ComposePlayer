package com.example.videoplayercompose

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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

    // Videoların URI'lerini tutar
    private val _videoUris = MutableStateFlow<List<Uri>>(emptyList())
    private val videoUris: StateFlow<List<Uri>> = _videoUris

    // Geçerli video küçük resmini tutar
    private val _currentVideoThumbnail = MutableStateFlow<String?>(null)
    val currentVideoThumbnail: StateFlow<String?> = _currentVideoThumbnail

    // Oynatma durumunu tutar
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    // Geçerli içerik URI'sini tutar
    private val _currentContentUri = MutableStateFlow<Uri?>(null)
    val currentContentUri: StateFlow<Uri?> = _currentContentUri

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    // Video öğelerini URI'lerden oluşturur
    val videoItems = videoUris.map { uris ->
        uris.map { uri ->
            VideoItem(
                contentUri = uri,
                mediaItem = MediaItem.fromUri(uri),
                name = metaDataReader.getMetaDataFromUri(uri)?.fileName ?: "No name",
                thumbnailUri = loadThumbnail(uri),
                duration = getVideoDuration(getApplication(), uri)
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        player.prepare() // Player'ı hazırlayın
    }

    // Video oynatma fonksiyonu
    fun playVideo(uri: Uri) {
        val mediaItemToPlay = videoItems.value.find { it.contentUri == uri }?.mediaItem
        if (mediaItemToPlay != null) {
            player.setMediaItem(mediaItemToPlay)
            player.prepare()
            player.play()
            _isPlaying.value = true
            _currentVideoThumbnail.value = null // Oynatmaya başladığında küçük resmi gizle
        }
    }

    // Küçük resmi göster fonksiyonu
    fun showThumbnail(uri: Uri) {
        player.pause()
        val thumbnailUri = videoItems.value.find { it.contentUri == uri }?.thumbnailUri
        _currentVideoThumbnail.value = thumbnailUri
        _currentContentUri.value = uri // Tıklanan videonun URI'sini güncelle
        _isPlaying.value = false
    }

    // Tüm videoları yükle fonksiyonu
    fun loadAllVideos(filterStatus: String = "date desc") {
        viewModelScope.launch {
            val videos = queryAllVideos(filterStatus)
            _videoUris.value = videos.map { it.contentUri } // _videoUris'i List<Uri> ile ayarla
        }
    }

    // Tüm videoları sorgula fonksiyonu
    private suspend fun queryAllVideos(filterStatus: String): List<VideoItem> =
        withContext(Dispatchers.IO) {
            val filter = when (filterStatus) {
                "date asc" -> "${MediaStore.Video.Media.DATE_ADDED} ASC"
                "date desc" -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
                "duration asc" -> "${MediaStore.Video.Media.DURATION} ASC"
                "duration desc" -> "${MediaStore.Video.Media.DURATION} DESC"
                "name asc" -> "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
                "name desc" -> "${MediaStore.Video.Media.DISPLAY_NAME} DESC"
                else -> null
            }

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
                filter
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
                            thumbnailUri = loadThumbnail(contentUri),
                            duration = getVideoDuration(getApplication(), contentUri)
                        )
                    )
                }
            }
            videos
        }

    private suspend fun loadThumbnail(uri: Uri): String? = withContext(Dispatchers.IO) {
         try {
            val size = Size(250, 250)
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

    fun getVideoOrientation(uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(getApplication(), uri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
        retriever.release()

        return if (width > height) "landscape" else "portrait"
    }

    private fun getVideoDuration(context: Context, videoUri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        return durationStr?.toLongOrNull() ?: 0L
    }

    override fun onCleared() {
        super.onCleared()
        player.release() // Player'ı serbest bırak
    }
}