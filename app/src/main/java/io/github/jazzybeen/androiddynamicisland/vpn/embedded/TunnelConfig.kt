package io.github.jazzybeen.androiddynamicisland.vpn.embedded

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** Import only a proxy outbound. Never execute a provider's control API, file paths, or routing rules. */
object TunnelConfig{
    fun importProfile(text:String):JSONObject{
        val proxy=if(text.trim().startsWith("vless://"))vless(text.trim())else{
            val source=JSONObject(text)
            if(source.has("configuration"))extract(source.getJSONObject("configuration"))else extract(source)
        }
        require(proxy.optString("type") in setOf("vless","vmess","trojan","shadowsocks","hysteria2","tuic","socks","http","anytls")){"Неподдерживаемый тип подключения"}
        require(proxy.optString("server").isNotBlank()&&proxy.optInt("server_port") in 1..65535){"В конфигурации нет адреса сервера"}
        require(!proxy.has("detour")&&!proxy.has("tls_fragment")){"Нужна отдельная конфигурация сервера"}
        rejectFileReferences(proxy)
        proxy.put("tag","proxy")
        return JSONObject().put("name","Мой VPN").put("proxy",proxy)
    }
    private fun extract(source:JSONObject):JSONObject{
        if(source.has("proxy"))return JSONObject(source.getJSONObject("proxy").toString())
        if(source.has("server"))return JSONObject(source.toString())
        val outbounds=source.optJSONArray("outbounds")?:error("В файле нет VPN-сервера")
        val choices=(0 until outbounds.length()).map{outbounds.getJSONObject(it)}.filter{it.optString("type") !in setOf("direct","block","dns","selector","urltest")}
        val selected=choices.firstOrNull{it.optString("tag")=="proxy"}?:choices.singleOrNull()?:error("Импортируй конфигурацию одного сервера")
        return JSONObject(selected.toString())
    }
    fun runtime(profile:JSONObject):String{
        val proxy=JSONObject(profile.getJSONObject("proxy").toString()).put("tag","proxy")
        return JSONObject().put("log",JSONObject().put("disabled",true))
            .put("inbounds",JSONArray().put(JSONObject().put("type","tun").put("tag","tun-in").put("address",JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126")).put("mtu",1400).put("auto_route",true).put("strict_route",true).put("stack","gvisor")))
            .put("outbounds",JSONArray().put(proxy).put(JSONObject().put("type","direct").put("tag","direct")))
            .put("dns",JSONObject().put("servers",JSONArray()
                .put(JSONObject().put("type","https").put("tag","remote").put("server","1.1.1.1").put("tls",JSONObject().put("server_name","cloudflare-dns.com")).put("detour","proxy"))
                .put(JSONObject().put("type","udp").put("tag","bootstrap").put("server","1.1.1.1")))
                .put("final","remote").put("strategy","ipv4_only"))
            .put("route",JSONObject().put("auto_detect_interface",true).put("default_domain_resolver","bootstrap").put("final","proxy").put("rules",JSONArray().put(JSONObject().put("port",53).put("action","hijack-dns"))))
            .toString()
    }
    private fun vless(link:String):JSONObject{
        val uri=URI(link);require(uri.host!=null&&uri.port>0){"Неполная ссылка VLESS"}
        val query=uri.rawQuery.orEmpty().split('&').mapNotNull{val pair=it.split('=',limit=2);if(pair.size==2)decode(pair[0]) to decode(pair[1])else null}.toMap()
        require(query["type"].orEmpty() in setOf("","tcp","ws","grpc","httpupgrade")){"Этот транспорт пока не поддерживается"}
        val proxy=JSONObject().put("type","vless").put("server",uri.host).put("server_port",uri.port).put("uuid",decode(uri.rawUserInfo?:error("Нет идентификатора")))
        query["flow"]?.takeIf{it.isNotBlank()}?.let{proxy.put("flow",it)}
        val security=query["security"].orEmpty()
        require(security in setOf("","none","tls","reality")){"Неизвестный режим TLS"}
        if(security=="tls"||security=="reality"){
            val tls=JSONObject().put("enabled",true).put("server_name",query["sni"]?:uri.host).put("utls",JSONObject().put("enabled",true).put("fingerprint",query["fp"]?:"chrome"))
            if(security=="reality")tls.put("reality",JSONObject().put("enabled",true).put("public_key",query["pbk"]?:error("Нет ключа Reality")).put("short_id",query["sid"].orEmpty()))
            proxy.put("tls",tls)
        }
        when(query["type"]){
            "ws","httpupgrade"->proxy.put("transport",JSONObject().put("type",query["type"]).put("path",query["path"]?:"/").put("headers",JSONObject().put("Host",query["host"]?:uri.host)))
            "grpc"->proxy.put("transport",JSONObject().put("type","grpc").put("service_name",query["serviceName"].orEmpty()))
        }
        return proxy
    }
    private fun decode(value:String)=URLDecoder.decode(value.replace("+","%2B"),"UTF-8")
    private fun rejectFileReferences(value:Any){
        when(value){
            is JSONObject->value.keys().forEach{key->require(!key.endsWith("_path")&&!key.endsWith("_file")&&key!="provider"){"Внешние файлы не поддерживаются"};rejectFileReferences(value.get(key))}
            is JSONArray->(0 until value.length()).forEach{rejectFileReferences(value.get(it))}
        }
    }
}
