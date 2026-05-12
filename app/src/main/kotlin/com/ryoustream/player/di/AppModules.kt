package com.ryoustream.player.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.ryoustream.player.data.local.MediaPlaybackStateDao
import com.ryoustream.player.data.local.MediaStoreDataSource
import com.ryoustream.player.data.local.NetworkStreamDao
import com.ryoustream.player.data.local.PlaylistDao
import com.ryoustream.player.data.local.RyouDatabase
import com.ryoustream.player.data.repository.MediaRepositoryImpl
import com.ryoustream.player.data.repository.SettingsRepositoryImpl
import com.ryoustream.player.domain.repository.MediaRepository
import com.ryoustream.player.domain.repository.PlaylistRepository
import com.ryoustream.player.domain.repository.SettingsRepository
import com.ryoustream.player.domain.repository.StreamRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// ─── DATABASE MODULE ──────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RyouDatabase =
        RyouDatabase.create(context)

    @Provides
    @Singleton
    fun provideMediaPlaybackStateDao(db: RyouDatabase): MediaPlaybackStateDao =
        db.mediaPlaybackStateDao()

    @Provides
    @Singleton
    fun providePlaylistDao(db: RyouDatabase): PlaylistDao =
        db.playlistDao()

    @Provides
    @Singleton
    fun provideNetworkStreamDao(db: RyouDatabase): NetworkStreamDao =
        db.networkStreamDao()
}

// ─── REPOSITORY MODULE ────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

// ─── NETWORK MODULE ───────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE // Set BODY for debug
            }
        )
        .build()
}

// ─── MEDIA MODULE ─────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideTrackSelector(@ApplicationContext context: Context): DefaultTrackSelector {
        return DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(null) // Use device language
                    .setPreferredTextLanguage(null)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
            )
        }
    }
}
