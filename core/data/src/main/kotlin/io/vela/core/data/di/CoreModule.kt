package io.vela.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.vela.core.catalog.CatalogPlayers
import io.vela.core.catalog.EmulatorCatalog
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.catalog.PlayerCatalog
import io.vela.core.catalog.ThemeCatalog
import io.vela.core.common.ApplicationScope
import io.vela.core.common.DefaultDispatcherProvider
import io.vela.core.common.DispatcherProvider
import javax.inject.Singleton

/** Bindings for the pure-Kotlin modules that have no Hilt annotations of their own. */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides @Singleton
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides @Singleton
    fun provideApplicationScope(dispatchers: DispatcherProvider): ApplicationScope = ApplicationScope(dispatchers)

    @Provides @Singleton
    fun providePlatformCatalog(): PlatformCatalog = PlatformCatalog()

    @Provides @Singleton
    fun providePlayerCatalog(): PlayerCatalog = PlayerCatalog()

    @Provides @Singleton
    fun provideThemeCatalog(): ThemeCatalog = ThemeCatalog()

    /** Generated emulator catalogue (Daijishō + ES-DE, MIT); parsed lazily on first use. */
    @Provides @Singleton
    fun provideEmulatorCatalog(): EmulatorCatalog = EmulatorCatalog()

    /** Catalogue recipes as players, for emulators players.json does not cover. */
    @Provides @Singleton
    fun provideCatalogPlayers(emulators: EmulatorCatalog, platforms: PlatformCatalog, players: PlayerCatalog): CatalogPlayers =
        CatalogPlayers(emulators, platforms, players)
}
