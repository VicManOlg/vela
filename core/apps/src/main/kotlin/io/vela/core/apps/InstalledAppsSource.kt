package io.vela.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.common.DispatcherProvider
import io.vela.core.model.InstalledApp
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads launchable applications from the PackageManager and classifies games vs. apps. */
@Singleton
class InstalledAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val pm: PackageManager get() = context.packageManager

    suspend fun installedApps(includeSystem: Boolean): List<InstalledApp> = withContext(dispatchers.io) {
        val launchables = LinkedHashMap<String, ResolveInfo>()
        queryLaunchers(Intent.CATEGORY_LAUNCHER).forEach { launchables.putIfAbsent(it.activityInfo.packageName, it) }
        queryLaunchers(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach { launchables.putIfAbsent(it.activityInfo.packageName, it) }

        launchables.values.mapNotNull { info ->
            val app = info.activityInfo.applicationInfo
            val pkg = app.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0 && app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            if (isSystem && !includeSystem && pkg !in ALWAYS_LISTED_SYSTEM) return@mapNotNull null
            val packageInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
            InstalledApp(
                packageName = pkg,
                label = info.loadLabel(pm)?.toString() ?: pkg,
                activity = info.activityInfo.name,
                isGame = isGame(app),
                isSystem = isSystem,
                installedAt = packageInfo?.firstInstallTime ?: 0L,
                updatedAt = packageInfo?.lastUpdateTime ?: 0L,
                versionName = packageInfo?.versionName,
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun isInstalled(packageName: String): Boolean =
        runCatching { pm.getPackageInfo(packageName, 0) }.isSuccess

    fun label(packageName: String): String? =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrNull()

    private fun queryLaunchers(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

    @Suppress("DEPRECATION")
    private fun isGame(app: ApplicationInfo): Boolean =
        app.category == ApplicationInfo.CATEGORY_GAME || (app.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    companion object {
        /** System apps users regularly pin in a console launcher. */
        val ALWAYS_LISTED_SYSTEM = setOf(
            "com.android.settings", "com.android.chrome", "com.google.android.youtube", "com.android.vending",
            "com.google.android.apps.nbu.files", "com.android.documentsui", "com.google.android.documentsui",
        )

        /** Quick-add suggestions for the Apps section: label -> package candidates. */
        val SUGGESTED_APPS: List<Pair<String, List<String>>> = listOf(
            "Settings" to listOf("com.android.settings"),
            "Chrome" to listOf("com.android.chrome"),
            "YouTube" to listOf("com.google.android.youtube"),
            "Discord" to listOf("com.discord"),
            "Files" to listOf("com.google.android.apps.nbu.files", "com.android.documentsui", "com.google.android.documentsui"),
            "Play Store" to listOf("com.android.vending"),
            "Steam Link" to listOf("com.valvesoftware.steamlink"),
            "Moonlight" to listOf("com.limelight"),
            "RetroArch" to listOf("com.retroarch.aarch64", "com.retroarch"),
            "Winlator" to listOf("com.winlator.cmod", "com.winlator"),
        )
    }
}
