package io.github.jazzybeen.androiddynamicisland.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import io.github.jazzybeen.androiddynamicisland.core.AutoConnectPolicy
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.data.VpnProfileStore
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.IslandVpnService

/**
 * Brings the embedded tunnel up with nobody tapping anything: after the phone starts, when the
 * network returns and after an unexpected drop. Switching the VPN off, or connecting another VPN
 * app, is respected until the user connects again or the phone restarts.
 */
object VpnAutoConnect{
    private val policy=AutoConnectPolicy()

    /** The phone just started: the user's earlier "off" no longer applies. */
    @JvmStatic fun onBoot(context:Context){
        val settings=IslandSettings(context)
        if(!settings.autoConnect()||!settings.embeddedVpn())return
        settings.vpnWanted(true)
        // With the island off nothing watches for the network later, so this is the only chance.
        if(!settings.enabled())check(context,online(context))
    }

    /** Runs every couple of seconds while the island is on. */
    @Synchronized fun check(context:Context,online:Boolean){
        val settings=IslandSettings(context);val status=IslandVpnService.status
        if(!settings.autoConnect()||!settings.embeddedVpn()||!settings.vpnWanted())return
        if(status.connected||status.busy||!online)return
        // VpnService.prepare() would tear down another app's tunnel, so it is never called from here;
        // any VPN network while ours is idle belongs to someone else and is left alone.
        if(otherVpn(context)||!VpnProfileStore(context).exists())return
        val now=SystemClock.elapsedRealtime()
        if(!policy.due(now))return
        policy.attempted(now)
        try{IslandVpnService.start(context,automatic=true)}catch(_:RuntimeException){/* Background start refused; the pause above paces the retry. */}
    }

    @Synchronized fun connected(){policy.succeeded()}
    @Synchronized fun networkReturned(){policy.networkReturned(SystemClock.elapsedRealtime())}

    fun online(context:Context):Boolean{
        val manager=context.getSystemService(ConnectivityManager::class.java)?:return false
        @Suppress("DEPRECATION") return manager.allNetworks.any{n->manager.getNetworkCapabilities(n)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&!it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}==true}
    }
    private fun otherVpn(context:Context):Boolean{
        val manager=context.getSystemService(ConnectivityManager::class.java)?:return false
        @Suppress("DEPRECATION") return manager.allNetworks.any{manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}
    }
}
