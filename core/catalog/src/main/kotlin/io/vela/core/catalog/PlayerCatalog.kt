package io.vela.core.catalog

import io.vela.core.common.VelaJson
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerDefinition
import io.vela.core.model.PlayerId
import kotlinx.serialization.builtins.ListSerializer

/**
 * Built-in emulator launch recipes from `catalog/players.json`, merged with optional user JSON.
 * Adding an emulator means adding a JSON entry, never code.
 */
class PlayerCatalog(userJson: String? = null) {

    val players: List<PlayerDefinition>
    private val byId: Map<PlayerId, PlayerDefinition>

    init {
        val builtIn = CatalogResources.readList("catalog/players.json", PlayerDefinition.serializer())
        val user = userJson?.let { VelaJson.decodeFromString(ListSerializer(PlayerDefinition.serializer()), it) }.orEmpty()
        players = (builtIn.associateBy { it.id } + user.associateBy { it.id }).values.toList()
        byId = players.associateBy { it.id }
    }

    operator fun get(id: PlayerId): PlayerDefinition? = byId[id]

    /** Players able to run [platformId], honouring the platform's preferred order first. */
    fun forPlatform(platformId: PlatformId, preferred: List<PlayerId>): List<PlayerDefinition> {
        val ordered = preferred.mapNotNull { byId[it] }.filter { it.supports(platformId) }
        val rest = players.filter { it.supports(platformId) && it !in ordered && it.platforms.isNotEmpty() }
        val generic = players.filter { it.platforms.isEmpty() && it !in ordered && it.coresFor(platformId).isNotEmpty() }
        return ordered + rest + generic
    }

    /** Every package name we may query; used to build manifest `<queries>` and installed checks. */
    val allPackages: Set<String> get() = players.flatMap { it.packages }.toSet()
}
