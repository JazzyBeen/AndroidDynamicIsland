package io.github.jazzybeen.androiddynamicisland.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-local session. Route text and coordinates are never written to disk or logcat. */
object NavigationHub{
    private val main=Handler(Looper.getMainLooper())
    private val mutable=MutableStateFlow<NavigationSnapshot?>(null)
    val state:StateFlow<NavigationSnapshot?> = mutable
    private val sourceMessage=MutableStateFlow("")
    val sourceIssue:StateFlow<String> = sourceMessage
    fun sourceError(){main.post{sourceMessage.value="Не удалось прочитать уведомление Карт. Обнови приложение острова или перезапусти его, когда VPN можно будет отключить."}}
    var route:SharedRoute?=null;private set
    var routeOption=0
    fun plan(){route=null;routeOption=0;routeVersion.value++;update(NavigationSnapshot("planner","Маршрут на карте","Нажми остров и выбери начало и назначение",updatedAt=SystemClock.elapsedRealtime()))}
    private val routeVersion=MutableStateFlow(0)
    val routeChanges:StateFlow<Int> = routeVersion
    val routeRevision:Int get()=routeVersion.value
    private val gate=NavigationAlertGate()
    private val observers=linkedSetOf<(NavigationSnapshot?,Boolean)->Unit>()
    private var removal:Runnable?=null
    fun enabled(context:Context)=context.getSharedPreferences("navigation",0).getBoolean("enabled",false)
    fun enabled(context:Context,value:Boolean){context.getSharedPreferences("navigation",0).edit().putBoolean("enabled",value).apply();if(!value)clear()}
    fun listen(observer:(NavigationSnapshot?,Boolean)->Unit):()->Unit{observers.add(observer);observer(mutable.value,false);return{observers.remove(observer)}}
    fun update(value:NavigationSnapshot){main.post{sourceMessage.value="";removal?.let{main.removeCallbacks(it)};removal=null;val old=mutable.value;mutable.value=value;val announce=old==null||gate.shouldAnnounce(value,SystemClock.elapsedRealtime());if(old==null)gate.shouldAnnounce(value,SystemClock.elapsedRealtime());observers.toList().forEach{it(value,announce)};if(value.maneuver==Maneuver.ARRIVAL){removal=Runnable{if(mutable.value?.key==value.key&&mutable.value?.maneuver==Maneuver.ARRIVAL)clear()}.also{main.postDelayed(it,4500)}}}}
    fun removed(key:String){main.post{if(mutable.value?.key!=key)return@post;removal?.let{main.removeCallbacks(it)};removal=Runnable{if(mutable.value?.key==key)clear()}.also{main.postDelayed(it,1000)}}}
    fun heartbeat(){main.post{val s=mutable.value?:return@post;if(s.key!="shared"&&!s.demo&&!s.stale&&SystemClock.elapsedRealtime()-s.updatedAt>90000){mutable.value=s.copy(stale=true);observers.toList().forEach{it(mutable.value,false)}}}}
    fun clear(){main.post{removal?.let{main.removeCallbacks(it)};removal=null;mutable.value=null;route=null;routeVersion.value++;gate.reset();observers.toList().forEach{it(null,false)}}}
    fun sourceLost(){if(mutable.value?.key!="shared"&&mutable.value?.demo!=true)clear()}
    fun setRoute(value:SharedRoute){route=value;routeOption=0;routeVersion.value++;if(mutable.value==null||mutable.value?.key=="planner")update(NavigationSnapshot("shared",if(value.origin=="shared")"Маршрут по ссылке"else"Общественный транспорт","Предпросмотр · не активное ведение",updatedAt=SystemClock.elapsedRealtime()))else observers.toList().forEach{it(mutable.value,false)}}
    fun clearRoute(){route=null;routeVersion.value++;if(mutable.value?.key=="shared")clear()else observers.toList().forEach{it(mutable.value,false)}}
    fun preview(transfer:Boolean=false){
        update(if(transfer)NavigationSnapshot("demo","Переход на другую линию","Демонстрация · данные не из Карт",remaining="12 мин",maneuver=Maneuver.TRANSFER,lines=listOf("Станция А · линия 1","Переход на линию 2","Станция Б · выход 3"),updatedAt=SystemClock.elapsedRealtime(),demo=true)
        else NavigationSnapshot("demo","Через 20 м поверните налево","Демонстрация · данные не из Карт","20 м","7 мин",Maneuver.LEFT,updatedAt=SystemClock.elapsedRealtime(),demo=true))
    }
}
