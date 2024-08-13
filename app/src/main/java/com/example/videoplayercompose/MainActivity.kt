package com.example.videoplayercompose

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.example.videoplayercompose.ui.theme.VideoPlayerComposeTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoPlayerComposeTheme {
                Surface {
                    VideoPlayerContent()
                }
            }
        }
    }
}

@Composable
fun VideoPlayerContent() {
    val viewModel: MainViewModel = hiltViewModel()
    val videoItems by viewModel.videoItems.collectAsState()
    val currentVideoThumbnail by viewModel.currentVideoThumbnail.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentContentUri by viewModel.currentContentUri.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()

    val requestPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            viewModel.loadAllVideos()
        }
    }

    LaunchedEffect(true) {
        requestPermissions.launch(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        )
    }

    val current = LocalContext.current

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            val orientation = currentContentUri?.let {
                viewModel.getVideoOrientation(it)
            } ?: "portrait"
            (current as? Activity)?.requestedOrientation =
                if (orientation == "landscape") ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            (current as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycle by remember(lifecycleOwner) {
        mutableStateOf(Lifecycle.Event.ON_CREATE)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycle = event
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PlayerViewContainer(
            viewModel = viewModel,
            lifecycle = lifecycle,
            thumbnailUri = currentVideoThumbnail,
            isPlaying = isPlaying,
            contentUri = currentContentUri,
            isFullscreen = isFullscreen
        )

        Row(
            Modifier
                .fillMaxWidth()
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

        VideoList(videoItems = videoItems, viewModel = viewModel)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.Blue, shape = CircleShape)
                .clickable {
                    viewModel.toggleFullscreen()
                }
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Toggle Fullscreen",
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun PlayerViewContainer(
    viewModel: MainViewModel,
    lifecycle: Lifecycle.Event,
    thumbnailUri: String?,
    isPlaying: Boolean,
    contentUri: Uri?,
    isFullscreen: Boolean
) {
    Box(
        modifier = Modifier
            .then(
                if (isFullscreen) Modifier.fillMaxSize() else Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
            )
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
                        viewModel.playVideo(contentUri)
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
                border = BorderStroke(0.8.dp, Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = thumbnailUri),
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    item.duration.let { durationMillis ->
                        val durationText = formatDuration(durationMillis)
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = durationText,
                                color = Color.White,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DropDownFilter(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("date desc") }

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
                DropdownMenuItem(
                    text = { Text(text = filterLabel) },
                    onClick = {
                        selectedFilter = filterKey
                        viewModel.loadAllVideos(filterKey)
                        expanded = false
                    })
            }
        }
    }
}

fun formatDuration(durationMillis: Long): String {
    val minutes = (durationMillis / 1000 / 60).toInt()
    val seconds = (durationMillis / 1000 % 60).toInt()
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}