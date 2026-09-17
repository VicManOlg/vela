package io.vela.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.GameSummaryView
import io.vela.core.database.entity.LocationType
import io.vela.core.database.entity.PlatformCount
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    // ---- Writes -------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(game: GameEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(games: List<GameEntity>): List<Long>

    @Update
    suspend fun update(game: GameEntity)

    @Query("SELECT * FROM games WHERE locationType = :type AND locationValue = :value LIMIT 1")
    suspend fun findByLocation(type: LocationType, value: String): GameEntity?

    @Query("SELECT id, locationValue, fileSize, lastModified FROM games WHERE sourceId = :sourceId")
    suspend fun fileSignaturesForSource(sourceId: Long): List<FileSignature>

    @Query("SELECT id, locationValue, fileSize, lastModified FROM games WHERE locationType IN ('FILE','DOCUMENT')")
    suspend fun allFileSignatures(): List<FileSignature>

    @Query(
        """UPDATE games SET fileSize = :fileSize, lastModified = :lastModified, present = 1, scanGeneration = :generation,
           platformId = :platformId, fileName = :fileName WHERE id = :id""",
    )
    suspend fun touchScanned(id: Long, fileSize: Long, lastModified: Long, generation: Long, platformId: String, fileName: String)

    @Query("UPDATE games SET present = 1, scanGeneration = :generation WHERE id IN (:ids)")
    suspend fun markSeen(ids: List<Long>, generation: Long)

    @Query("UPDATE games SET present = 0 WHERE sourceId = :sourceId AND scanGeneration < :generation AND present = 1")
    suspend fun markMissingForSource(sourceId: Long, generation: Long): Int

    @Query("DELETE FROM games WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long): Int

    @Query("DELETE FROM games WHERE present = 0 AND playCount = 0 AND favorite = 0")
    suspend fun purgeMissingUnplayed(): Int

    @Query("UPDATE games SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE games SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE games SET completion = :completion WHERE id = :id")
    suspend fun setCompletion(id: Long, completion: String)

    @Query("UPDATE games SET userRating = :rating WHERE id = :id")
    suspend fun setUserRating(id: Long, rating: Int?)

    @Query("UPDATE games SET playerOverride = :playerId, coreOverride = :coreId WHERE id = :id")
    suspend fun setPlayerOverride(id: Long, playerId: String?, coreId: String?)

    @Query("UPDATE games SET platformId = :platformId WHERE id = :id")
    suspend fun setPlatform(id: Long, platformId: String)

    @Query("UPDATE games SET title = :title, sortTitle = :sortTitle, duplicateKey = :duplicateKey WHERE id = :id")
    suspend fun rename(id: Long, title: String, sortTitle: String, duplicateKey: String)

    @Query(
        """UPDATE games SET playCount = playCount + 1, lastPlayedAt = :startedAt WHERE id = :id""",
    )
    suspend fun recordLaunch(id: Long, startedAt: Long)

    @Query("UPDATE games SET totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE id = :id")
    suspend fun addPlayTime(id: Long, durationMs: Long)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM games WHERE locationType = 'ANDROID_APP' AND locationValue IN (:packages)")
    suspend fun deleteAndroidApps(packages: List<String>)

    // ---- Reads --------------------------------------------------------------------------------

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun byId(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM game_summaries WHERE id = :id")
    fun observeSummary(id: Long): Flow<GameSummaryView?>

    @Query("SELECT * FROM games WHERE locationType = 'ANDROID_APP'")
    suspend fun androidApps(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games WHERE hidden = 0 AND present = 1")
    fun observeTotalCount(): Flow<Int>

    @Query(
        """SELECT platformId, COUNT(*) AS count FROM games
           WHERE hidden = 0 AND present = 1 GROUP BY platformId""",
    )
    fun observePlatformCounts(): Flow<List<PlatformCount>>

    @Query(
        """SELECT * FROM game_summaries WHERE hidden = 0 AND present = 1 AND lastPlayedAt IS NOT NULL
           ORDER BY lastPlayedAt DESC LIMIT :limit""",
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<GameSummaryView>>

    @Query(
        """SELECT * FROM game_summaries WHERE hidden = 0 AND present = 1 AND favorite = 1
           ORDER BY sortTitle LIMIT :limit""",
    )
    fun observeFavorites(limit: Int): Flow<List<GameSummaryView>>

    @Query(
        """SELECT * FROM game_summaries WHERE hidden = 0 AND present = 1 AND completion = 'PLAYING'
           ORDER BY lastPlayedAt DESC LIMIT :limit""",
    )
    fun observePlaying(limit: Int): Flow<List<GameSummaryView>>

    @Query(
        """SELECT * FROM game_summaries WHERE hidden = 0 AND present = 1
           ORDER BY addedAt DESC LIMIT :limit""",
    )
    fun observeRecentlyAdded(limit: Int): Flow<List<GameSummaryView>>

    @Query(
        """SELECT * FROM game_summaries WHERE hidden = 0 AND present = 1 AND platformId = :platformId
           ORDER BY sortTitle LIMIT :limit""",
    )
    fun observeByPlatformPreview(platformId: String, limit: Int): Flow<List<GameSummaryView>>

    /**
     * Library-only recommendations: games the user never launched, on platforms they play most,
     * sharing a genre with recently played titles when metadata exists.
     */
    @Query(
        """SELECT s.* FROM game_summaries s
           WHERE s.hidden = 0 AND s.present = 1 AND s.playCount = 0
             AND s.platformId IN (
               SELECT platformId FROM games WHERE playCount > 0 GROUP BY platformId ORDER BY SUM(playCount) DESC LIMIT 3)
           ORDER BY (s.genres IS NOT NULL AND s.genres IN (
               SELECT m.genres FROM game_metadata m JOIN games g ON g.id = m.gameId
               WHERE g.lastPlayedAt IS NOT NULL ORDER BY g.lastPlayedAt DESC LIMIT 10)) DESC,
             s.rating DESC, RANDOM()
           LIMIT :limit""",
    )
    fun observeRecommendations(limit: Int): Flow<List<GameSummaryView>>

    @RawQuery(observedEntities = [GameSummaryView::class])
    fun pagingSummaries(query: SupportSQLiteQuery): PagingSource<Int, GameSummaryView>

    @RawQuery(observedEntities = [GameSummaryView::class])
    fun observeSummaries(query: SupportSQLiteQuery): Flow<List<GameSummaryView>>

    @Query(
        """SELECT s.* FROM game_summaries s JOIN games_fts f ON f.rowid = s.id
           WHERE games_fts MATCH :ftsQuery AND s.hidden = 0 AND s.present = 1
           ORDER BY s.sortTitle LIMIT :limit""",
    )
    suspend fun searchTitles(ftsQuery: String, limit: Int): List<GameSummaryView>

    @Query(
        """SELECT s.* FROM game_summaries s
           WHERE s.hidden = 0 AND s.present = 1 AND s.genres LIKE '%' || :genre || '%'
           ORDER BY s.sortTitle LIMIT :limit""",
    )
    suspend fun searchGenre(genre: String, limit: Int): List<GameSummaryView>

    @Query(
        """SELECT s.* FROM game_summaries s WHERE s.duplicateKey = :duplicateKey AND s.id != :exceptId
           AND s.present = 1 ORDER BY s.discNumber, s.title""",
    )
    suspend fun duplicatesOf(duplicateKey: String, exceptId: Long): List<GameSummaryView>

    @Query("SELECT DISTINCT genres FROM game_metadata WHERE genres IS NOT NULL")
    suspend fun allGenreStrings(): List<String>

    @Transaction
    suspend fun replaceAndroidApps(current: List<GameEntity>, removedPackages: List<String>) {
        if (removedPackages.isNotEmpty()) deleteAndroidApps(removedPackages)
        insertAllIgnore(current)
    }
}

/** Minimal row used by the incremental scanner to diff the filesystem against the database. */
data class FileSignature(
    val id: Long,
    val locationValue: String,
    val fileSize: Long,
    val lastModified: Long,
)
