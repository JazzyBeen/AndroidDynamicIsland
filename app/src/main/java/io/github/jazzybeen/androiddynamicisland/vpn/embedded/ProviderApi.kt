package io.github.jazzybeen.androiddynamicisland.vpn.embedded

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Optional adapter for the connection/list + connection/connect provider API. */
object ProviderApi{
    /**
     * [countryCode] picks the location the user chose in their own VPN app; [hint] (that app's
     * notification text) narrows it to the matching gateway when it names a city or server.
     */
    fun load(host:String,key:String,deviceId:String,locationId:String?=null,gatewayName:String?=null,countryCode:String?=null,hint:String=""):JSONObject{
        val origin=origin(host)
        val locations=locations(origin,key,deviceId)
        val available=(0 until locations.length()).map{locations.getJSONObject(it)}.filter{it.optJSONArray("gateways")?.length()?.let{size->size>0}==true}
        val location=available.firstOrNull{it.optString("id")==locationId}
            ?:countryCode?.takeIf{it.isNotBlank()}?.let{code->available.firstOrNull{it.optString("shortname").equals(code,true)}}
            ?:available.firstOrNull{it.optBoolean("is_best")}?:available.firstOrNull()?:error("Нет доступных серверов")
        val gateways=location.getJSONArray("gateways")
        val all=(0 until gateways.length()).map{gateways.getJSONObject(it)}
        val lowerHint=hint.lowercase()
        val gateway=all.firstOrNull{it.optString("name")==gatewayName}
            ?:all.firstOrNull{g->lowerHint.isNotBlank()&&listOf(g.optString("name"),g.optString("city")).any{it.length>=3&&lowerHint.contains(it.lowercase())}}
            ?:all.first()
        val result=JSONObject(request("$origin/api/v1/public/connection/connect?gate_id=${encode(location.optString("id"))}&gateway_name=${encode(gateway.getString("name"))}",key,deviceId))
        val configuration=result.get("configuration").let{if(it is JSONObject)it else JSONObject(it.toString())}
        return TunnelConfig.importProfile(configuration.toString()).put("name",location.optString("name","Мой VPN")).put("countryCode",location.optString("shortname")).put("city",gateway.optString("city",location.optJSONObject("meta")?.optString("city").orEmpty())).put("provider",JSONObject().put("host",origin).put("key",key).put("deviceId",deviceId).put("locationId",location.optString("id")).put("gatewayName",gateway.getString("name"))).put("expiration",result.optString("expiration_at"))
    }
    fun origin(host:String):String{
        val base=URI(if(host.contains("://"))host.trim() else "https://${host.trim()}")
        require(base.scheme=="https"&&base.host!=null&&base.rawUserInfo==null&&base.rawQuery==null&&base.rawFragment==null&&base.path.orEmpty() in setOf("","/")){"Укажи HTTPS-адрес API без пути"}
        return "https://${base.rawAuthority}"
    }
    fun locations(host:String,key:String,deviceId:String):JSONArray{
        require(key.isNotBlank()&&!key.contains('\n')&&!key.contains('\r')){"Нужен ключ доступа"}
        return JSONArray(request("${origin(host)}/api/v1/public/connection/list",key,deviceId))
    }
    private fun request(url:String,key:String,deviceId:String):String{
        val connection=URI(url).toURL().openConnection() as HttpURLConnection
        try{
            connection.connectTimeout=15000;connection.readTimeout=20000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("X-Access-Key",key);connection.setRequestProperty("X-Hwid",deviceId)
            connection.setRequestProperty("User-Agent","AndroidDynamicIsland/0.2");connection.setRequestProperty("Accept","application/json")
            val code=connection.responseCode
            if(code!=200){
                val reason=reason(connection)
                when(code){401,403->error("Проверь ключ и состояние подписки${reason?.let{": $it"}.orEmpty()}");402->error("Подписка закончилась");429->error("Слишком много запросов. Попробуй позже");else->error("API провайдера ответил ошибкой (HTTP $code)${reason?.let{": $it"}.orEmpty()}")}
            }
            return connection.inputStream.use{input->
                val output=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                while(true){val count=input.read(chunk);if(count<0)break;require(output.size()+count<=1_048_576){"Ответ API слишком большой"};output.write(chunk,0,count)}
                output.toString("UTF-8")
            }
        }finally{connection.disconnect()}
    }
    /** The provider's own short explanation ("Access key not found", …), never the request itself. */
    private fun reason(connection:HttpURLConnection):String?=runCatching{
        val body=connection.errorStream?.use{s->val out=java.io.ByteArrayOutputStream();val chunk=ByteArray(2048);while(out.size()<4096){val r=s.read(chunk);if(r<0)break;out.write(chunk,0,r)};out.toString("UTF-8")}?:return null
        val json=JSONObject(body)
        listOf(json.optJSONObject("response")?.optString("error"),json.optString("error"),json.optString("detail"),json.optString("message")).firstOrNull{!it.isNullOrBlank()}?.take(120)
    }.getOrNull()
    private fun encode(value:String)=URLEncoder.encode(value,"UTF-8")
}
