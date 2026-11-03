package io.github.jazzybeen.androiddynamicisland.vpn.embedded

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.*
import android.os.*
import io.github.jazzybeen.androiddynamicisland.R
import io.github.jazzybeen.androiddynamicisland.data.VpnProfileStore
import io.github.jazzybeen.androiddynamicisland.ui.MainActivity
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator
import io.nekohasekai.libbox.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors

data class TunnelStatus(val connected:Boolean=false,val busy:Boolean=false,val message:String="Готов к подключению")

/** Owns the tunnel, not another VPN app. All native lifecycle work is serialized away from the UI. */
class IslandVpnService:VpnService(){
    private val worker=Executors.newSingleThreadExecutor()
    private var probeFailure=""
    private var engine:BoxService?=null
    private var platform:TunnelPlatform?=null
    @Volatile private var stopping=false
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==STOP){stopping=true;setStatus(TunnelStatus(busy=true,message="Отключение…"));worker.execute{closeTunnel();setStatus(TunnelStatus(message="Отключено"));stopSelf()};return START_NOT_STICKY}
        if(intent?.action!=START){stopSelf();return START_NOT_STICKY}
        if(engine!=null||status.busy)return START_NOT_STICKY
        stopping=false;setStatus(TunnelStatus(busy=true,message="Подключение…"))
        val notice=notification("Подключение…")
        if(Build.VERSION.SDK_INT>=34){
            try{startForeground(21,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)}
            catch(_:RuntimeException){startForeground(21,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)}
        }else startForeground(21,notice)
        worker.execute{
            var stage="профиль"
            try{
                check(prepare(this)==null){"VPN permission required"}
                var profile=VpnProfileStore(this).read()?:error("No profile")
                val provider=profile.optJSONObject("provider")
                if(provider!=null&&System.currentTimeMillis()-profile.optLong("fetchedAt")>6*60*60*1000){
                    profile=ProviderApi.load(provider.getString("host"),provider.getString("key"),provider.getString("deviceId"),provider.optString("locationId"),provider.optString("gatewayName")).put("fetchedAt",System.currentTimeMillis())
                    VpnProfileStore(this).write(profile)
                }
                if(stopping)return@execute
                stage="запуск ядра"
                val directory=File(noBackupFilesDir,"vpn-core").apply{mkdirs()}
                Libbox.setup(SetupOptions().apply{basePath=directory.absolutePath;workingPath=directory.absolutePath;tempPath=cacheDir.absolutePath;fixAndroidStack=true})
                val adapter=TunnelPlatform(this);platform=adapter
                engine=Libbox.newService(TunnelConfig.runtime(profile),adapter)
                engine!!.start()
                if(stopping)return@execute
                setStatus(TunnelStatus(busy=true,message="Проверка подключения…"))
                stage="проверка сети"
                check(probeTunnel()){ "Tunnel connectivity check failed" }
                if(!stopping){setStatus(TunnelStatus(true,false,"Подключено"));getSystemService(NotificationManager::class.java).notify(21,notification("VPN подключён"))}
            }catch(error:Exception){
                closeTunnel()
                android.util.Log.i("IslandVpn","Startup failed; phase=$stage; category=${error.javaClass.simpleName}; probe=$probeFailure")
                if(!stopping){setStatus(TunnelStatus(message=if(stage=="проверка сети")"Не удалось проверить интернет через VPN"else"Не удалось запустить VPN. Проверь профиль"));stopSelf()}
            }
        }
        return START_NOT_STICKY
    }
    private fun probeTunnel():Boolean{
        val manager=getSystemService(ConnectivityManager::class.java)
        var network:Network?=null
        for(attempt in 0..9){
            network=manager.allNetworks.firstOrNull{manager.getNetworkCapabilities(it)?.let{caps->caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&caps.ownerUid==android.os.Process.myUid()}==true}
            if(network!=null)break
            if(stopping)return false
            Thread.sleep(150)
        }
        val tunnel=network?:run{probeFailure="owned-network-not-found";return false}
        for(endpoint in listOf("https://cp.cloudflare.com/generate_204","https://www.gstatic.com/generate_204")){
            if(stopping)return false
            try{
                val connection=tunnel.openConnection(URI(endpoint).toURL()) as HttpURLConnection
                try{connection.connectTimeout=7000;connection.readTimeout=7000;connection.instanceFollowRedirects=false;val code=connection.responseCode;if(code==204)return true;probeFailure="http-$code"}finally{connection.disconnect()}
            }catch(error:Exception){
                var root:Throwable=error;while(root.cause!=null&&root.cause!==root)root=root.cause!!
                probeFailure=error.javaClass.simpleName+"/"+root.javaClass.simpleName+(if(root is android.system.ErrnoException)"/errno-${root.errno}"else"")
                android.util.Log.i("IslandVpn","Probe failure: $probeFailure; origin="+root.stackTrace.take(4).joinToString(" > "){it.className+"."+it.methodName})
            }
        }
        return false
    }
    private fun notification(text:String):android.app.Notification{
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("island_vpn","VPN-подключение",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);setShowBadge(false)})
        val open=PendingIntent.getActivity(this,21,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,22,Intent(this,IslandVpnService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return android.app.Notification.Builder(this,"island_vpn").setSmallIcon(R.drawable.ic_island).setContentTitle("Dynamic Island VPN").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(android.app.Notification.Action.Builder(null,"Отключить",stop).build()).build()
    }
    private fun closeTunnel(){val active=engine;engine=null;runCatching{active?.close()};platform?.close();platform=null}
    private fun setStatus(value:TunnelStatus){status=value;VpnCoordinator.get(this).embeddedChanged(value)}
    override fun onRevoke(){stopping=true;worker.execute{closeTunnel();setStatus(TunnelStatus(message="VPN отключён системой"));stopSelf()}}
    override fun onDestroy(){stopping=true;worker.execute{closeTunnel();if(status.connected||status.busy)setStatus(TunnelStatus(message="Отключено"))};worker.shutdown();super.onDestroy()}
    companion object{
        private const val START="island.vpn.START"
        private const val STOP="island.vpn.STOP"
        @Volatile var status=TunnelStatus();private set
        fun start(context:Context){context.startForegroundService(Intent(context,IslandVpnService::class.java).setAction(START))}
        fun stop(context:Context){context.startService(Intent(context,IslandVpnService::class.java).setAction(STOP))}
    }
}
