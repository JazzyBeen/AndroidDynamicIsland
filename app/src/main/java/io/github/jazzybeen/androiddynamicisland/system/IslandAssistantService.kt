package io.github.jazzybeen.androiddynamicisland.system

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.app.KeyguardManager
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator

/** System assistant entry point for the physical long-press. No voice recording or assist-data collection. */
class IslandAssistantService:VoiceInteractionService(){
    override fun onReady(){super.onReady();setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)}
}

class IslandAssistantSessionService:VoiceInteractionSessionService(){
    override fun onNewSession(args:Bundle?):VoiceInteractionSession=IslandAssistantSession(this)
}

private class IslandAssistantSession(context:android.content.Context):VoiceInteractionSession(context){
    override fun onCreate(){super.onCreate();setUiEnabled(false);setDisabledShowContext(SHOW_WITH_ASSIST or SHOW_WITH_SCREENSHOT)}
    override fun onShow(args:Bundle?,showFlags:Int){
        super.onShow(args,showFlags)
        val service=IslandAccessibilityService.current()
        if(!IslandSettings(context).enabled())service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        else if(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)service?.showIsland("VPN","Сначала разблокируй телефон",true,false)
        else{IslandService.ensureStarted(context);VpnCoordinator.get(context).toggle()}
        Handler(Looper.getMainLooper()).postDelayed({finish()},250)
    }
}
