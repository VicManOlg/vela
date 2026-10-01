package io.vela.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.vela.core.database.entity.PlaySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaySessionDao {

    @Insert
    suspend fun insert(session: PlaySessionEntity): Long

    @Query("UPDATE play_sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun end(id: Long, endedAt: Long)

    @Query("UPDATE games SET totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE id = :gameId")
    suspend fun addPlayTime(gameId: Long, durationMs: Long)

    /**
     * Ends a session and credits its time to the game in one commit. Apart, a process death in
     * between closes the session without the time, and recovery never revisits closed sessions.
     */
    @Transaction
    suspend fun close(sessionId: Long, gameId: Long, startedAt: Long, durationMs: Long) {
        end(sessionId, startedAt + durationMs)
        addPlayTime(gameId, durationMs)
    }

    @Query("SELECT * FROM play_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSession(): PlaySessionEntity?

    @Query("SELECT * FROM play_sessions WHERE gameId = :gameId ORDER BY startedAt DESC LIMIT :limit")
    fun observeForGame(gameId: Long, limit: Int): Flow<List<PlaySessionEntity>>

    @Query("SELECT COALESCE(SUM(endedAt - startedAt), 0) FROM play_sessions WHERE endedAt IS NOT NULL AND startedAt >= :since")
    fun observeTotalSince(since: Long): Flow<Long>

    @Query("DELETE FROM play_sessions WHERE endedAt IS NULL AND startedAt < :olderThan")
    suspend fun dropStaleOpenSessions(olderThan: Long)
}
