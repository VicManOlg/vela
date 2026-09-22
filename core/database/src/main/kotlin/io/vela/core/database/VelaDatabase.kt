package io.vela.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.vela.core.database.dao.CollectionDao
import io.vela.core.database.dao.GameDao
import io.vela.core.database.dao.LibraryDao
import io.vela.core.database.dao.MetadataDao
import io.vela.core.database.dao.PlaySessionDao
import io.vela.core.database.entity.ArtworkEntity
import io.vela.core.database.entity.CollectionEntity
import io.vela.core.database.entity.CollectionGameEntity
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.GameFtsEntity
import io.vela.core.database.entity.GameMetadataEntity
import io.vela.core.database.entity.GameSummaryView
import io.vela.core.database.entity.LibrarySourceEntity
import io.vela.core.database.entity.LocationType
import io.vela.core.database.entity.PlatformSettingsEntity
import io.vela.core.database.entity.PlaySessionEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 3,
    exportSchema = true,
    // 2 -> 3: game_summaries gains userRating (view only, no table change).
    autoMigrations = [AutoMigration(from = 2, to = 3)],
    entities = [
        GameEntity::class,
        GameFtsEntity::class,
        GameMetadataEntity::class,
        ArtworkEntity::class,
        PlaySessionEntity::class,
        PlatformSettingsEntity::class,
        LibrarySourceEntity::class,
        CollectionEntity::class,
        CollectionGameEntity::class,
    ],
    views = [GameSummaryView::class],
)
@TypeConverters(VelaTypeConverters::class)
abstract class VelaDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun metadataDao(): MetadataDao
    abstract fun libraryDao(): LibraryDao
    abstract fun collectionDao(): CollectionDao
    abstract fun playSessionDao(): PlaySessionDao

    companion object {
        const val NAME = "vela.db"
    }
}

class VelaTypeConverters {
    @TypeConverter
    fun locationTypeToString(value: LocationType): String = value.name

    @TypeConverter
    fun stringToLocationType(value: String): LocationType = LocationType.valueOf(value)
}

/** 1 -> 2: franchise, age rating and description source on game_metadata. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game_metadata ADD COLUMN franchise TEXT")
        db.execSQL("ALTER TABLE game_metadata ADD COLUMN ageRating TEXT")
        db.execSQL("ALTER TABLE game_metadata ADD COLUMN sourceUrl TEXT")
    }
}
