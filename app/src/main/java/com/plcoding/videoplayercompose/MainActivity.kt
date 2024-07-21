package com.plcoding.videoplayercompose

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.plcoding.videoplayercompose.ui.theme.VideoPlayerComposeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoPlayerComposeTheme {
                VideoPlayerContent() // VideoPlayerContent bileşeni çağrılır.
            }
        }
    }
}

// VideoPlayerContent bileşeni, ana içeriği oluşturur.
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun VideoPlayerContent() {
    // ViewModel'i alır.
    val viewModel: MainViewModel = hiltViewModel()
    // Durum değerlerini toplar.
    val videoItems by viewModel.videoItems.collectAsState()
    val currentVideoThumbnail by viewModel.currentVideoThumbnail.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentContentUri by viewModel.currentContentUri.collectAsState()

    // İzinler için bir başlatıcı oluşturur.
    val requestPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // İzinlerin verilip verilmediğini kontrol eder.
        val granted = permissions.entries.all { it.value }
        if (granted) {
            viewModel.loadAllVideos() // Tüm videoları yükler.
        }
    }

    // İzinler için bir etkilişim başlatır.
    LaunchedEffect(true) {
        requestPermissions.launch(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO) // Android 13 ve üzeri için.
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE) // Daha düşük sürümler için.
            }
        )
    }

    // LifecycleOwner'ı alır.
    val lifecycleOwner = LocalLifecycleOwner.current
    // Lifecycle durumu için bir state hatırlar.
    var lifecycle by remember(lifecycleOwner) {
        mutableStateOf(Lifecycle.Event.ON_CREATE)
    }

    // Lifecycle değişikliklerini gözlemlemek için bir DisposableEffect kullanır.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycle = event
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Ana kolon düzenini oluşturur.
    Column(modifier = Modifier.fillMaxSize()) {
        // Video oynatıcı bileşenini ekler.
        PlayerViewContainer(
            viewModel = viewModel,
            lifecycle = lifecycle,
            thumbnailUri = currentVideoThumbnail,
            isPlaying = isPlaying,
            contentUri = currentContentUri
        )

        Spacer(modifier = Modifier.height(8.dp)) // Boşluk ekler.

        // Video durumu metnini ekler.
        videoItems.let {
            val text =
                if (videoItems.isEmpty()) "Loading videos..." else "Videos found: ${videoItems.size}"
            VideoStatusText(text)
        }

        // Video listesi bileşenini ekler.
        VideoList(videoItems = videoItems, viewModel = viewModel)
    }
}

// Video oynatıcı bileşeni.
@Composable
fun PlayerViewContainer(
    viewModel: MainViewModel,
    lifecycle: Lifecycle.Event,
    thumbnailUri: String?,
    isPlaying: Boolean,
    contentUri: Uri?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    when (lifecycle) {
                        Lifecycle.Event.ON_PAUSE -> {
                            onPause()
                            player?.pause()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            onResume()
                        }
                        else -> Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Eğer oynatma yoksa ve küçük resim ve içerik URI'si varsa, küçük resmi gösterir.
        if (!isPlaying && thumbnailUri != null && contentUri != null) {
            Image(
                painter = rememberAsyncImagePainter(model = thumbnailUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        viewModel.playVideo(contentUri) // Videoyu oynatır.
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Video",
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        .padding(8.dp),
                    tint = Color.White
                )
            }
        }
    }
}

// Video durumu metni bileşeni.
@Composable
fun VideoStatusText(text: String) {
    Row {
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

// Video listesi bileşeni.
@Composable
fun VideoList(videoItems: List<VideoItem>, viewModel: MainViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(videoItems) { item ->
            VideoListItem(item = item, onVideoClick = { viewModel.showThumbnail(item.contentUri) })
        }
    }
}

// Video liste öğesi bileşeni.
@Composable
fun VideoListItem(item: VideoItem, onVideoClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(1.dp)
            .clickable(onClick = onVideoClick)
    ) {
        item.thumbnailUri?.let { thumbnailUri ->
            Card(
                border = BorderStroke(0.8.dp, Color.White)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = thumbnailUri),
                    contentDescription = item.name,
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}