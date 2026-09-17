package io.vela.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.vela.core.database.entity.PlaySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaySessionDao {

    @Insert
    suspend fun insert(session: PlaySessionEntity): Long

    @Query("UPDATE play_sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun end(id: Long, endedAt: Long)

    @Query("SELECT * FROM play_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSession(): PlaySessionEntity?

    @Query("SELECT * FROM play_sessions WHERE gameId = :gameId ORDER BY startedAt DESC LIMIT :limit")
    fun observeForGame(gameId: Long, limit: Int): Flow<List<PlaySessionEntity>>

    @Query("SELECT COALESCE(SUM(endedAt - startedAt), 0) FROM play_sessions WHERE endedAt IS NOT NULL AND startedAt >= :since")
    fun observeTotalSince(since: Long): Flow<Long>

    @Query("DELETE FROM play_sessions WHERE endedAt IS NULL AND startedAt < :olderThan")
    suspend fun dropStaleOpenSessions(olderThan: Long)
}
