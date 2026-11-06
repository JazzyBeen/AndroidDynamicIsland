package io.github.jazzybeen.androiddynamicisland.vpn.embedded

import java.util.Locale

/** What the user copied in their VPN app: a ready server config, the provider API origin, or an access key. */
sealed class SetupInput{
    data class Config(val text:String):SetupInput()
    data class Host(val origin:String):SetupInput()
    data class Key(val value:String):SetupInput()
    companion object{
        private val key=Regex("^[A-Za-z0-9_\\-.:+/=]{8,512}$")
        private val url=Regex("^https://[^\\s/?#]+(/[^\\s]*)?$",RegexOption.IGNORE_CASE)
        fun classify(raw:String?):SetupInput?{
            val text=raw?.trim()?.takeIf{it.isNotEmpty()&&it.length<=20000}?:return null
            if(text.startsWith("vless://",true))return Config(text)
            if(text.startsWith("{")&&text.endsWith("}"))return Config(text)
            if(url.matches(text))return SetupHosts.origin(text)?.let{Host(it)}
            if(!text.contains("://")&&!text.contains(' ')&&!text.contains('\n')&&key.matches(text))return Key(text)
            return null
        }
    }
}

object SetupHosts{
    private val host=Regex("https://((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24})(?=[/\"'\\s:?#\\x00-\\x1f]|$)",RegexOption.IGNORE_CASE)
    // Libraries and platform services embedded in almost every app; never a VPN provider's API.
    private val noise=listOf("google","gstatic","firebase","crashlytics","android.com","apple.com","icloud","facebook","fbcdn","instagram",
        "twitter","x.com","github","githubusercontent","w3.org","xmlsoap","schemas","apache.org","mozilla","sentry","appsflyer","adjust.com",
        "amplitude","mixpanel","onesignal","yandex","flutter.dev","dart.dev","pub.dev","kotlinlang","jetbrains","gradle","maven","jcenter",
        "cloudflare-dns","dns.google","example.com","example.org","localhost","youtube","wikipedia","telegram","t.me","whatsapp","play.google",
        "microsoft","windows","bing","openssl","bouncycastle","unicode.org","iana.org","ietf.org","creativecommons","opensource.org","sing-box.sagernet")
    fun origin(text:String):String?{
        val match=host.find(text.trim())?:return null
        return "https://"+match.groupValues[1].lowercase(Locale.ROOT)
    }
    private fun isNoise(name:String)=noise.any{if(it.contains('.'))name==it||name.endsWith(".$it")else name.contains(it)}
    /** Adds HTTPS hosts found in one chunk of an app's code or resources; library hosts are skipped. */
    fun collect(blob:CharSequence,counts:MutableMap<String,Int>){
        for(m in host.findAll(blob)){
            val name=m.groupValues[1].lowercase(Locale.ROOT)
            if(!isNoise(name))counts[name]=(counts[name]?:0)+1
        }
    }
    fun rank(counts:Map<String,Int>,limit:Int=40)=counts.entries.sortedByDescending{it.value}.take(limit).map{"https://"+it.key}
    fun candidates(blob:CharSequence,limit:Int=40):List<String>{val counts=LinkedHashMap<String,Int>();collect(blob,counts);return rank(counts,limit)}
    /**
     * How much a key-less reply from the API path looks like the provider API: 2 when it asks for the
     * key, 1 for any other JSON (some providers hide the API behind a JSON 404, but so do plain
     * websites), 0 for HTML, redirects and server errors.
     */
    fun apiScore(code:Int,body:String):Int{
        val text=body.trimStart()
        if(code !in 200..499||code in 300..399||!(text.startsWith("{")||text.startsWith("[")))return 0
        val asksForKey=code in setOf(400,401,403)&&Regex("access|key|token|auth|hwid",RegexOption.IGNORE_CASE).containsMatchIn(text)
        return if(asksForKey||code==200)2 else 1
    }
    fun countryName(code:String):String=
        if(code.length==2)Locale("",code.uppercase(Locale.ROOT)).getDisplayCountry(Locale("ru")).ifBlank{code.uppercase(Locale.ROOT)}else ""
}
