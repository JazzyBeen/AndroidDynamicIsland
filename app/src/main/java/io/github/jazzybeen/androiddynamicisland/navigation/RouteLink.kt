package io.github.jazzybeen.androiddynamicisland.navigation

import java.net.URI
import java.net.URLDecoder

data class RoutePoint(val latitude:Double,val longitude:Double)
data class SharedRoute(val points:List<RoutePoint>,val mode:String="pd",val origin:String="shared")

/** Accept explicit Yandex route coordinates, never an arbitrary URL or application Intent. */
object RouteLink{
    private val hosts=setOf("yandex.ru","www.yandex.ru","yandex.com","www.yandex.com","yandex.kz","maps.yandex.ru")
    fun parse(input:String):SharedRoute{
        val link=Regex("(?:https://|yandexmaps://)[^\\s]+",RegexOption.IGNORE_CASE).find(input)?.value?:error("Нужна ссылка Яндекс Карт на маршрут")
        val uri=URI(link);require(uri.host?.lowercase() in hosts&&uri.rawUserInfo==null){"Поддерживаются только ссылки Яндекс Карт"}
        require(uri.scheme=="https"||uri.scheme=="yandexmaps"){"Нужна HTTPS-ссылка Яндекс Карт"}
        val args=uri.rawQuery.orEmpty().split('&').mapNotNull{val p=it.split('=',limit=2);if(p.size==2)URLDecoder.decode(p[0],"UTF-8") to URLDecoder.decode(p[1],"UTF-8") else null}.toMap()
        val points=args["rtext"].orEmpty().split('~').filter{it.isNotBlank()}.map{part->
            val pair=part.split(',');require(pair.size==2){"В ссылке нужны координаты начала и конца"}
            val lat=pair[0].toDouble();val lon=pair[1].toDouble();require(lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0){"Некорректные координаты"};RoutePoint(lat,lon)
        }
        require(points.size in 2..16){"Нужна полная ссылка с началом и концом маршрута (rtext). Короткую ссылку открой в браузере и скопируй полный адрес."}
        val mode=args["rtt"].orEmpty().ifBlank{"pd"};require(mode in setOf("pd","mt","auto")){"Пока поддерживаются пешие, транспортные и автомобильные маршруты"}
        return SharedRoute(points,mode)
    }
}
