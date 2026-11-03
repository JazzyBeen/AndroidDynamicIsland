package io.github.jazzybeen.androiddynamicisland.vpn.embedded

import android.net.*
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import io.nekohasekai.libbox.*
import java.net.InetSocketAddress
import java.net.NetworkInterface as JavaInterface
import java.security.KeyStore
import android.util.Base64

/** Android routing adapter for the pinned upstream libbox interface. No screen or microphone access. */
class TunnelPlatform(private val service:IslandVpnService):PlatformInterface{
    private val connectivity=service.getSystemService(ConnectivityManager::class.java)
    private var descriptor:ParcelFileDescriptor?=null
    @Volatile private var listener:InterfaceUpdateListener?=null
    private var registered=false
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:Network){updateNetwork()}
        override fun onLost(network:Network){updateNetwork()}
        override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities){updateNetwork()}
        override fun onLinkPropertiesChanged(network:Network,properties:LinkProperties){updateNetwork()}
    }
    override fun openTun(options:TunOptions):Int{
        check(VpnService.prepare(service)==null){"VPN permission required"}
        val builder=service.Builder().setSession("Dynamic Island VPN").setMtu(options.mtu).setMetered(false).setBlocking(false)
        val ipv4=options.inet4Address;while(ipv4.hasNext()){val p=ipv4.next();builder.addAddress(p.address(),p.prefix())}
        val ipv6=options.inet6Address;while(ipv6.hasNext()){val p=ipv6.next();builder.addAddress(p.address(),p.prefix())}
        builder.addDnsServer(options.dnsServerAddress.value)
        val routes4=options.inet4RouteRange;while(routes4.hasNext()){val p=routes4.next();builder.addRoute(p.address(),p.prefix())}
        val routes6=options.inet6RouteRange;while(routes6.hasNext()){val p=routes6.next();builder.addRoute(p.address(),p.prefix())}
        descriptor=builder.establish()?:error("Cannot establish VPN interface")
        return descriptor!!.fd
    }
    override fun usePlatformAutoDetectInterfaceControl()=true
    override fun autoDetectInterfaceControl(fd:Int){check(service.protect(fd)){"Cannot protect VPN socket"}}
    override fun startDefaultInterfaceMonitor(value:InterfaceUpdateListener){
        listener=value
        connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),callback)
        registered=true;updateNetwork()
    }
    override fun closeDefaultInterfaceMonitor(value:InterfaceUpdateListener){listener=null;if(registered){registered=false;runCatching{connectivity.unregisterNetworkCallback(callback)}}}
    private fun updateNetwork(){
        val target=listener?:return
        val network=connectivity.allNetworks.filter{n->connectivity.getNetworkCapabilities(n)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&!it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}==true}
            .maxByOrNull{n->val c=connectivity.getNetworkCapabilities(n)!!;(if(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))10 else 0)+(if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))2 else 0)}
        val properties=network?.let{connectivity.getLinkProperties(it)};val name=properties?.interfaceName
        val index=name?.let{runCatching{JavaInterface.getByName(it)?.index}.getOrNull()}
        service.setUnderlyingNetworks(network?.let{arrayOf(it)})
        target.updateDefaultInterface(name.orEmpty(),index?:-1,network?.let{connectivity.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)==false}?:false,false)
    }
    override fun getInterfaces():NetworkInterfaceIterator{
        val interfaces=connectivity.allNetworks.mapNotNull{network->
            val caps=connectivity.getNetworkCapabilities(network)?:return@mapNotNull null
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return@mapNotNull null
            val properties=connectivity.getLinkProperties(network)?:return@mapNotNull null
            val native=properties.interfaceName?.let{runCatching{JavaInterface.getByName(it)}.getOrNull()}?:return@mapNotNull null
            NetworkInterface().apply{
                name=native.name;index=native.index;mtu=runCatching{native.mtu}.getOrDefault(1500)
                flags=OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                type=when{caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)->Libbox.InterfaceTypeWIFI;caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)->Libbox.InterfaceTypeCellular;else->Libbox.InterfaceTypeOther}
                metered=!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                addresses=Strings(properties.linkAddresses.map{it.toString()})
                dnsServer=Strings(properties.dnsServers.mapNotNull{it.hostAddress})
            }
        }.iterator()
        return object:NetworkInterfaceIterator{override fun hasNext()=interfaces.hasNext();override fun next()=interfaces.next()}
    }
    override fun useProcFS()=false
    override fun findConnectionOwner(protocol:Int,source:String,sourcePort:Int,destination:String,destinationPort:Int)=connectivity.getConnectionOwnerUid(protocol,InetSocketAddress(source,sourcePort),InetSocketAddress(destination,destinationPort))
    override fun packageNameByUid(uid:Int)=service.packageManager.getPackagesForUid(uid)?.firstOrNull().orEmpty()
    override fun uidByPackageName(name:String)=service.packageManager.getPackageUid(name,0)
    override fun underNetworkExtension()=false
    override fun includeAllNetworks()=false
    override fun readWIFIState():WIFIState?=null
    override fun localDNSTransport():LocalDNSTransport?=null
    override fun clearDNSCache(){}
    override fun writeLog(message:String){} // Core messages may contain server identifiers. Do not send them to logcat.
    override fun sendNotification(notification:Notification){}
    override fun systemCertificates():StringIterator{
        val store=KeyStore.getInstance("AndroidCAStore").apply{load(null)}
        return Strings(store.aliases().toList().map{store.getCertificate(it)}.map{"-----BEGIN CERTIFICATE-----\n${Base64.encodeToString(it.encoded,Base64.NO_WRAP)}\n-----END CERTIFICATE-----"})
    }
    fun close(){listener?.let{closeDefaultInterfaceMonitor(it)};runCatching{descriptor?.close()};descriptor=null}
    private class Strings(private val values:List<String>):StringIterator{
        private var index=0
        override fun len()=values.size-index
        override fun hasNext()=index<values.size
        override fun next()=values[index++]
    }
}
