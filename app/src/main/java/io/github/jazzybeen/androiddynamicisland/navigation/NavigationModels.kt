package io.github.jazzybeen.androiddynamicisland.navigation

enum class Maneuver { LEFT, RIGHT, STRAIGHT, UTURN, TRANSFER, WALK, ARRIVAL, WAIT, ROUTE }

data class NavigationSnapshot(
    val key:String, val instruction:String, val detail:String="", val distance:String="",
    val remaining:String="", val maneuver:Maneuver=Maneuver.ROUTE,
    val lines:List<String> = emptyList(), val updatedAt:Long=0, val stale:Boolean=false, val demo:Boolean=false
){
    val compactLabel:String get()=if(stale)"Обновление…" else remaining.ifBlank{distance.ifBlank{"Маршрут"}}
    val transfer:Boolean get()=maneuver==Maneuver.TRANSFER
}

/** Only documented notification text is used. Missing stops, coordinates and line colours stay unknown. */
object NavigationParser{
    val packages=setOf("ru.yandex.yandexmaps","ru.yandex.yandexnavi")
    private val distance=Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(км|метров|метра|метр|м)(?![\\p{L}\\p{N}_])")
    private val time=Regex("(?i)(?:(\\d+)\\s*ч(?:ас(?:а|ов)?)?\\s*)?(\\d+)\\s*мин(?:ут[ауы]?)?(?![\\p{L}\\p{N}_])")
    private val navigationWords=Regex("(?i)налево|направо|поверн|поворот|прямо|разворот|пересад|переход|останов|станци|маршрут|прибыт|прибыли|метров|двигайтесь|следуйте|съезд|выход")
    fun parse(key:String,title:String,text:String,extraLines:List<String>,ongoing:Boolean,category:String?,now:Long):NavigationSnapshot?{
        val parts=(listOf(title,text)+extraLines).map{it.take(700).map{c->if(c.isISOControl()&&c!='\n')' 'else c}.joinToString("").trim()}.filter{it.isNotBlank()}.distinct()
        val all=parts.joinToString("\n");if(all.isBlank()||all.length>5000)return null
        if(category!="navigation"&&(!ongoing||!navigationWords.containsMatchIn(all)))return null
        val lower=all.lowercase()
        val kind=when{
            lower.contains("пересад")||lower.contains("переход") -> Maneuver.TRANSFER
            lower.contains("разворот")||lower.contains("развернит") -> Maneuver.UTURN
            lower.contains("налево")||lower.contains("левее") -> Maneuver.LEFT
            lower.contains("направо")||lower.contains("правее") -> Maneuver.RIGHT
            lower.contains("прибыли")||lower.contains("вы достигли")||lower.contains("маршрут заверш") -> Maneuver.ARRIVAL
            lower.contains("пешком")||lower.contains("пройдите") -> Maneuver.WALK
            lower.contains("прямо") -> Maneuver.STRAIGHT
            lower.contains("ожидайте") -> Maneuver.WAIT
            else -> Maneuver.ROUTE
        }
        val actionWords=Regex("(?i)налево|направо|поверн|поворот|прямо|разворот|пересад|переход|пройдите|съезд|вы достигли|прибыли")
        val instruction=parts.firstOrNull{actionWords.containsMatchIn(it)}?:parts.firstOrNull{navigationWords.containsMatchIn(it)&&!it.equals("Яндекс Карты",true)}?:parts.first()
        val d=distance.find(all)?.let{it.groupValues[1].replace('.',',')+" "+if(it.groupValues[2].startsWith("к"))"км"else"м"}.orEmpty()
        val t=time.find(all)?.let{(if(it.groupValues[1].isNotBlank())it.groupValues[1]+" ч "else"")+it.groupValues[2]+" мин"}.orEmpty()
        val rows=parts.flatMap{it.lines()}.map{it.trim()}.filter{it.isNotBlank()&&!it.equals("Яндекс Карты",true)}.distinct().take(8)
        return NavigationSnapshot(key,instruction,parts.firstOrNull{it!=instruction}.orEmpty(),d,t,kind,rows,now)
    }
}

/** Suppress distance-only churn; announce a new action or a useful approach threshold. */
class NavigationAlertGate{
    private var action="";private var bucket=-1;private var lastAt=-10000L
    fun shouldAnnounce(value:NavigationSnapshot,now:Long):Boolean{
        val next=value.instruction.lowercase().replace(Regex("\\d+(?:[.,]\\d+)?\\s*(?:км|метров|м|мин)(?![\\p{L}\\p{N}_])"),"#").trim()
        val meters=value.distance.substringBefore(' ').replace(',','.').toDoubleOrNull()?.let{if(value.distance.endsWith("км"))it*1000 else it}
        val nextBucket=when{meters==null->-1;meters<=20->0;meters<=50->1;meters<=100->2;meters<=200->3;else->4}
        val changed=next!=action||(nextBucket>=0&&bucket>=0&&nextBucket<bucket)
        val announce=changed&&now-lastAt>=2500
        if(announce){action=next;bucket=nextBucket;lastAt=now}
        return announce
    }
    fun reset(){action="";bucket=-1;lastAt=-10000}
}
