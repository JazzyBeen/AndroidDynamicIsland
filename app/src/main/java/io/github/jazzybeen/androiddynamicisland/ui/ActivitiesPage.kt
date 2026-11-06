package io.github.jazzybeen.androiddynamicisland.ui

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jazzybeen.androiddynamicisland.activities.*
import io.github.jazzybeen.androiddynamicisland.system.VpnNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.function.Consumer

@Composable fun ActivitiesPage(action:Consumer<String>,revision:Int){
    val context=LocalContext.current;val prefs=remember{ActivityPreferences(context)}
    var notifications by remember(revision){mutableStateOf(prefs.notifications)}
    var all by remember(revision){mutableStateOf(prefs.allApps)}
    var media by remember(revision){mutableStateOf(prefs.media)}
    var banners by remember(revision){mutableStateOf(prefs.hideBanners)}
    var picker by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf(prefs.apps)}
    val playing by ActivityHub.media.collectAsState()
    fun changed(){VpnNotificationListener.refreshActivities()}
    PageHeader("События","Уведомления и музыка рядом")
    Group{SettingRow("Уведомления в острове","Локально, без истории сообщений"){
        AppSwitch(notifications){notifications=it;prefs.notifications=it;if(!it)ActivityHub.clearNotices();changed();if(it&&!Permissions.listener(context))action.accept("listener")}}
        SettingRow("Все приложения",null){AppSwitch(all){all=it;prefs.allApps=it;if(!it){banners=false;prefs.hideBanners=false};changed()}}
        if(!all)ActionRow("Выбрать приложения","${selected.size} выбрано"){picker=true}
        ActionRow("Доступ к уведомлениям",if(Permissions.listener(context))"Разрешён"else"Нужен доступ"){action.accept("listener")}
    }
    Caption("Аватарка, отправитель и текст появятся в острове. Кнопка «Открыть» ведёт в исходное приложение. Содержимое скрыто при блокировке; коды и защищённые уведомления Android может не передавать.")
    Group{SettingRow("Без баннеров Android","Для режима «Все приложения»"){
        AppSwitch(banners,enabled=notifications&&all){banners=it;prefs.hideBanners=it;changed();if(it&&!context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted)action.accept("bannerPermission")}
    }}
    Caption("Отключаются только всплывающие баннеры, пока остров работает на разблокированном экране. Звук, звонки и шторка сохраняются. Нужен системный доступ к режимам. Для отдельных приложений отключи баннеры в настройках их каналов уведомлений.")
    Section("МЕДИА"){
        SettingRow("Медиаостров","Музыка и видео из медиасессий Android"){
            AppSwitch(media){media=it;prefs.media=it;changed();if(it&&!Permissions.listener(context))action.accept("listener")}
        }
        Column(Modifier.padding(16.dp)){Label(playing?.title?:"Ожидаем воспроизведение",15,bold=true);Label(playing?.app?:"Запусти музыку или видео в любом совместимом плеере",12,LocalLook.current.secondary)}
    }
    Caption("Через 4–5 секунд плеер сжимается. Нажми капсулу, чтобы раскрыть его. Доступные кнопки и перемотка определяются самим плеером; приложения без медиасессии Android управлять через остров нельзя.")
    if(picker){
        val apps by produceState<List<Pair<String,String>>>(emptyList()){
            value=withContext(Dispatchers.IO){context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).map{it.activityInfo.packageName to it.loadLabel(context.packageManager).toString()}.distinctBy{it.first}.filter{it.first!=context.packageName}.sortedBy{it.second.lowercase()}}
        }
        AlertDialog(onDismissRequest={picker=false},title={Label("Приложения",22,bold=!LocalLook.current.material)},text={LazyColumn(Modifier.heightIn(max=440.dp)){
            items(apps,key={it.first}){(pkg,name)->Row(Modifier.fillMaxWidth().clickable{selected=if(pkg in selected)selected-pkg else selected+pkg;prefs.apps=selected}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Label(name,14,modifier=Modifier.weight(1f));Checkbox(pkg in selected,{selected=if(it)selected+pkg else selected-pkg;prefs.apps=selected})}}
        }},confirmButton={TextButton(onClick={picker=false;changed()}){Text("Готово")}})
    }
}
