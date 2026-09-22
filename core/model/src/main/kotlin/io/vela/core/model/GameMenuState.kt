package io.vela.core.model

/**
 * Presentation state of the shared game context menu. Lives in the model module so the data
 * layer can drive it and the UI module can render it without depending on each other.
 */
sealed interface GameMenuState {
    data object Hidden : GameMenuState

    data class Context(val game: GameSummary, val completion: CompletionStatus) : GameMenuState

    data class Collections(
        val game: GameSummary,
        val collections: List<GameCollection>,
        val memberOf: Set<CollectionId>,
    ) : GameMenuState

    data class NewCollection(val game: GameSummary) : GameMenuState

    data class LaunchWith(val game: GameSummary, val options: List<LaunchOption>) : GameMenuState

    data class Completion(val game: GameSummary, val current: CompletionStatus) : GameMenuState

    /** Star picker; [current] is the user's rating (1..5) or null. */
    data class Rate(val game: GameSummary, val current: Int?) : GameMenuState

    data class ConfirmHide(val game: GameSummary) : GameMenuState
}

/** One "Launch with" choice: a player, optionally with a specific core. */
data class LaunchOption(
    val playerId: PlayerId,
    val playerName: String,
    val coreId: String?,
    val coreName: String?,
    val installed: Boolean,
    val isCurrent: Boolean,
) {
    val id: String get() = "${playerId.value}:${coreId.orEmpty()}"
    val label: String get() = if (coreName != null) "$playerName · $coreName" else playerName
}
