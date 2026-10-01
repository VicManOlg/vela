package io.vela.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.vela.core.data.repository.CollectionRepository
import io.vela.core.data.repository.RoomCollectionRepository

/** Repositories exposed as interfaces (fakes in ViewModel tests) and their Room implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryBindings {
    @Binds
    abstract fun collections(impl: RoomCollectionRepository): CollectionRepository
}
