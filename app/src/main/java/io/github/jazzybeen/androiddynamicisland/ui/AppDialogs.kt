package io.github.jazzybeen.androiddynamicisland.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp

object AppDialogs{
    fun show(activity:Activity,title:String,message:String,positive:String,onPositive:()->Unit,negative:String="Позже",onNegative:(()->Unit)?=null){
        val dialog=ComponentDialog(activity);var handled=false
        dialog.setContentView(ComposeView(dialog.context).apply{setContent{AppTheme{
            val look=LocalLook.current
            Column(Modifier.fillMaxWidth().padding(20.dp).background(if(look.material)MaterialTheme.colorScheme.surfaceContainerHigh else look.card,RoundedCornerShape(28.dp)).padding(24.dp)){
                Label(title,24,bold=!look.material);Spacer(Modifier.height(14.dp))
                Box(Modifier.weight(1f,false).verticalScroll(rememberScrollState())){Label(message,14,if(look.material)look.secondary else Color(0xFFB0B0B7))}
                Spacer(Modifier.height(24.dp))
                if(look.material)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
                    TextButton({handled=true;dialog.dismiss();onNegative?.invoke()}){Label(negative,14,look.accent,true)}
                    Spacer(Modifier.width(8.dp));Button({handled=true;dialog.dismiss();onPositive()}){Label(positive,14,look.onAccent,true)}
                }else{
                    PrimaryButton(positive){handled=true;dialog.dismiss();onPositive()}
                    Box(Modifier.fillMaxWidth().height(44.dp).clickable{handled=true;dialog.dismiss();onNegative?.invoke()},contentAlignment=Alignment.Center){Label(negative,14,look.secondary)}
                }
            }
        }}})
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT));dialog.window?.setDimAmount(.75f)
        dialog.setOnDismissListener{if(!handled)onNegative?.invoke()};dialog.show();dialog.window?.setLayout(-1,-2)
    }
}
