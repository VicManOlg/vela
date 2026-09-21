package io.vela.core.data.mapper

import io.vela.core.common.VelaJson
import io.vela.core.database.dao.CollectionWithStats
import io.vela.core.database.entity.ArtworkEntity
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.GameMetadataEntity
import io.vela.core.database.entity.GameSummaryView
import io.vela.core.database.entity.LibrarySourceEntity
import io.vela.core.database.entity.LocationType
import io.vela.core.database.entity.PlatformSettingsEntity
import io.vela.core.model.ArtworkType
import io.vela.core.model.CollectionId
import io.vela.core.model.CollectionKind
import io.vela.core.model.CompletionStatus
import io.vela.core.model.Game
import io.vela.core.model.GameCollection
import io.vela.core.model.GameId
import io.vela.core.model.GameKind
import io.vela.core.model.GameLocation
import io.vela.core.model.GameMetadata
import io.vela.core.model.GameSummary
import io.vela.core.model.LibrarySource
import io.vela.core.model.LibrarySourceId
import io.vela.core.model.PlatformId
import io.vela.core.model.PlatformSettings
import io.vela.core.model.PlayerId
import io.vela.core.model.SourceAccess
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

fun GameEntity.toLocation(): GameLocation = when (locationType) {
    LocationType.FILE -> GameLocation.File(locationValue)
    LocationType.DOCUMENT -> GameLocation.Document(locationValue)
    LocationType.ANDROID_APP -> GameLocation.AndroidApp(locationValue, locationExtra)
    LocationType.EXTERNAL -> GameLocation.External(locationValue)
}

fun GameEntity.toDomain(metadata: GameMetadataEntity?, artwork: List<ArtworkEntity>): Game = Game(
    id = GameId(id),
    platformId = PlatformId(platformId),
    kind = runCatching { GameKind.valueOf(kind) }.getOrDefault(GameKind.ROM),
    title = title,
    sortTitle = sortTitle,
    location = toLocation(),
    fileName = fileName,
    fileSize = fileSize,
    lastModified = lastModified,
    extension = extension,
    favorite = favorite,
    hidden = hidden,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    totalPlayTimeMs = totalPlayTimeMs,
    addedAt = addedAt,
    completion = runCatching { CompletionStatus.valueOf(completion) }.getOrDefault(CompletionStatus.NONE),
    userRating = userRating,
    playerOverride = playerOverride?.let(::PlayerId),
    coreOverride = coreOverride,
    metadata = metadata?.toDomain(),
    artwork = artwork.associate { runCatching { ArtworkType.valueOf(it.type) }.getOrDefault(ArtworkType.SCREENSHOT) to it.localPath },
)

fun GameMetadataEntity.toDomain(): GameMetadata = GameMetadata(
    title = title,
    description = description,
    developer = developer,
    publisher = publisher,
    releaseDate = releaseDate,
    genres = genres?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
    players = players,
    rating = rating,
    region = region,
    franchise = franchise,
    ageRating = ageRating,
    sourceUrl = sourceUrl,
    providerId = providerId,
    providerGameId = providerGameId,
    scrapedAt = scrapedAt,
)

fun GameSummaryView.toDomain(): GameSummary = GameSummary(
    id = GameId(id),
    platformId = PlatformId(platformId),
    kind = runCatching { GameKind.valueOf(kind) }.getOrDefault(GameKind.ROM),
    title = title,
    boxArt = boxArt,
    logo = logo,
    background = background,
    favorite = favorite,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    totalPlayTimeMs = totalPlayTimeMs,
    packageName = packageName,
)

fun LibrarySourceEntity.toDomain(): LibrarySource = LibrarySource(
    id = LibrarySourceId(id),
    uri = uri,
    displayName = displayName,
    access = runCatching { SourceAccess.valueOf(access) }.getOrDefault(SourceAccess.FILE),
    platformId = platformId?.let(::PlatformId),
    recursive = recursive,
    enabled = enabled,
    lastScanAt = lastScanAt,
    lastScanGameCount = lastScanGameCount,
)

private val overridesSerializer = MapSerializer(String.serializer(), String.serializer())

fun PlatformSettingsEntity.toDomain(): PlatformSettings = PlatformSettings(
    platformId = PlatformId(platformId),
    enabled = enabled,
    playerId = playerId?.let(::PlayerId),
    coreId = coreId,
    launchOverrides = launchOverrides?.let { runCatching { VelaJson.decodeFromString(overridesSerializer, it) }.getOrNull() }.orEmpty(),
    customName = customName,
)

fun PlatformSettings.toEntity(): PlatformSettingsEntity = PlatformSettingsEntity(
    platformId = platformId.value,
    enabled = enabled,
    playerId = playerId?.value,
    coreId = coreId,
    launchOverrides = launchOverrides.takeIf { it.isNotEmpty() }?.let { VelaJson.encodeToString(overridesSerializer, it) },
    customName = customName,
)

fun CollectionWithStats.toDomain(): GameCollection = GameCollection(
    id = CollectionId(id),
    name = name,
    kind = runCatching { CollectionKind.valueOf(kind) }.getOrDefault(CollectionKind.MANUAL),
    icon = icon,
    accentColor = accentColor,
    sortOrder = sortOrder,
    gameCount = gameCount,
    coverArt = coverArt,
)
