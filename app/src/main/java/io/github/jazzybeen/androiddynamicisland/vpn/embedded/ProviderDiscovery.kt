package io.github.jazzybeen.androiddynamicisland.vpn.embedded

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Finds what the user's own VPN app already knows, without reading its private data:
 * the provider API origin (from strings in its installed APK, which Android keeps world-readable)
 * and the exit country of the tunnel it just opened.
 */
object ProviderDiscovery{
    private const val LIST_PATH="/api/v1/public/connection/list"
    private const val CHUNK=4 shl 20
    private const val MAX_ENTRY=64L shl 20

    fun externalVpnActive(context:Context):Boolean{
        val cm=context.getSystemService(ConnectivityManager::class.java)?:return false
        @Suppress("DEPRECATION") return cm.allNetworks.any{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}
    }

    /** Exit country of the current route, as seen by Cloudflare (two-letter code), or "" when offline. */
    fun exitCountry():String{
        for(url in listOf("https://www.cloudflare.com/cdn-cgi/trace","https://1.1.1.1/cdn-cgi/trace")){
            val body=runCatching{get(url,null,null,6000)?.second}.getOrNull()?:continue
            val code=body.lineSequence().firstOrNull{it.startsWith("loc=")}?.removePrefix("loc=")?.trim().orEmpty()
            if(code.matches(Regex("[A-Z]{2}"))&&code!="XX"&&code!="T1")return code
        }
        return ""
    }

    /**
     * APIs an app does not spell out in its APK because it fetches the live domain at run time.
     * Still only candidates: each must answer like the API before it sees the key.
     */
    private val knownApis=mapOf("org.interhive.sota.connect" to listOf("https://sotacdn.com"))

    /** Where to look for the provider API of an installed app: known origins first, then its APK. */
    fun apiCandidates(context:Context,packageName:String):List<String> =
        (knownApis[packageName].orEmpty()+apkCandidates(context,packageName)).distinct()

    /** Candidate API origins embedded in an installed app, most frequent first. */
    fun apkCandidates(context:Context,packageName:String):List<String>{
        val info=runCatching{context.packageManager.getApplicationInfo(packageName,0)}.getOrNull()?:return emptyList()
        val counts=LinkedHashMap<String,Int>()
        for(path in listOfNotNull(info.sourceDir)+(info.splitSourceDirs?.toList()?:emptyList()))runCatching{
            ZipFile(path).use{zip->
                for(entry in zip.entries()){
                    val n=entry.name
                    val useful=n.endsWith(".dex")||n.endsWith("/libapp.so")||n=="resources.arsc"||(n.startsWith("assets/")&&!n.endsWith(".png")&&!n.endsWith(".jpg")&&!n.endsWith(".ttf")&&!n.endsWith(".otf"))
                    if(!useful||entry.isDirectory||entry.size>MAX_ENTRY)continue
                    zip.getInputStream(entry).use{scan(it,counts)}
                }
            }
        }
        return SetupHosts.rank(counts)
    }

    private fun scan(input:InputStream,counts:MutableMap<String,Int>){
        // Chunks overlap by 300 bytes so a URL split across a chunk boundary is still seen whole.
        val buffer=ByteArray(CHUNK+300);var carry=0
        while(true){
            val read=input.readNBytesCompat(buffer,carry,CHUNK);if(read<=0)break
            val length=carry+read
            SetupHosts.collect(String(buffer,0,length,Charsets.ISO_8859_1),counts)
            carry=minOf(300,length);System.arraycopy(buffer,length-carry,buffer,0,carry)
        }
    }
    private fun InputStream.readNBytesCompat(b:ByteArray,off:Int,len:Int):Int{var total=0;while(total<len){val r=read(b,off+total,len-total);if(r<0)break;total+=r};return total}

    /**
     * Returns the first origin that answers like the provider API. Unknown hosts only ever see a
     * key-less request; the access key goes solely to a host that already behaved like the API,
     * and hosts that ask for the key are tried before ones that merely answer in JSON.
     */
    fun findApi(candidates:List<String>,key:String,deviceId:String):String?{
        val unique=candidates.distinct().take(40);if(unique.isEmpty())return null
        val pool=Executors.newFixedThreadPool(8)
        try{
            val scores=pool.invokeAll(unique.map{origin->Callable{probe(origin)}},40,TimeUnit.SECONDS).map{runCatching{it.get()}.getOrDefault(0)}
            var refusal:Exception?=null
            for(origin in unique.indices.filter{scores[it]>0}.sortedByDescending{scores[it]}.map{unique[it]}){
                try{ProviderApi.locations(origin,key,deviceId);return origin}catch(e:Exception){refusal=refusal?:e}
            }
            // A host that looked like the API but refused the key: report why, not "API not found".
            refusal?.let{throw IllegalStateException("Сервер провайдера не принял запрос: ${it.message}",it)}
            return null
        }finally{pool.shutdownNow()}
    }
    private fun probe(origin:String):Int{
        val (code,body)=get(origin+LIST_PATH,null,null,5000)?:return 0
        return SetupHosts.apiScore(code,body)
    }
    private fun get(url:String,key:String?,deviceId:String?,timeout:Int):Pair<Int,String>?{
        val connection=URI(url).toURL().openConnection() as HttpURLConnection
        return try{
            connection.connectTimeout=timeout;connection.readTimeout=timeout;connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept","application/json, text/plain")
            key?.let{connection.setRequestProperty("X-Access-Key",it)};deviceId?.let{connection.setRequestProperty("X-Hwid",it)}
            val code=connection.responseCode
            val stream=if(code>=400)connection.errorStream else connection.inputStream
            val body=stream?.use{s->val out=java.io.ByteArrayOutputStream();val chunk=ByteArray(4096);while(out.size()<65536){val r=s.read(chunk);if(r<0)break;out.write(chunk,0,r)};out.toString("UTF-8")}.orEmpty()
            code to body
        }catch(_:Exception){null}finally{connection.disconnect()}
    }
}
