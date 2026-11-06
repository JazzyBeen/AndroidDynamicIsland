package io.github.jazzybeen.androiddynamicisland.navigation

object TransportLabels {
    fun name(types:List<String>,number:String):String {
        val type=when {
            "minibus" in types->"Маршрутка"
            "bus" in types->"Автобус"
            "trolleybus" in types->"Троллейбус"
            "tramway" in types||"tram" in types->"Трамвай"
            "underground" in types||"metro" in types->"Метро"
            "suburban" in types->"Электричка"
            "railway" in types->"Поезд"
            "water" in types->"Паром"
            else->"Транспорт"
        }
        return "$type $number".trim()
    }
}
