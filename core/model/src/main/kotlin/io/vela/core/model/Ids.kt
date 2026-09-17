package io.vela.core.model

import kotlinx.serialization.Serializable

/** Database identity of a [Game]. */
@JvmInline
@Serializable
value class GameId(val value: Long) {
    override fun toString(): String = value.toString()
}

/** Stable, human-readable platform key such as `snes`, `ps2` or `android`. */
@JvmInline
@Serializable
value class PlatformId(val value: String) {
    override fun toString(): String = value

    companion object {
        /** Virtual platform for installed Android games. */
        val ANDROID = PlatformId("android")

        /** Virtual platform for installed Android apps the user pinned (Settings, Chrome...). */
        val ANDROID_APPS = PlatformId("android_apps")

        /** Virtual platform for PC games launched through Winlator, GameHub, streaming, etc. */
        val PC = PlatformId("pc")
    }
}

/** Identifier of a [PlayerDefinition] (an emulator / launcher recipe), e.g. `retroarch64`. */
@JvmInline
@Serializable
value class PlayerId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
value class CollectionId(val value: Long) {
    override fun toString(): String = value.toString()
}

@JvmInline
@Serializable
value class LibrarySourceId(val value: Long) {
    override fun toString(): String = value.toString()
}
