package io.github.illiapohukai.focusmode.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.domain.platform.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNetworkStatus @Inject constructor(@ApplicationContext context: Context) : NetworkStatus {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(isOnline())
    override val online: StateFlow<Boolean> = state.asStateFlow()

    init {
        runCatching {
            manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { state.value = isOnline() }
                override fun onLost(network: Network) { state.value = isOnline() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
            })
        }
    }

    private fun isOnline(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkStatusModule {
    @Binds abstract fun status(impl: AndroidNetworkStatus): NetworkStatus
}
