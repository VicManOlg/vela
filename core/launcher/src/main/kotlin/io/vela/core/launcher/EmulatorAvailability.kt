package io.vela.core.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.catalog.EmulatorCatalog
import io.vela.core.model.CatalogEmulator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers, for any recipe in the emulator catalogue, whether it can be launched on this device:
 * the package must be installed and the activity must exist. Checking the activity with
 * `resolveActivity` before `startActivity` turns a would-be `ActivityNotFoundException` into a
 * clear status. Package visibility: the app declares QUERY_ALL_PACKAGES, so every package is
 * visible on Android 11+; without that permission, `<queries>` entries would be needed per package.
 *
 * This class only reports. Launching still goes through [GameLauncher] and `players.json`.
 */
@Singleton
class EmulatorAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: EmulatorCatalog,
) {
    enum class Status {
        /** The package is not installed. */
        NOT_INSTALLED,
        /** The package is installed but the recipe's activity does not exist (renamed or removed). */
        ACTIVITY_MISSING,
        /** Package installed and activity resolvable. */
        READY,
    }

    data class Entry(val emulator: CatalogEmulator, val status: Status)

    fun status(emulator: CatalogEmulator): Status {
        val pkg = emulator.packageName ?: return Status.NOT_INSTALLED
        if (!isInstalled(pkg)) return Status.NOT_INSTALLED
        val activity = emulator.activity ?: return Status.READY
        return if (resolves(pkg, activity, emulator.action)) Status.READY else Status.ACTIVITY_MISSING
    }

    /** Every recipe of a catalogue platform with its status; READY ones first, catalogue order otherwise. */
    fun forPlatform(catalogPlatformId: String): List<Entry> =
        catalog.platform(catalogPlatformId)?.emulators.orEmpty()
            .map { Entry(it, status(it)) }
            .sortedBy { it.status != Status.READY }

    /** Installed packages the catalogue knows, whatever the platform. */
    fun installedPackages(): Set<String> = catalog.allPackages.filterTo(HashSet()) { isInstalled(it) }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** An explicit intent resolves iff the activity exists in that package (exported or not is checked at start time). */
    private fun resolves(packageName: String, activity: String, action: String?): Boolean {
        val intent = Intent().apply {
            component = ComponentName(packageName, activity)
            if (action != null) this.action = action
        }
        return context.packageManager.resolveActivity(intent, 0) != null
    }
}
