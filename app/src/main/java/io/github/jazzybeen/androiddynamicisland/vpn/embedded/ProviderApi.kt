package io.github.jazzybeen.androiddynamicisland.vpn.embedded

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Optional adapter for the connection/list + connection/connect provider API. */
object ProviderApi{
    fun load(host:String,key:String,deviceId:String,locationId:String?=null,gatewayName:String?=null):JSONObject{
        val base=URI(if(host.contains("://"))host else "https://$host")
        require(base.scheme=="https"&&base.host!=null&&base.rawUserInfo==null&&base.rawQuery==null&&base.rawFragment==null&&base.path.orEmpty() in setOf("","/")){"Укажи HTTPS-адрес API без пути"}
        require(key.isNotBlank()&&!key.contains('\n')&&!key.contains('\r')){"Нужен ключ доступа"}
        val origin="https://${base.rawAuthority}"
        val locations=JSONArray(request("$origin/api/v1/public/connection/list",key,deviceId))
        val available=(0 until locations.length()).map{locations.getJSONObject(it)}.filter{it.optJSONArray("gateways")?.length()?.let{size->size>0}==true}
        val location=available.firstOrNull{it.optString("id")==locationId}?:available.firstOrNull{it.optBoolean("is_best")}?:available.firstOrNull()?:error("Нет доступных серверов")
        val gateways=location.getJSONArray("gateways")
        val gateway=(0 until gateways.length()).map{gateways.getJSONObject(it)}.firstOrNull{it.optString("name")==gatewayName}?:gateways.getJSONObject(0)
        val result=JSONObject(request("$origin/api/v1/public/connection/connect?gate_id=${encode(location.optString("id"))}&gateway_name=${encode(gateway.getString("name"))}",key,deviceId))
        val configuration=result.get("configuration").let{if(it is JSONObject)it else JSONObject(it.toString())}
        return TunnelConfig.importProfile(configuration.toString()).put("name",location.optString("name","Мой VPN")).put("countryCode",location.optString("shortname")).put("city",gateway.optString("city",location.optJSONObject("meta")?.optString("city").orEmpty())).put("provider",JSONObject().put("host",origin).put("key",key).put("deviceId",deviceId).put("locationId",location.optString("id")).put("gatewayName",gateway.getString("name"))).put("expiration",result.optString("expiration_at"))
    }
    private fun request(url:String,key:String,deviceId:String):String{
        val connection=URI(url).toURL().openConnection() as HttpURLConnection
        try{
            connection.connectTimeout=15000;connection.readTimeout=20000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("X-Access-Key",key);connection.setRequestProperty("X-Hwid",deviceId)
            connection.setRequestProperty("User-Agent","AndroidDynamicIsland/0.2");connection.setRequestProperty("Accept","application/json")
            when(connection.responseCode){200->Unit;401,403->error("Проверь ключ и состояние подписки");429->error("Слишком много запросов. Попробуй позже");else->error("API провайдера недоступен (HTTP ${connection.responseCode})")}
            return connection.inputStream.use{input->
                val output=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                while(true){val count=input.read(chunk);if(count<0)break;require(output.size()+count<=1_048_576){"Ответ API слишком большой"};output.write(chunk,0,count)}
                output.toString("UTF-8")
            }
        }finally{connection.disconnect()}
    }
    private fun encode(value:String)=URLEncoder.encode(value,"UTF-8")
}
