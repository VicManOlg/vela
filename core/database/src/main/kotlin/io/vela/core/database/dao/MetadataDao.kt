package io.vela.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.vela.core.database.entity.ArtworkEntity
import io.vela.core.database.entity.GameMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataDao {

    @Upsert
    suspend fun upsertMetadata(metadata: GameMetadataEntity)

    @Query("SELECT * FROM game_metadata WHERE gameId = :gameId")
    suspend fun metadata(gameId: Long): GameMetadataEntity?

    @Query("SELECT * FROM game_metadata WHERE gameId = :gameId")
    fun observeMetadata(gameId: Long): Flow<GameMetadataEntity?>

    @Query("DELETE FROM game_metadata WHERE gameId = :gameId")
    suspend fun deleteMetadata(gameId: Long)

    @Query("SELECT gameId FROM game_metadata")
    suspend fun scrapedGameIds(): List<Long>

    @Upsert
    suspend fun upsertArtwork(artwork: ArtworkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtwork(artwork: List<ArtworkEntity>)

    @Query("SELECT * FROM artwork WHERE gameId = :gameId")
    suspend fun artwork(gameId: Long): List<ArtworkEntity>

    @Query("SELECT * FROM artwork WHERE gameId = :gameId")
    fun observeArtwork(gameId: Long): Flow<List<ArtworkEntity>>

    @Query("DELETE FROM artwork WHERE gameId = :gameId AND type = :type")
    suspend fun deleteArtwork(gameId: Long, type: String)

    @Query("DELETE FROM artwork WHERE gameId = :gameId")
    suspend fun deleteAllArtwork(gameId: Long)

    @Query("SELECT localPath FROM artwork")
    suspend fun allArtworkPaths(): List<String>

    @Query("SELECT SUM(LENGTH(localPath)) FROM artwork")
    suspend fun artworkCount(): Int
}
