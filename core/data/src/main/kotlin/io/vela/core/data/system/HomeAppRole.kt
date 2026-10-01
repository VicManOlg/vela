package io.vela.core.data.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Vela as the Android home app, the system settings it links to, and its own version. */
@Singleton
class HomeAppRole @Inject constructor(@ApplicationContext private val context: Context) {

    fun isDefaultLauncher(): Boolean = context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

    /** Asks Android to make Vela the home app, or opens the chooser where the role is unavailable. */
    fun requestIntent(): Intent {
        val rm = context.getSystemService(RoleManager::class.java)
        return if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        else Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    fun androidSettingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)

    val appVersion: String
        get() = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "dev" }.getOrDefault("dev")
}
