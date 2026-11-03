package io.github.jazzybeen.androiddynamicisland.system

import android.content.Context
import android.net.*
import android.os.*
import io.github.jazzybeen.androiddynamicisland.core.TrafficIdleGate
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.IslandVpnService

/** Samples own-UID counters off the UI thread. No packets, addresses or browsing data are collected. */
class VpnWatchdog(context:Context):AutoCloseable{
    private val context=context.applicationContext
    private val thread=HandlerThread("Island VPN monitor",Process.THREAD_PRIORITY_BACKGROUND)
    private lateinit var handler:Handler
    private val idle=TrafficIdleGate()
    private var offline=false
    private var startedAt=0L
    @Volatile private var closed=false
    private val tick=object:Runnable{
        override fun run(){
            if(closed)return
            try{sample()}catch(_:RuntimeException){/* Optional counters/network metadata may be unavailable on some firmware. */}
            if(!closed)handler.postDelayed(this,2000)
        }
    }
    fun start(){thread.start();handler=Handler(thread.looper);startedAt=SystemClock.elapsedRealtime();handler.post(tick)}
    private fun sample(){
        val settings=IslandSettings(context);val status=IslandVpnService.status;val now=SystemClock.elapsedRealtime()
        if(!settings.enabled()||!settings.embeddedVpn()){idle.sample(-1,false,now);offline=false;return}
        if(!status.connected){
            idle.sample(-1,false,now);offline=false
            if(!status.busy&&settings.expectedConnected()&&now-startedAt>5000){
                settings.expectedConnected(false)
                if(settings.connectionAlerts())VpnCoordinator.get(context).notifyCompact("VPN отключился",false)
            }
            return
        }
        val connectivity=context.getSystemService(ConnectivityManager::class.java)
        val online=connectivity.allNetworks.any{n->connectivity.getNetworkCapabilities(n)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&!it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}==true}
        if(!online&&!offline&&settings.connectionAlerts())VpnCoordinator.get(context).notifyCompact("Нет сети",true)
        if(online&&offline&&settings.connectionAlerts())VpnCoordinator.get(context).notifyCompact("Сеть восстановлена",true)
        offline=!online
        val rx=TrafficStats.getUidRxBytes(Process.myUid());val tx=TrafficStats.getUidTxBytes(Process.myUid())
        val total=if(rx<0||tx<0)-1 else rx+tx
        if(idle.sample(total,online&&settings.idleAlerts(),now))VpnCoordinator.get(context).notifyCompact("Нет передачи данных",true)
    }
    override fun close(){closed=true;if(::handler.isInitialized)handler.removeCallbacksAndMessages(null);thread.quitSafely()}
}
