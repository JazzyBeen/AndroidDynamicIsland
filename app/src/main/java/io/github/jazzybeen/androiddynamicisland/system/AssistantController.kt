package io.github.jazzybeen.androiddynamicisland.system

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.voice.VoiceInteractionService

object AssistantController{
    fun isSelected(context:Context):Boolean=VoiceInteractionService.isActiveService(context,ComponentName(context,IslandAssistantService::class.java))
    fun request(activity:Activity){
        val roles=activity.getSystemService(RoleManager::class.java)
        if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)&&!roles.isRoleHeld(RoleManager.ROLE_ASSISTANT))activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),72)
        else activity.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    }
}
