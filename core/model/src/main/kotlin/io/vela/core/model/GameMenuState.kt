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

/** The entries of the game context menu. */
enum class GameMenuAction { PLAY, DETAILS, FAVORITE, COLLECTIONS, LAUNCH_WITH, COMPLETION, RATE, REFRESH_METADATA, HIDE }

/** What the menu's dialogs report back; implemented by the data layer's menu controller. */
interface GameMenuActions {
    fun onAction(action: GameMenuAction)

    /** From the star picker; null clears the rating. */
    fun onRate(stars: Int?)

    fun onDismiss()
    fun onToggleCollection(id: CollectionId)
    fun onStartNewCollection()
    fun onCreateCollection(name: String)
    fun onLaunchWith(option: LaunchOption, remember: Boolean)
    fun onSetCompletion(status: CompletionStatus)
    fun onConfirmHide()
}

/** One-shot results of a menu choice that the screen acts on (navigation). */
sealed interface GameMenuEvent {
    data class OpenDetails(val game: GameSummary) : GameMenuEvent

    /** The game was hidden; a screen showing only that game should close. */
    data class Hidden(val game: GameSummary) : GameMenuEvent
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
