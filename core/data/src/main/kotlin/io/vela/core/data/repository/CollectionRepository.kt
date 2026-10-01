package io.vela.core.data.repository

import io.vela.core.common.DispatcherProvider
import io.vela.core.data.mapper.toDomain
import io.vela.core.database.dao.CollectionDao
import io.vela.core.database.entity.CollectionEntity
import io.vela.core.database.entity.CollectionGameEntity
import io.vela.core.model.CollectionId
import io.vela.core.model.CollectionKind
import io.vela.core.model.GameCollection
import io.vela.core.model.GameId
import io.vela.core.model.GameSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val dao: CollectionDao,
    private val dispatchers: DispatcherProvider,
) {
    fun observeCollections(): Flow<List<GameCollection>> = dao.observeCollections().map { it.map { c -> c.toDomain() } }.distinctUntilChanged()

    fun observeGames(id: CollectionId): Flow<List<GameSummary>> = dao.observeGames(id.value).map { it.map { g -> g.toDomain() } }.distinctUntilChanged()

    fun observeCollectionsOfGame(gameId: GameId): Flow<Set<CollectionId>> =
        dao.observeCollectionsOfGame(gameId.value).map { ids -> ids.map(::CollectionId).toSet() }

    suspend fun create(name: String, icon: String = "collection", accentColor: Long? = null): CollectionId = withContext(dispatchers.io) {
        CollectionId(
            dao.insert(CollectionEntity(name = name.trim(), kind = CollectionKind.MANUAL.name, icon = icon, accentColor = accentColor, createdAt = System.currentTimeMillis())),
        )
    }

    suspend fun rename(id: CollectionId, name: String) = withContext(dispatchers.io) {
        dao.byId(id.value)?.let { dao.update(it.copy(name = name.trim())) }
    }

    suspend fun delete(id: CollectionId) = withContext(dispatchers.io) { dao.delete(id.value) }

    suspend fun addGame(id: CollectionId, gameId: GameId) = withContext(dispatchers.io) {
        dao.addGameAtEnd(id.value, gameId.value, System.currentTimeMillis())
    }

    suspend fun removeGame(id: CollectionId, gameId: GameId) = withContext(dispatchers.io) { dao.removeGame(id.value, gameId.value) }

    suspend fun toggleGame(id: CollectionId, gameId: GameId, member: Boolean) =
        if (member) removeGame(id, gameId) else addGame(id, gameId)
}
