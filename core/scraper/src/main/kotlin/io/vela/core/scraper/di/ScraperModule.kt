package io.vela.core.scraper.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.vela.core.scraper.MetadataProvider
import io.vela.core.scraper.ProviderRegistry
import io.vela.core.scraper.provider.LibretroThumbnailsProvider
import io.vela.core.scraper.provider.ScreenScraperProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScraperBindings {
    @Binds @IntoSet abstract fun bindLibretro(p: LibretroThumbnailsProvider): MetadataProvider
    @Binds @IntoSet abstract fun bindScreenScraper(p: ScreenScraperProvider): MetadataProvider
}

@Module
@InstallIn(SingletonComponent::class)
object ScraperModule {

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Vela/0.1 (Android; +https://github.com/vela-frontend)").build())
        }
        .build()

    @Provides @Singleton
    fun provideRegistry(providers: Set<@JvmSuppressWildcards MetadataProvider>): ProviderRegistry = ProviderRegistry(providers)
}
