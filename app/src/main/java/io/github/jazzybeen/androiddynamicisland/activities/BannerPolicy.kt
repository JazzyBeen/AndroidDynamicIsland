package io.github.jazzybeen.androiddynamicisland.activities

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.service.notification.*
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.IslandAccessibilityService

/** A separate, reversible rule. Never overwrites the user's global Do Not Disturb policy. */
object BannerPolicy {
    private fun uri(context:Context)=Uri.parse("condition://${context.packageName}/island-banners")
    fun update(context:Context,listenerConnected:Boolean){
        val manager=context.getSystemService(NotificationManager::class.java)
        val store=context.getSharedPreferences("activities",0);val prefs=ActivityPreferences(context)
        if(!manager.isNotificationPolicyAccessGranted)return
        try{
            var id=store.getString("bannerRule",null)
            if(id!=null&&manager.getAutomaticZenRule(id)==null){id=null;store.edit().remove("bannerRule").apply()}
            val wanted=prefs.hideBanners&&prefs.notifications&&prefs.allApps
            if(!wanted){if(id!=null){manager.removeAutomaticZenRule(id);store.edit().remove("bannerRule").apply()};return}
            if(id==null){
                val policy=ZenPolicy.Builder().allowAllSounds().showAllVisualEffects().showPeeking(false).build()
                val owner=ComponentName(context,BannerConditionService::class.java)
                val rule=AutomaticZenRule("Dynamic Island · без баннеров",owner,null,uri(context),policy,NotificationManager.INTERRUPTION_FILTER_PRIORITY,true)
                id=manager.addAutomaticZenRule(rule);store.edit().putString("bannerRule",id).apply()
            }
            val ready=listenerConnected&&IslandSettings(context).enabled()&&IslandAccessibilityService.current()!=null&&context.getSystemService(PowerManager::class.java).isInteractive&&!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val state=if(ready)Condition.STATE_TRUE else Condition.STATE_FALSE
            if(Build.VERSION.SDK_INT<35||manager.getAutomaticZenRuleState(id)!=state)manager.setAutomaticZenRuleState(id,Condition(uri(context),"Уведомления через остров",state))
        }catch(_:SecurityException){}catch(_:IllegalArgumentException){}
    }
}
class BannerConditionService:ConditionProviderService(){
    override fun onConnected(){BannerPolicy.update(this,io.github.jazzybeen.androiddynamicisland.system.VpnNotificationListener.connected())}
    override fun onSubscribe(conditionId:Uri){BannerPolicy.update(this,io.github.jazzybeen.androiddynamicisland.system.VpnNotificationListener.connected())}
    override fun onUnsubscribe(conditionId:Uri){}
}
