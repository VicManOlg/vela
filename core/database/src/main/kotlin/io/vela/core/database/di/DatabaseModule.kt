package io.vela.core.database.di

import android.content.Context
import androidx.room.Room
import io.vela.core.database.VelaDatabase
import io.vela.core.database.dao.CollectionDao
import io.vela.core.database.dao.GameDao
import io.vela.core.database.dao.LibraryDao
import io.vela.core.database.dao.MetadataDao
import io.vela.core.database.dao.PlaySessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VelaDatabase =
        Room.databaseBuilder(context, VelaDatabase::class.java, VelaDatabase.NAME)
            // Schema is young; wipe rather than crash until 1.0 ships real migrations.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideGameDao(db: VelaDatabase): GameDao = db.gameDao()
    @Provides fun provideMetadataDao(db: VelaDatabase): MetadataDao = db.metadataDao()
    @Provides fun provideLibraryDao(db: VelaDatabase): LibraryDao = db.libraryDao()
    @Provides fun provideCollectionDao(db: VelaDatabase): CollectionDao = db.collectionDao()
    @Provides fun providePlaySessionDao(db: VelaDatabase): PlaySessionDao = db.playSessionDao()
}
