package io.github.jazzybeen.androiddynamicisland.activities

import android.app.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.IslandAccessibilityService

class NoticeSource(private val context:Context){
    private val fingerprints=linkedMapOf<String,Int>()
    fun posted(sbn:StatusBarNotification){
        val n=sbn.notification;val pkg=sbn.packageName
        if(pkg==context.packageName||!ActivityPreferences(context).accepts(pkg)||!IslandSettings(context).enabled()||IslandAccessibilityService.current()==null)return
        if(!context.getSystemService(PowerManager::class.java).isInteractive||context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)return
        if(n.flags and Notification.FLAG_GROUP_SUMMARY!=0||sbn.isOngoing||n.category in setOf(Notification.CATEGORY_TRANSPORT,Notification.CATEGORY_CALL,Notification.CATEGORY_ALARM,Notification.CATEGORY_SERVICE,Notification.CATEGORY_NAVIGATION))return
        if(n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))return
        if(pkg in setOf("ru.yandex.yandexmaps","ru.yandex.yandexnavi"))return
        val extras=n.extras
        var title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text=(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?:extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val messages=(Notification.Builder.recoverBuilder(context,n).style as? Notification.MessagingStyle)?.messages
        val last=messages?.lastOrNull()
        val person=last?.senderPerson
        if(last!=null){title=person?.name?.toString()?:title;text=last.text?.toString()?:text}
        if(title.isBlank()&&text.isBlank())return
        title=title.take(180);text=text.take(800)
        val fingerprint=(title+"\n"+text).hashCode();if(fingerprints[sbn.key]==fingerprint)return
        fingerprints[sbn.key]=fingerprint;if(fingerprints.size>120)fingerprints.remove(fingerprints.keys.first())
        val bitmap=runCatching{(person?.icon?:n.getLargeIcon())?.loadDrawable(context)?.let{d->Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888).also{d.setBounds(0,0,112,112);d.draw(Canvas(it))}}}.getOrNull()?:appIcon(context,pkg)
        val label=runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg)
        ActivityHub.post(ActivityCard(sbn.key,ActivityKind.NOTIFICATION,label,title,text,bitmap,n.contentIntent))
    }
    fun removed(key:String){fingerprints.remove(key);ActivityHub.removed(key)}
    companion object {
        fun smallBitmap(bitmap:Bitmap?):Bitmap?=bitmap?.let{if(it.width>192||it.height>192){val ratio=192f/maxOf(it.width,it.height);Bitmap.createScaledBitmap(it,maxOf(1,(it.width*ratio).toInt()),maxOf(1,(it.height*ratio).toInt()),true)}else it}
        fun appIcon(context:Context,pkg:String):Bitmap?=runCatching{val d=context.packageManager.getApplicationIcon(pkg);Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).also{d.setBounds(0,0,96,96);d.draw(Canvas(it))}}.getOrNull()
    }
}
