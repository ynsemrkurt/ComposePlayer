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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        ) // Boşluk ekler.

        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            videoItems.let {
                val text =
                    if (videoItems.isEmpty()) "Loading videos..." else "Videos found: ${videoItems.size}"
                Text(text)
            }
            Spacer(Modifier.weight(1f))
            DropDownFilter(viewModel)
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

@Composable
fun DropDownFilter(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("date desc") } // Varsayılan olarak en yeni videolar

    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Default.FilterAlt,
                contentDescription = "Filter"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val filters = listOf(
                "date asc" to "Date Added (Oldest First)",
                "date desc" to "Date Added (Newest First)",
                "duration asc" to "Duration (Shortest First)",
                "duration desc" to "Duration (Longest First)",
                "name asc" to "Name (A-Z)",
                "name desc" to "Name (Z-A)"
            )

            filters.forEach { (filterKey, filterLabel) ->
                DropdownMenuItem(onClick = {
                    selectedFilter = filterKey
                    viewModel.loadAllVideos(filterKey) // Seçilen filtreyi uygula
                    expanded = false
                }) {
                    Text(text = filterLabel)
                }
            }
        }
    }
}