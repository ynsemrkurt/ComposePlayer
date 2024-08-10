package com.example.videoplayercompose

import android.app.Application
import android.content.ContentResolver
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.videoplayercompose.MetaDataReader
import com.example.videoplayercompose.MetaDataReaderImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object VideoPlayerModule {

    @Provides
    @ViewModelScoped
    fun provideVideoPlayer(app: Application): Player {
        return ExoPlayer.Builder(app)
            .build()
    }

    @Provides
    @ViewModelScoped
    fun provideMetaDataReader(app: Application): MetaDataReader {
        return MetaDataReaderImpl(app)
    }

    @Provides
    fun provideContentResolver(application: Application): ContentResolver {
        return application.contentResolver
    }
}