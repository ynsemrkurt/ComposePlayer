package com.plcoding.videoplayercompose

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import com.plcoding.videoplayercompose.ui.theme.VideoPlayerComposeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoPlayerComposeTheme {
                VideoPlayerContent()
            }
        }
    }
}

@Composable
fun VideoPlayerContent() {
    val viewModel: MainViewModel = hiltViewModel()
    val videoItems by viewModel.videoItems.collectAsState()

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
        PlayerViewContainer(viewModel = viewModel, lifecycle = lifecycle)

        Spacer(modifier = Modifier.height(16.dp))

        VideoList(videoItems = videoItems, viewModel = viewModel)
    }
}

@Composable
fun PlayerViewContainer(viewModel: MainViewModel, lifecycle: Lifecycle.Event) {
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
        }, modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
    )
}

@Composable
fun VideoList(videoItems: List<VideoItem>, viewModel: MainViewModel) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(videoItems) { item ->
            VideoListItem(item = item, onVideoClick = { viewModel.playVideo(item.contentUri) })
        }
    }
}

@Composable
fun VideoListItem(item: VideoItem, onVideoClick: () -> Unit) {
    Text(
        text = item.name,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onVideoClick)
    )
}