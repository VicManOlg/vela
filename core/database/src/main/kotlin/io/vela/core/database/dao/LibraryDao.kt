package io.vela.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import io.vela.core.database.entity.LibrarySourceEntity
import io.vela.core.database.entity.PlatformSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // ---- Sources ------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: LibrarySourceEntity): Long

    @Update
    suspend fun updateSource(source: LibrarySourceEntity)

    @Query("DELETE FROM library_sources WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("SELECT * FROM library_sources ORDER BY id")
    fun observeSources(): Flow<List<LibrarySourceEntity>>

    @Query("SELECT * FROM library_sources ORDER BY id")
    suspend fun sources(): List<LibrarySourceEntity>

    @Query("SELECT * FROM library_sources WHERE id = :id")
    suspend fun source(id: Long): LibrarySourceEntity?

    @Query("UPDATE library_sources SET lastScanAt = :at, lastScanGameCount = :count WHERE id = :id")
    suspend fun recordScan(id: Long, at: Long, count: Int)

    // ---- Platform settings --------------------------------------------------------------------

    @Upsert
    suspend fun upsertPlatformSettings(settings: PlatformSettingsEntity)

    @Query("SELECT * FROM platform_settings")
    fun observePlatformSettings(): Flow<List<PlatformSettingsEntity>>

    @Query("SELECT * FROM platform_settings WHERE platformId = :platformId")
    suspend fun platformSettings(platformId: String): PlatformSettingsEntity?

    @Query("SELECT * FROM platform_settings")
    suspend fun allPlatformSettings(): List<PlatformSettingsEntity>
}
