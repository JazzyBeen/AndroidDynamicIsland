package io.github.jazzybeen.androiddynamicisland.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import io.github.jazzybeen.androiddynamicisland.data.*
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.*
import io.nekohasekai.libbox.Libbox
import kotlinx.coroutines.*

object VpnSetupDialog{
    fun show(activity:Activity,after:()->Unit){
        val dialog=ComponentDialog(activity)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setContentView(ComposeView(dialog.context).apply{setContent{
            MaterialTheme(colorScheme=darkColorScheme(primary=Color.White,surface=Color(0xFF1C1C1E))){
                var api by remember{mutableStateOf(true)}
                var host by remember{mutableStateOf("")};var key by remember{mutableStateOf("")};var config by remember{mutableStateOf("")}
                var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
                val scope=rememberCoroutineScope()
                Column(Modifier.fillMaxWidth().padding(16.dp).background(Color(0xFF1C1C1E),RoundedCornerShape(28.dp)).padding(22.dp).verticalScroll(rememberScrollState()).imePadding()){
                    Label("Встроенный VPN",24,bold=true);Spacer(Modifier.height(10.dp))
                    Label("Подключение внутри острова — без запуска другого приложения.",13,Color(0xFF98989F));Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilterChip(api,{if(!busy)api=true},label={Label("По ключу",13)})
                        FilterChip(!api,{if(!busy)api=false},label={Label("Конфигурация",13)})
                    }
                    Spacer(Modifier.height(10.dp))
                    if(api){
                        OutlinedTextField(host,{host=it},label={Text("HTTPS-адрес API")},placeholder={Text("https://api.example.com")},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),modifier=Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(key,{key=it},label={Text("Ключ доступа")},singleLine=true,enabled=!busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp));Label("Для провайдеров с API connection/list и connection/connect. Ключ передаётся только указанному серверу по HTTPS.",11,Color(0xFF98989F))
                    }else{
                        OutlinedTextField(config,{config=it},label={Text("Ссылка VLESS или sing-box JSON")},minLines=3,maxLines=5,enabled=!busy,modifier=Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp));Label("Нужна конфигурация одного сервера. Правила, внешние файлы и API управления из исходного файла не импортируются.",11,Color(0xFF98989F))
                    }
                    Spacer(Modifier.height(12.dp));Label("Профиль шифруется ключом Android Keystore и не попадает в резервные копии. Android разрешает один активный VPN.",11,Color(0xFF98989F))
                    if(error.isNotBlank()){Spacer(Modifier.height(12.dp));Label(error,12,Color(0xFFFF6961))}
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(if(busy)"Проверка…" else "Сохранить профиль",enabled=!busy){
                        busy=true;error=""
                        scope.launch{
                            val failure=withContext(Dispatchers.IO){
                                try{
                                    val profile=if(api)ProviderApi.load(host.trim(),key.trim(),IslandSettings(activity).providerDeviceId())else TunnelConfig.importProfile(config.trim())
                                    Libbox.checkConfig(TunnelConfig.runtime(profile))
                                    currentCoroutineContext().ensureActive()
                                    profile.put("fetchedAt",System.currentTimeMillis());VpnProfileStore(activity).write(profile)
                                    VpnLocation.remember(activity,profile)
                                    IslandSettings(activity).apply{embeddedVpn(true);vpnName(profile.optString("name","Мой VPN"))}
                                    null
                                }catch(error:CancellationException){throw error}catch(_:Exception){"Не удалось проверить профиль. Проверь адрес API, ключ или формат конфигурации."}
                            }
                            busy=false
                            if(failure==null){key="";config="";VpnCoordinator.get(activity).clientChanged();dialog.dismiss();after()}else error=failure
                        }
                    }
                    Spacer(Modifier.height(9.dp));PrimaryButton("Отмена",primary=false,enabled=!busy){dialog.dismiss()}
                }
            }
        }})
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT));dialog.window?.setDimAmount(.8f)
        dialog.show();dialog.window?.setLayout(-1,-2)
    }
}
