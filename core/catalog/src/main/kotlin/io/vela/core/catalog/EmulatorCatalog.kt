package io.vela.core.catalog

import io.vela.core.model.CatalogEmulator
import io.vela.core.model.CatalogPlatform
import io.vela.core.model.KnownActivity
import io.vela.core.model.LaunchCatalog

/**
 * Read-only access to `catalog/emulators.json`, the generated catalogue of Android emulators and
 * their launch recipes (see `scripts/build_emulator_catalog.py`). Parsed lazily: it is about a
 * megabyte and only the emulator pages and the launch diagnostics need it.
 */
class EmulatorCatalog {

    val catalog: LaunchCatalog by lazy { CatalogResources.read("catalog/emulators.json", LaunchCatalog.serializer()) }

    val platforms: List<CatalogPlatform> get() = catalog.platforms

    private val byPlatformId: Map<String, CatalogPlatform> by lazy { platforms.associateBy { it.id } }
    private val byEmulatorId: Map<String, CatalogEmulator> by lazy { platforms.flatMap { it.emulators }.associateBy { it.id } }

    /** The Daijishō platform id (`nes`, `psx`, `switch`...), not Vela's; ids mostly coincide but check. */
    fun platform(id: String): CatalogPlatform? = byPlatformId[id]

    fun emulator(id: String): CatalogEmulator? = byEmulatorId[id]

    /** Every recipe that launches through [packageName], across platforms. */
    fun emulatorsForPackage(packageName: String): List<CatalogEmulator> =
        platforms.flatMap { it.emulators }.filter { it.packageName == packageName }

    /** Every package the catalogue knows; the app's manifest holds QUERY_ALL_PACKAGES so all are visible. */
    val allPackages: Set<String> by lazy { platforms.flatMap { it.emulators }.mapNotNull { it.packageName }.toSet() }

    /** ES-DE's package/activity pairs, for cross-checking a recipe that is not in the catalogue. */
    val knownActivities: List<KnownActivity> get() = catalog.knownActivities
}
