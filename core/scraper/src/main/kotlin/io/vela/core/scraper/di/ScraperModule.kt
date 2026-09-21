package io.vela.core.scraper.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.vela.core.scraper.MetadataProvider
import io.vela.core.scraper.ProviderRegistry
import io.vela.core.scraper.provider.LibretroIndex
import io.vela.core.scraper.provider.LibretroNameSource
import io.vela.core.scraper.provider.LibretroThumbnailsProvider
import io.vela.core.scraper.provider.ScreenScraperProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import io.vela.core.scraper.provider.LibretroDatabase
import io.vela.core.scraper.provider.LibretroMetadataSource
import io.vela.core.scraper.provider.WikipediaSummaries
import io.vela.core.scraper.provider.DescriptionSource
import io.vela.core.scraper.provider.SteamGridDbProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class ScraperBindings {
    @Binds @IntoSet abstract fun bindLibretro(p: LibretroThumbnailsProvider): MetadataProvider
    @Binds @IntoSet abstract fun bindScreenScraper(p: ScreenScraperProvider): MetadataProvider
    @Binds abstract fun bindLibretroNames(i: LibretroIndex): LibretroNameSource
    @Binds abstract fun bindLibretroDatabase(d: LibretroDatabase): LibretroMetadataSource
    @Binds abstract fun bindDescriptions(w: WikipediaSummaries): DescriptionSource
    @Binds @IntoSet abstract fun bindSteamGridDb(p: SteamGridDbProvider): MetadataProvider
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
