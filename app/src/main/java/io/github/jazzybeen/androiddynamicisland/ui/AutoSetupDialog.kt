package io.github.jazzybeen.androiddynamicisland.ui

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.data.VpnLocation
import io.github.jazzybeen.androiddynamicisland.data.VpnProfileStore
import io.github.jazzybeen.androiddynamicisland.system.VpnNotificationListener
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.*
import io.nekohasekai.libbox.Libbox
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Builds the embedded profile from the access key the user copies in their provider's app and brings
 * back on the clipboard. The provider API is found in that app's installed APK (Android keeps it
 * world-readable) and the server is the provider's best one, or the place used last time. Nothing is
 * read from the other app's private data; the tunnel and its settings then live only in this app.
 */
object AutoSetupDialog{
    private sealed interface Stage{
        data object Intro:Stage
        data object Working:Stage
        data class Ask(val field:String,val message:String):Stage
        data class Done(val title:String,val detail:String):Stage
        data class Failed(val message:String):Stage
    }

    fun show(activity:ComponentActivity,after:()->Unit){
        val dialog=ComponentDialog(activity)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setCanceledOnTouchOutside(false)
        val settings=IslandSettings(activity)
        // App stores declare a VpnService for their own downloads; they are never a provider's client.
        val stores=setOf("com.xiaomi.mipicks","ru.vk.store","com.android.vending","com.huawei.appmarket","com.sec.android.app.samsungapps","com.heytap.market","com.oppo.market")
        val clients=VpnClientBridge.discover(activity).filter{it.packageName()!=activity.packageName&&it.packageName() !in stores}
        dialog.setContentView(ComposeView(dialog.context).apply{setContent{AppTheme{
            val look=LocalLook.current
            var client by remember{mutableStateOf(clients.firstOrNull{it.packageName()==settings.vpnPackage()}?:clients.firstOrNull{it.packageName()==VpnClientBridge.NATIVE_CONNECT_PACKAGE}?:clients.firstOrNull())}
            var stage by remember{mutableStateOf<Stage>(Stage.Intro)}
            val steps=remember{mutableStateListOf<Pair<Boolean,String>>()}
            var input by remember{mutableStateOf("")};var host by remember{mutableStateOf("")}
            var fromClipboard by remember{mutableStateOf(false)};var savedKey by remember{mutableStateOf(false)}
            val scope=rememberCoroutineScope()
            val appName=client?.label()

            var waiting by remember{mutableStateOf(false)}
            LaunchedEffect(Unit){
                savedKey=withContext(Dispatchers.IO){runCatching{VpnProfileStore(activity).read()?.optJSONObject("provider")?.optString("key").orEmpty().isNotBlank()}.getOrDefault(false)}
            }
            fun run(){
                val typed=SetupInput.classify(input)
                if(input.isNotBlank()&&typed==null){stage=Stage.Failed("Это не похоже на ключ доступа или ссылку vless://");return}
                stage=Stage.Working;steps.clear()
                val pkg=client?.packageName()
                scope.launch{
                    stage=withContext(Dispatchers.IO){
                        try{
                            fun step(ok:Boolean,text:String){scope.launch{steps.add(ok to text)}}
                            // If the provider's own app is connected right now, keep the country it is using.
                            val country=if(ProviderDiscovery.externalVpnActive(activity))ProviderDiscovery.exitCountry()else ""
                            if(country.isNotBlank())step(true,"Страна как сейчас в «${appName?:"VPN-приложении"}»: ${SetupHosts.countryName(country)}")
                            val previous=runCatching{VpnProfileStore(activity).read()?.optJSONObject("provider")}.getOrNull()
                            val profile:JSONObject=when(typed){
                                is SetupInput.Config->{
                                    step(true,"Конфигурация принята")
                                    TunnelConfig.importProfile(typed.text).apply{if(country.isNotBlank()){put("name",SetupHosts.countryName(country));put("countryCode",country)}}
                                }
                                else->{
                                    val accessKey=(typed as? SetupInput.Key)?.value?:previous?.optString("key").orEmpty()
                                    if(accessKey.isBlank())return@withContext Stage.Ask("key","Скопируй ключ доступа в «${appName?:"VPN-приложении"}» и вернись — или вставь его сюда.")
                                    step(true,if(typed is SetupInput.Key)"Ключ принят" else "Используется сохранённый ключ")
                                    val deviceId=settings.providerDeviceId()
                                    val known=listOf(host.trim(),(typed as? SetupInput.Host)?.origin.orEmpty(),settings.providerHost(),previous?.optString("host").orEmpty()).filter{it.isNotBlank()}.map{runCatching{ProviderApi.origin(it)}.getOrDefault("")}.filter{it.isNotBlank()}.distinct()
                                    val origin=known.firstOrNull{o->runCatching{ProviderApi.locations(o,accessKey,deviceId);true}.getOrDefault(false)}
                                        ?:pkg?.let{step(true,"Ищу сервер провайдера в «$appName»");ProviderDiscovery.findApi(ProviderDiscovery.apiCandidates(activity,it),accessKey,deviceId)}
                                        ?:return@withContext Stage.Ask("host","Не нашёл адрес API провайдера${if(appName!=null)" в «$appName»" else ""}. Вставь его один раз — обычно это адрес личного кабинета, его подскажет поддержка.")
                                    step(true,"Сервер провайдера найден")
                                    settings.providerHost(origin)
                                    // Same key as before: stay where the user was. New key or a live country: let it decide.
                                    val stay=country.isBlank()&&previous?.optString("key")==accessKey
                                    ProviderApi.load(origin,accessKey,deviceId,
                                        locationId=if(stay)previous?.optString("locationId") else null,
                                        gatewayName=if(stay)previous?.optString("gatewayName") else null,
                                        countryCode=country,hint=VpnNotificationListener.textOf(pkg))
                                }
                            }
                            Libbox.checkConfig(TunnelConfig.runtime(profile))
                            profile.put("fetchedAt",System.currentTimeMillis());VpnProfileStore(activity).write(profile)
                            VpnLocation.remember(activity,profile)
                            settings.embeddedVpn(true);settings.vpnName(profile.optString("name","Мой VPN"));if(pkg!=null)settings.vpnPackage(pkg)
                            step(true,"Профиль сохранён и зашифрован")
                            Stage.Done("${VpnLocation.emoji(settings.vpnCountryCode())} ${settings.vpnCountry()}",settings.vpnCity())
                        }catch(e:CancellationException){throw e}catch(e:Exception){Stage.Failed(e.message?.takeIf{it.length<160}?:"Не удалось настроить профиль")}
                    }
                    if(stage is Stage.Done){input="";VpnCoordinator.get(activity).clientChanged()}
                }
            }
            /** The key the user just copied in the provider's app; read only on their way back for this purpose. */
            fun takeFromClipboard(){
                val copied=runCatching{activity.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf{it.itemCount>0}?.getItemAt(0)?.coerceToText(activity)?.toString()}.getOrNull()
                when(val found=SetupInput.classify(copied)){
                    is SetupInput.Key->{input=found.value;fromClipboard=true;run()}
                    is SetupInput.Config->{input=found.text;fromClipboard=true;run()}
                    is SetupInput.Host->{host=found.origin;stage=Stage.Ask("key","Адрес сервера взят из буфера обмена. Теперь скопируй ключ доступа в «${appName?:"VPN-приложении"}» или вставь его сюда.")}
                    null->stage=Stage.Ask("key","В буфере обмена нет ключа доступа. Скопируй его в «${appName?:"VPN-приложении"}» ещё раз или вставь сюда.")
                }
            }
            // Android lets an app read the clipboard only while it has focus, i.e. once the user is back here.
            DisposableEffect(Unit){
                val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME&&waiting){waiting=false;scope.launch{delay(450);takeFromClipboard()}}}
                activity.lifecycle.addObserver(observer);onDispose{activity.lifecycle.removeObserver(observer)}
            }
            fun openClient(){
                val launch=client?.let{activity.packageManager.getLaunchIntentForPackage(it.packageName())}?:return
                waiting=true;activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            @Composable fun KeyField(){
                val hint=when{fromClipboard->"Вставлено из буфера обмена";input.isBlank()&&savedKey->"Пусто — будет использован сохранённый ключ";else->null}
                OutlinedTextField(input,{input=it;fromClipboard=false},label={Text("Ключ доступа или ссылка vless://")},singleLine=true,
                    visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth(),
                    supportingText=if(hint!=null){{Text(hint)}}else null)
            }
            @Composable fun Steps(size:Int){
                steps.forEach{(ok,text)->Row(Modifier.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).background(if(ok)look.positive else look.secondary,CircleShape));Spacer(Modifier.width(10.dp));Label(text,size,look.secondary)}}
            }

            Column(Modifier.fillMaxWidth().padding(16.dp).background(if(look.material)MaterialTheme.colorScheme.surfaceContainerHigh else look.card,RoundedCornerShape(28.dp)).padding(22.dp).verticalScroll(rememberScrollState()).imePadding()){
                Label("Подключение VPN",24,bold=!look.material);Spacer(Modifier.height(12.dp))
                when(val s=stage){
                    Stage.Intro->if(appName==null){
                        Label("Не нашёл VPN-приложения провайдера. Вставь ключ доступа или ссылку vless:// — адрес API понадобится один раз, если остров не найдёт его сам.",14,look.secondary)
                        Spacer(Modifier.height(14.dp));KeyField()
                        Spacer(Modifier.height(18.dp));PrimaryButton("Подключить",enabled=input.isNotBlank()||savedKey){run()}
                        Spacer(Modifier.height(4.dp));SecondaryAction("Ввести вручную"){dialog.dismiss();VpnSetupDialog.show(activity,after)}
                    }else{
                        Label("1. В «$appName» скопируй ключ доступа.\n2. Вернись сюда — остров сам возьмёт ключ из буфера обмена, найдёт сервер и создаст туннель. Профиль хранится зашифрованным только на этом телефоне.",14,look.secondary)
                        if(clients.size>1){
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){clients.forEach{c->FilterChip(c==client,{client=c},label={Label(c.label(),13)})}}
                        }
                        Spacer(Modifier.height(18.dp));PrimaryButton("Открыть «$appName»"){openClient()}
                        Spacer(Modifier.height(4.dp));SecondaryAction("Ключ уже скопирован"){takeFromClipboard()}
                        if(savedKey)SecondaryAction("Использовать сохранённый ключ"){input="";run()}
                        SecondaryAction("Ввести вручную"){dialog.dismiss();VpnSetupDialog.show(activity,after)}
                    }
                    Stage.Working->{
                        Steps(14)
                        Row(Modifier.padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),color=look.accent,strokeWidth=2.dp);Spacer(Modifier.width(10.dp));Label("Настраиваю…",13,look.secondary)}
                    }
                    is Stage.Ask->{
                        Steps(13)
                        Spacer(Modifier.height(10.dp));Label(s.message,14)
                        Spacer(Modifier.height(12.dp))
                        if(s.field=="key")KeyField()
                        else OutlinedTextField(host,{host=it},label={Text("Адрес API")},placeholder={Text("https://api.example.com")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),modifier=Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp));PrimaryButton("Продолжить",enabled=if(s.field=="key")input.isNotBlank() else host.isNotBlank()){run()}
                        if(s.field=="key"&&appName!=null)SecondaryAction("Открыть «$appName»"){openClient()}
                    }
                    is Stage.Done->{
                        Label(s.title,22,bold=true);if(s.detail.isNotBlank()){Spacer(Modifier.height(2.dp));Label(s.detail,14,look.secondary)}
                        Spacer(Modifier.height(14.dp));Steps(13)
                        Spacer(Modifier.height(12.dp))
                        if(settings.autoConnect())Label("Дальше VPN включается сам — после перезагрузки телефона и обрыва связи. Выключить это можно в разделе VPN.",12,look.secondary)
                        if(appName!=null){Spacer(Modifier.height(6.dp));Label("Android держит один VPN: если «$appName» сейчас подключён, он отключится, когда включится остров.",12,look.secondary)}
                        Spacer(Modifier.height(18.dp));PrimaryButton("Подключить"){dialog.dismiss();after()}
                    }
                    is Stage.Failed->{
                        Label(s.message,14,look.danger)
                        Spacer(Modifier.height(18.dp));PrimaryButton("Повторить"){stage=Stage.Intro}
                        SecondaryAction("Ввести вручную"){dialog.dismiss();VpnSetupDialog.show(activity,after)}
                    }
                }
                if(stage !is Stage.Done&&stage !is Stage.Working){Spacer(Modifier.height(2.dp));Box(Modifier.fillMaxWidth().clickable{dialog.dismiss()}.padding(10.dp),contentAlignment=Alignment.Center){Label("Отмена",13,look.secondary)}}
            }
        }}})
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT));dialog.window?.setDimAmount(.8f)
        dialog.show();dialog.window?.setLayout(-1,-2)
    }
}
