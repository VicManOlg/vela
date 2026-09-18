package io.vela.feature.library

import kotlinx.serialization.Serializable

/** Type-safe navigation routes owned by the library feature. */
@Serializable
data class GameGridRoute(
    val platformId: String? = null,
    val collectionId: Long? = null,
    val favorites: Boolean = false,
    val title: String? = null,
)

@Serializable
data class GameDetailRoute(val gameId: Long)

@Serializable
data object CollectionsRoute
