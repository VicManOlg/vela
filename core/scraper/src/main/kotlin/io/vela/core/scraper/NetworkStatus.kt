package io.vela.core.scraper

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Answers the "Wi-Fi only" question for background downloads. */
@Singleton
class NetworkStatus @Inject constructor(@ApplicationContext private val context: Context) {

    /** True on Wi-Fi, Ethernet or any connection Android reports as not metered. */
    fun isUnmetered(): Boolean = capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

    private fun capabilities(): NetworkCapabilities? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(network)
    }
}
