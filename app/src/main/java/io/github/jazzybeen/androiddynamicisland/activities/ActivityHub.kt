package io.github.jazzybeen.androiddynamicisland.activities

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

enum class ActivityKind { NOTIFICATION, MEDIA }
data class ActivityCard(val id:String,val kind:ActivityKind,val app:String,val title:String,val text:String,
    val image:Bitmap?=null,val open:PendingIntent?=null,val media:MediaState?=null)
data class MediaState(val playing:Boolean,val position:Long,val duration:Long,val speed:Float,val updated:Long,
    val canPlay:Boolean,val canPause:Boolean,val canPrevious:Boolean,val canNext:Boolean,val canSeek:Boolean) {
    fun positionAt(now:Long):Long = MediaPosition.at(position,duration,playing,speed,updated,now)
}
object MediaPosition {
    fun at(position:Long,duration:Long,playing:Boolean,speed:Float,updated:Long,now:Long):Long {
        val delta=if(playing&&updated>0&&speed.isFinite())((now-updated).coerceAtLeast(0)*speed).toLong()else 0L
        return (position+delta).coerceAtLeast(0).let{if(duration>0)it.coerceAtMost(duration)else it}
    }
}
class ActivityPreferences(context:Context){
    private val prefs=context.getSharedPreferences("activities",0)
    var notifications:Boolean get()=prefs.getBoolean("notifications",false);set(value){prefs.edit().putBoolean("notifications",value).apply()}
    var allApps:Boolean get()=prefs.getBoolean("allApps",true);set(value){prefs.edit().putBoolean("allApps",value).apply()}
    var apps:Set<String> get()=prefs.getStringSet("apps",emptySet())!!.toSet();set(value){prefs.edit().putStringSet("apps",value).apply()}
    var media:Boolean get()=prefs.getBoolean("media",true);set(value){prefs.edit().putBoolean("media",value).apply()}
    var hideBanners:Boolean get()=prefs.getBoolean("hideBanners",false);set(value){prefs.edit().putBoolean("hideBanners",value).apply()}
    fun accepts(pkg:String)=notifications&&(allApps||pkg in apps)
}

/** Ephemeral, bounded foreground delivery. No notification history is saved. */
object ActivityHub {
    private val main=Handler(Looper.getMainLooper())
    val media=MutableStateFlow<ActivityCard?>(null)
    private val observers=linkedSetOf<(ActivityCard?,Boolean)->Unit>()
    private val notices=ArrayDeque<ActivityCard>()
    var notice:ActivityCard?=null;private set
    private var expires:Runnable?=null
    fun listen(observer:(ActivityCard?,Boolean)->Unit):()->Unit{observers.add(observer);return{observers.remove(observer)}}
    private fun emit(card:ActivityCard?,announce:Boolean){observers.toList().forEach{it(card,announce)}}
    fun setMedia(card:ActivityCard?){main.post{val previous=media.value;media.value=card;if(notice==null)emit(card,card!=null&&(previous==null||previous.id!=card.id||previous.title!=card.title))}}
    fun post(card:ActivityCard){main.post{
        if(notice?.id==card.id){notice=card;emit(card,false);return@post}
        notices.removeAll{it.id==card.id};if(notices.size>=4)notices.removeFirst();notices.addLast(card)
        if(notice==null)next()
    }}
    private fun next(){notice=notices.removeFirstOrNull();emit(notice?:media.value,notice!=null);expires?.let(main::removeCallbacks);expires=null;if(notice!=null)expires=Runnable{next()}.also{main.postDelayed(it,5500)}}
    fun dismiss(){main.post{if(notice!=null)next()}}
    fun removed(id:String){main.post{notices.removeAll{it.id==id};if(notice?.id==id)next()}}
    fun clearNotices(){main.post{notices.clear();notice=null;expires?.let(main::removeCallbacks);expires=null;emit(media.value,false)}}
}
