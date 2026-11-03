package io.github.jazzybeen.androiddynamicisland.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.jazzybeen.androiddynamicisland.core.VpnOperation
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.*
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.IslandVpnService
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.TunnelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VpnStatus(val connected:Boolean=false,val busy:Boolean=false,val message:String="Готов к подключению")

class VpnCoordinator private constructor(private val context:Context){
    private val main=Handler(Looper.getMainLooper())
    private val mutableState=MutableStateFlow(VpnStatus())
    val state:StateFlow<VpnStatus> = mutableState
    private var operation:VpnOperation?=null
    private var lastCommand=-5000L
    private val poll=Runnable{observe()}
    fun message()=mutableState.value.message
    fun busy()=operation!=null||IslandVpnService.status.busy
    fun anyVpn():Boolean{
        val manager=context.getSystemService(ConnectivityManager::class.java)?:return false
        return manager.allNetworks.any{manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}
    }
    fun clientChanged(){main.removeCallbacks(poll);operation=null;mutableState.value=VpnStatus(false,false,"Подключение настроено");refresh()}
    fun refresh(){main.post{if(IslandSettings(context).embeddedVpn()){val s=IslandVpnService.status;mutableState.value=VpnStatus(s.connected,s.busy,s.message)}else if(operation!=null)observe()else{val connected=anyVpn();val old=mutableState.value;mutableState.value=old.copy(connected=connected,message=if(old.connected!=connected)if(connected)"Подключение активно"else"Готов к подключению" else old.message)}}}
    fun toggle(){main.post{begin()}}
    private fun begin(){
        if(busy()||SystemClock.elapsedRealtime()-lastCommand<1600)return
        lastCommand=SystemClock.elapsedRealtime()
        if(!IslandSettings(context).enabled()){announce("Остров выключен — включи его в приложении",true,false);return}
        if(IslandSettings(context).embeddedVpn()){
            if(!io.github.jazzybeen.androiddynamicisland.data.VpnProfileStore(context).exists()){announce("Добавь VPN-профиль в приложении",true,false);return}
            if(android.net.VpnService.prepare(context)!=null){announce("Один раз разреши VPN в приложении острова",true,false);return}
            try{if(IslandVpnService.status.connected)IslandVpnService.stop(context)else IslandVpnService.start(context)}catch(_:RuntimeException){announce("Не удалось запустить VPN-службу",true,false)}
            return
        }
        if(!VpnClientBridge.installed(context)){announce("Выбери VPN-приложение",true,false);return}
        val connected=anyVpn()
        operation=VpnOperation(!connected,SystemClock.elapsedRealtime())
        announce(if(connected)"Отключение…"else"Подключение…",false,connected)
        try{
            if(connected)VpnClientBridge.disconnect(context)else VpnClientBridge.connect(context)
            main.postDelayed(poll,350)
        }catch(error:RuntimeException){operation=null;announce(error.message?:"Не удалось отправить команду",true,connected)}
    }
    fun observationChanged(){refresh()}
    private fun observe(){
        main.removeCallbacks(poll)
        val pending=operation?:return
        val connected=anyVpn()
        val clientEvidence=VpnNotificationListener.clientOngoing()||IslandAccessibilityService.current()?.clientUiConnected()==true
        when(pending.observe(connected,clientEvidence,SystemClock.elapsedRealtime())){
            VpnOperation.Result.WAITING->main.postDelayed(poll,350)
            VpnOperation.Result.CONNECTED->{operation=null;IslandAccessibilityService.current()?.cancelClientAction();announce("Подключено",true,true)}
            VpnOperation.Result.DISCONNECTED->{operation=null;IslandAccessibilityService.current()?.cancelClientAction();announce("Отключено",true,false)}
            else->{operation=null;IslandAccessibilityService.current()?.cancelClientAction();announce("Не удалось подтвердить — открой VPN-приложение",true,connected)}
        }
    }
    fun embeddedChanged(value:TunnelStatus){main.post{if(IslandSettings(context).embeddedVpn())announce(value.message,!value.busy,value.connected)}}
    fun preview(){main.post{val connected=if(IslandSettings(context).embeddedVpn())IslandVpnService.status.connected else anyVpn();announce(if(connected)"Подключено · предпросмотр"else"Готов к работе · предпросмотр",true,connected)}}
    private fun announce(message:String,complete:Boolean,connected:Boolean){
        mutableState.value=VpnStatus(connected,!complete,message)
        IslandAccessibilityService.current()?.showIsland("VPN",message,complete,connected)
    }
    companion object{
        @Volatile private var instance:VpnCoordinator?=null
        @JvmStatic fun get(context:Context):VpnCoordinator=instance?:synchronized(this){instance?:VpnCoordinator(context.applicationContext).also{instance=it}}
    }
}
