package io.vela.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import io.vela.core.common.DispatcherProvider
import io.vela.core.common.TitleCleaner
import io.vela.core.data.mapper.toDomain
import io.vela.core.database.dao.GameDao
import io.vela.core.database.dao.MetadataDao
import io.vela.core.model.CompletionStatus
import io.vela.core.model.Game
import io.vela.core.model.GameId
import io.vela.core.model.GameSort
import io.vela.core.model.GameSummary
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Filters for a game grid. */
data class GameQuery(
    val platformId: PlatformId? = null,
    val favoritesOnly: Boolean = false,
    val completion: CompletionStatus? = null,
    val genre: String? = null,
    val sort: GameSort = GameSort.TITLE,
    val hideDuplicateRegions: Boolean = true,
    val showHidden: Boolean = false,
)

@Singleton
class GameRepository @Inject constructor(
    private val gameDao: GameDao,
    private val metadataDao: MetadataDao,
    private val dispatchers: DispatcherProvider,
) {
    fun observeGame(id: GameId): Flow<Game?> =
        gameDao.observeById(id.value).flatMapLatest { entity ->
            if (entity == null) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                combine(metadataDao.observeMetadata(id.value), metadataDao.observeArtwork(id.value)) { meta, art ->
                    entity.toDomain(meta, art)
                }
            }
        }

    suspend fun game(id: GameId): Game? = withContext(dispatchers.io) {
        val entity = gameDao.byId(id.value) ?: return@withContext null
        entity.toDomain(metadataDao.metadata(id.value), metadataDao.artwork(id.value))
    }

    fun observeSummary(id: GameId): Flow<GameSummary?> = gameDao.observeSummary(id.value).map { it?.toDomain() }

    /** Paged grid; the same SQL powers [observeGames] for small result sets. */
    fun pagedGames(query: GameQuery): Flow<PagingData<GameSummary>> = Pager(
        config = PagingConfig(pageSize = 60, prefetchDistance = 60, enablePlaceholders = true, initialLoadSize = 120),
        pagingSourceFactory = { gameDao.pagingSummaries(buildQuery(query)) },
    ).flow.map { data -> data.map { it.toDomain() } }

    fun observeGames(query: GameQuery, limit: Int = 5000): Flow<List<GameSummary>> =
        gameDao.observeSummaries(buildQuery(query, limit)).map { list -> list.map { it.toDomain() } }

    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<GameSummary>> = gameDao.observeRecentlyPlayed(limit).map { it.map { v -> v.toDomain() } }
    fun observeFavorites(limit: Int = 30): Flow<List<GameSummary>> = gameDao.observeFavorites(limit).map { it.map { v -> v.toDomain() } }
    fun observePlaying(limit: Int = 12): Flow<List<GameSummary>> = gameDao.observePlaying(limit).map { it.map { v -> v.toDomain() } }
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<GameSummary>> = gameDao.observeRecentlyAdded(limit).map { it.map { v -> v.toDomain() } }
    fun observeRecommendations(limit: Int = 20): Flow<List<GameSummary>> = gameDao.observeRecommendations(limit).map { it.map { v -> v.toDomain() } }
    fun observeTotalCount(): Flow<Int> = gameDao.observeTotalCount()

    fun observeByPlatformPreview(platformId: PlatformId, limit: Int = 12): Flow<List<GameSummary>> =
        gameDao.observeByPlatformPreview(platformId.value, limit).map { it.map { v -> v.toDomain() } }

    /** Other games of the same series, oldest first (needs franchise metadata on both sides). */
    suspend fun sameFranchise(game: Game, limit: Int = 20): List<GameSummary> = withContext(dispatchers.io) {
        val franchise = game.metadata?.franchise ?: return@withContext emptyList()
        gameDao.sameFranchise(franchise, game.id.value, limit).map { it.toDomain() }
    }

    suspend fun duplicatesOf(game: Game): List<GameSummary> = withContext(dispatchers.io) {
        gameDao.duplicatesOf("${game.platformId}:${game.sortTitle}", game.id.value).map { it.toDomain() }
    }

    /** Title search through FTS with prefix matching on every word; falls back to genre contains. */
    suspend fun search(text: String, limit: Int = 200): List<GameSummary> = withContext(dispatchers.io) {
        val terms = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return@withContext emptyList()
        val fts = terms.joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
        val byTitle = runCatching { gameDao.searchTitles(fts, limit) }.getOrDefault(emptyList())
        val byGenre = if (terms.size == 1) gameDao.searchGenre(terms.single(), limit) else emptyList()
        (byTitle + byGenre).distinctBy { it.id }.map { it.toDomain() }
    }

    suspend fun genres(): List<String> = withContext(dispatchers.io) {
        gameDao.allGenreStrings().flatMap { it.split('|') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    suspend fun setFavorite(id: GameId, favorite: Boolean) = withContext(dispatchers.io) { gameDao.setFavorite(id.value, favorite) }
    suspend fun setHidden(id: GameId, hidden: Boolean) = withContext(dispatchers.io) { gameDao.setHidden(id.value, hidden) }
    suspend fun setCompletion(id: GameId, status: CompletionStatus) = withContext(dispatchers.io) { gameDao.setCompletion(id.value, status.name) }
    suspend fun setUserRating(id: GameId, rating: Int?) = withContext(dispatchers.io) { gameDao.setUserRating(id.value, rating) }
    suspend fun setPlayerOverride(id: GameId, player: PlayerId?, coreId: String?) =
        withContext(dispatchers.io) { gameDao.setPlayerOverride(id.value, player?.value, coreId) }
    suspend fun setPlatform(id: GameId, platformId: PlatformId) = withContext(dispatchers.io) { gameDao.setPlatform(id.value, platformId.value) }

    suspend fun rename(id: GameId, title: String) = withContext(dispatchers.io) {
        val game = gameDao.byId(id.value) ?: return@withContext
        val sort = TitleCleaner.sortKey(title)
        gameDao.rename(id.value, title, sort, "${game.platformId}:$sort")
    }

    suspend fun delete(id: GameId) = withContext(dispatchers.io) { gameDao.delete(id.value) }

    private fun buildQuery(q: GameQuery, limit: Int? = null): SimpleSQLiteQuery {
        val where = mutableListOf("present = 1")
        val args = mutableListOf<Any>()
        if (!q.showHidden) where += "hidden = 0"
        q.platformId?.let { where += "platformId = ?"; args += it.value }
        if (q.favoritesOnly) where += "favorite = 1"
        q.completion?.let { where += "completion = ?"; args += it.name }
        q.genre?.let { where += "genres LIKE ?"; args += "%$it%" }
        if (q.hideDuplicateRegions) {
            // One row per duplicateKey: the one the user played, else the lowest id (stable).
            where += """id IN (SELECT id FROM game_summaries s2 WHERE s2.duplicateKey = game_summaries.duplicateKey
                AND s2.present = 1 ORDER BY s2.playCount DESC, s2.id ASC LIMIT 1)"""
        }
        val order = when (q.sort) {
            GameSort.TITLE -> "sortTitle ASC"
            GameSort.LAST_PLAYED -> "lastPlayedAt DESC NULLS LAST, sortTitle ASC"
            GameSort.MOST_PLAYED -> "playCount DESC, totalPlayTimeMs DESC, sortTitle ASC"
            GameSort.RECENTLY_ADDED -> "addedAt DESC, sortTitle ASC"
            GameSort.RELEASE_YEAR -> "releaseDate DESC NULLS LAST, sortTitle ASC"
            GameSort.RATING -> "rating DESC NULLS LAST, sortTitle ASC"
        }
        val sql = "SELECT * FROM game_summaries WHERE ${where.joinToString(" AND ")} ORDER BY $order" +
            (limit?.let { " LIMIT $it" } ?: "")
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
