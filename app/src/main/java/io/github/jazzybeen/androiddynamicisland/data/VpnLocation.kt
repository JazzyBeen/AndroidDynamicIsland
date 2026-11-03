package io.github.jazzybeen.androiddynamicisland.data

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Display metadata comes from the selected server, never from the phone's location. */
object VpnLocation{
    fun remember(context:Context,profile:JSONObject){
        val gateway=profile.optJSONObject("provider")?.optString("gatewayName").orEmpty()
        val inferred=gateway.take(2).uppercase(Locale.ROOT).takeIf{Regex("^[A-Z]{2}-[A-Z]{3}-.*$").matches(gateway.uppercase(Locale.ROOT))}.orEmpty()
        val code=profile.optString("countryCode").uppercase(Locale.ROOT).ifBlank{inferred}
        val city=profile.optString("city").ifBlank{cityFromGateway(gateway)}
        IslandSettings(context).location(code,profile.optString("name","VPN"),city.ifBlank{if(gateway.isBlank())"Мой сервер"else gateway})
    }
    fun cityFromGateway(gateway:String):String{
        val code=gateway.lowercase(Locale.ROOT).split('-').getOrNull(1).orEmpty()
        return mapOf("ams" to "Амстердам","fra" to "Франкфурт","ber" to "Берлин","hel" to "Хельсинки","sto" to "Стокгольм","lon" to "Лондон","par" to "Париж","nyc" to "Нью-Йорк","lax" to "Лос-Анджелес","sin" to "Сингапур","tok" to "Токио","waw" to "Варшава","prg" to "Прага","vie" to "Вена","zrh" to "Цюрих","mad" to "Мадрид","ist" to "Стамбул","rga" to "Рига","tll" to "Таллин")[code].orEmpty()
    }
    fun emoji(code:String):String{
        val upper=code.uppercase(Locale.ROOT)
        if(!upper.matches(Regex("[A-Z]{2}")))return "🌐"
        return upper.map{String(Character.toChars(0x1F1E6+it.code-'A'.code))}.joinToString("")
    }
}
