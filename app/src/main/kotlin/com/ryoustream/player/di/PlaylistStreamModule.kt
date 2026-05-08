package com.ryoustream.player.di

import com.ryoustream.player.data.repository.PlaylistRepositoryImpl
import com.ryoustream.player.data.repository.StreamRepositoryImpl
import com.ryoustream.player.domain.repository.PlaylistRepository
import com.ryoustream.player.domain.repository.StreamRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaylistStreamModule {

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository
}
