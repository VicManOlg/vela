package io.vela.feature.library

import com.google.common.truth.Truth.assertThat
import io.vela.core.data.repository.CollectionRepository
import io.vela.core.model.CollectionId
import io.vela.core.model.CollectionKind
import io.vela.core.model.GameCollection
import io.vela.core.model.GameId
import io.vela.core.model.GameSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

private class FakeCollectionRepository : CollectionRepository {
    val names = MutableStateFlow<List<String>>(emptyList())

    override fun observeCollections(): Flow<List<GameCollection>> = names.map { list ->
        list.mapIndexed { i, name -> GameCollection(id = CollectionId(i.toLong() + 1), name = name, kind = CollectionKind.MANUAL) }
    }
    override fun observeGames(id: CollectionId): Flow<List<GameSummary>> = emptyFlow()
    override fun observeCollectionsOfGame(gameId: GameId): Flow<Set<CollectionId>> = emptyFlow()
    override suspend fun create(name: String, icon: String, accentColor: Long?): CollectionId {
        names.value = names.value + name
        return CollectionId(names.value.size.toLong())
    }
    override suspend fun rename(id: CollectionId, name: String) {
        names.value = names.value.mapIndexed { i, n -> if (i.toLong() + 1 == id.value) name else n }
    }
    override suspend fun delete(id: CollectionId) {
        names.value = names.value.filterIndexed { i, _ -> i.toLong() + 1 != id.value }
    }
    override suspend fun addGame(id: CollectionId, gameId: GameId) = Unit
    override suspend fun removeGame(id: CollectionId, gameId: GameId) = Unit
    override suspend fun toggleGame(id: CollectionId, gameId: GameId, member: Boolean) = Unit
}

class CollectionsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeCollectionRepository()
    // Lazy: viewModelScope must be created after the rule installed the Main dispatcher.
    private val viewModel by lazy { CollectionsViewModel(repo) }

    @Test
    fun `collections are unknown until the first read, then follow the repository`() = runTest(main.dispatcher) {
        assertThat(viewModel.collections.value).isNull()
        backgroundScope.launch { viewModel.collections.collect {} }

        assertThat(viewModel.collections.value).isEmpty()
        viewModel.create("Zelda")
        viewModel.create("Pokémon")
        assertThat(viewModel.collections.value!!.map { it.name }).containsExactly("Zelda", "Pokémon").inOrder()
    }

    @Test
    fun `rename and delete go through the repository`() = runTest(main.dispatcher) {
        backgroundScope.launch { viewModel.collections.collect {} }
        viewModel.create("Zelda")
        viewModel.create("Mario")

        viewModel.rename(CollectionId(1), "The Legend of Zelda")
        viewModel.delete(CollectionId(2))

        assertThat(viewModel.collections.value!!.map { it.name }).containsExactly("The Legend of Zelda")
    }
}
