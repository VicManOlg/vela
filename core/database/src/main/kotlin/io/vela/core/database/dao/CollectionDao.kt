package io.vela.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.vela.core.database.entity.CollectionEntity
import io.vela.core.database.entity.CollectionGameEntity
import io.vela.core.database.entity.GameSummaryView
import kotlinx.coroutines.flow.Flow

/** Collection row plus derived counters, used by the Home rail and the Collections screen. */
data class CollectionWithStats(
    val id: Long,
    val name: String,
    val kind: String,
    val icon: String,
    val accentColor: Long?,
    val sortOrder: Int,
    val gameCount: Int,
    val coverArt: String?,
)

@Dao
interface CollectionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(collection: CollectionEntity): Long

    @Update
    suspend fun update(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun byId(id: Long): CollectionEntity?

    @Query(
        """SELECT c.id, c.name, c.kind, c.icon, c.accentColor, c.sortOrder,
                  (SELECT COUNT(*) FROM collection_games cg JOIN games g ON g.id = cg.gameId
                     WHERE cg.collectionId = c.id AND g.hidden = 0 AND g.present = 1) AS gameCount,
                  (SELECT a.localPath FROM collection_games cg JOIN artwork a ON a.gameId = cg.gameId AND a.type = 'BOX_FRONT'
                     WHERE cg.collectionId = c.id ORDER BY cg.position LIMIT 1) AS coverArt
           FROM collections c ORDER BY c.sortOrder, c.name""",
    )
    fun observeCollections(): Flow<List<CollectionWithStats>>

    @Query(
        """SELECT s.* FROM game_summaries s JOIN collection_games cg ON cg.gameId = s.id
           WHERE cg.collectionId = :collectionId AND s.hidden = 0 AND s.present = 1
           ORDER BY cg.position, s.sortTitle""",
    )
    fun observeGames(collectionId: Long): Flow<List<GameSummaryView>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addGame(link: CollectionGameEntity)

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId AND gameId = :gameId")
    suspend fun removeGame(collectionId: Long, gameId: Long)

    @Query("SELECT collectionId FROM collection_games WHERE gameId = :gameId")
    fun observeCollectionsOfGame(gameId: Long): Flow<List<Long>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM collection_games WHERE collectionId = :collectionId")
    suspend fun nextPosition(collectionId: Long): Int

    @Transaction
    suspend fun addGameAtEnd(collectionId: Long, gameId: Long, addedAt: Long) {
        addGame(CollectionGameEntity(collectionId, gameId, nextPosition(collectionId), addedAt))
    }
}
