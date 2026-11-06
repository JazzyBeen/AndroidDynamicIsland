package io.github.jazzybeen.androiddynamicisland.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import io.github.jazzybeen.androiddynamicisland.R
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.*
import io.github.jazzybeen.androiddynamicisland.vpn.*
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.IslandVpnService
import io.github.jazzybeen.androiddynamicisland.data.VpnProfileStore
import androidx.compose.ui.graphics.toArgb

class MainActivity:ComponentActivity(){
    private lateinit var settings:IslandSettings
    private var screen:View?=null
    private var configuring=false
    private var waitingForAccessibility=false
    private val main=Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);settings=IslandSettings(this)
        window.setDecorFitsSystemWindows(false);applySystemBars()
        screen=AppUi.create(this,settings){handle(it)};setContentView(screen)
        if(!settings.introduced())main.postDelayed({if(!isFinishing){settings.introduced(true);AppDialogs.show(this,"Твой остров","Добавь встроенный VPN-профиль и разреши показ острова через специальные возможности.\n\nПрофиль хранится зашифрованным. Приложение не делает снимки экрана и не использует микрофон.","Настроить",{VpnSetupDialog.show(this){configuring=true;nextSetup()}},"Позже")}},350)
    }
    override fun onResume(){
        super.onResume();if(!::settings.isInitialized)return
        refresh();VpnCoordinator.get(this).refresh();VpnNotificationListener.refreshActivities()
        if(settings.enabled()&&Permissions.ready(this))IslandService.ensureStarted(this)
        if(waitingForAccessibility){waitingForAccessibility=false;if(configuring&&Permissions.accessibility(this))main.postDelayed({nextSetup()},300)}
        main.postDelayed({if(!isFinishing)refresh()},450)
    }
    private fun refresh(){screen?.let{AppUi.refresh(it)}}
    private fun nextSetup(){
        if(!VpnClientBridge.installed(this)){VpnSetupDialog.show(this){refresh();nextSetup()};return}
        if(!Permissions.notifications(this)&&Build.VERSION.SDK_INT>=33){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),41);return}
        if(!Permissions.accessibility(this)){
            AppDialogs.show(this,"Показ острова","Включи Dynamic Island в разделе специальных возможностей. Служба показывает остров и распознаёт меню питания. При необходимости она нажимает кнопку VPN только в выбранном клиенте.\n\nЕсли Android блокирует доступ: информация о приложении → ⋮ → «Разрешить ограниченные настройки».","Открыть настройки",{waitingForAccessibility=true;open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
            return
        }
        configuring=false;settings.enabled(true);IslandService.ensureStarted(this);refresh()
        AppDialogs.show(this,"Остров включён","Кнопка «Показать остров» проверяет отображение.\n\nВ разделе VPN проверь подключение и отключение. Для вызова без меню выключения можно назначить остров системным ассистентом в настройках приложения.","Показать остров",{VpnCoordinator.get(this).preview()},"Готово")
    }
    private fun handle(action:String){when(action){
        "setup"->{configuring=true;nextSetup()}
        "enabled"->{if(settings.enabled()){settings.enabled(false);stopService(Intent(this,IslandService::class.java));IslandAccessibilityService.current()?.hideIsland();refresh()}else{configuring=true;nextSetup()}}
        "pickClient"->{if(IslandVpnService.status.connected||IslandVpnService.status.busy)profileBusy()else ClientPicker.show(this){refresh()}}
        "embeddedSetup"->{if(IslandVpnService.status.connected||IslandVpnService.status.busy)profileBusy()else VpnSetupDialog.show(this){refresh();authorizeVpn(false)}}
        "forgetProfile"->{if(IslandVpnService.status.connected||IslandVpnService.status.busy)profileBusy()else AppDialogs.show(this,"Удалить профиль?","Ключ и конфигурация будут удалены с этого устройства.","Удалить",{VpnProfileStore(this).clear();settings.embeddedVpn(false);settings.vpnPackage("");VpnCoordinator.get(this).clientChanged();refresh()},"Отмена")}
        "toggleVpn"->{
            if(!VpnClientBridge.installed(this)||!Permissions.ready(this)){configuring=true;nextSetup()}
            else if(IslandAccessibilityService.current()==null)showAccessibilityHelp()
            else{settings.enabled(true);IslandService.ensureStarted(this);refresh();if(settings.embeddedVpn())authorizeVpn(true)else VpnCoordinator.get(this).toggle()}
        }
        "preview"->{if(IslandAccessibilityService.current()!=null)VpnCoordinator.get(this).preview()else showAccessibilityHelp()}
        "bannerPermission"->open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        "originGuide"->{IslandAccessibilityService.current()?.showOriginGuide()}
        "eventPreview"->{VpnCoordinator.get(this).notifyCompact("Тестовое оповещение",IslandVpnService.status.connected)}
        "originPreview"->{if(IslandAccessibilityService.current()!=null){IslandAccessibilityService.current()?.hideIsland();VpnCoordinator.get(this).preview()}else showAccessibilityHelp()}
        "client"->{try{VpnClientBridge.open(this,false)}catch(_:RuntimeException){ClientPicker.show(this){refresh()}}}
        "accessibility"->showAccessibilityHelp()
        "listener"->AppDialogs.show(this,"Уведомления для острова","Доступ нужен для выбранных уведомлений и медиаплеера. Приложение не хранит историю сообщений и не отправляет их на сервер. Выбор источников — в разделе «События».","Открыть настройки",{open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))})
        "notifications"->{if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),42)}
        "battery"->AppDialogs.show(this,"Работа в фоне","Разреши работу без оптимизации батареи для острова и своего VPN-клиента.","Разрешить",{open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName")))})
        "autostart"->{try{startActivity(Intent().setComponent(ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity")))}catch(_:RuntimeException){open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}}
        "details"->open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
        "assistant"->assistantGuide()
        "theme"->{applySystemBars();refresh()}
        "tile"->{if(Build.VERSION.SDK_INT>=33)getSystemService(StatusBarManager::class.java).requestAddTileService(ComponentName(this,IslandTileService::class.java),"VPN · Island",Icon.createWithResource(this,R.drawable.ic_island),mainExecutor){}else AppDialogs.show(this,"Плитка VPN","Открой редактор шторки и добавь VPN · Island.","Понятно",{})}
    }}
    private fun profileBusy(){AppDialogs.show(this,"VPN работает","Сначала отключи VPN, затем измени профиль.","Понятно",{})}
    private fun authorizeVpn(connect:Boolean){
        val permission=android.net.VpnService.prepare(this)
        if(permission!=null){connectAfterPermission=connect;startActivityForResult(permission,73)}else if(connect)VpnCoordinator.get(this).toggle()
    }
    private var connectAfterPermission=false
    private fun showAccessibilityHelp(){AppDialogs.show(this,"Специальные возможности","Открой Dynamic Island и включи службу. Если она уже включена, но остров не появляется, выключи и снова включи её.\n\nДругие настройки специальных возможностей менять не нужно.","Открыть настройки",{open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})}
    /** System bars follow the chosen style: black for the classic look, tonal surfaces for Material 3. */
    private fun applySystemBars(){
        val material=settings.material()
        val dark=resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
        val scheme=Palette.scheme(settings.accent(),dark)
        val top=if(material)scheme.surface.toArgb() else Color.BLACK
        val bottom=if(material)scheme.surfaceContainer.toArgb() else Color.BLACK
        window.statusBarColor=top;window.navigationBarColor=bottom;window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(top))
        val light=material&&!dark
        window.insetsController?.setSystemBarsAppearance(if(light)android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
    }
    private val xiaomi get()=Build.MANUFACTURER.equals("Xiaomi",true)||Build.BRAND.lowercase() in setOf("xiaomi","redmi","poco")
    /** Short device-specific steps: which screen assigns the assistant and which one binds it to the power key. */
    private fun assistantGuide(){
        val selected=AssistantController.isSelected(this)
        val assistant=when{
            xiaomi->"Настройки → Приложения → Приложения по умолчанию → «Цифровой помощник» (в MIUI: «Помощник и голосовой ввод») → Dynamic Island."
            Build.VERSION.SDK_INT>=31->"Настройки → Приложения → Приложения по умолчанию → Цифровой помощник → Приложение цифрового помощника → Dynamic Island."
            else->"Настройки → Приложения → Приложения по умолчанию → Помощник и голосовой ввод → Dynamic Island."
        }
        val power=when{
            xiaomi->"Настройки → Дополнительные настройки → «Быстрые жесты» или «Функции кнопок» → «Запуск Google Ассистента» / «Цифровой помощник» → «Удержание кнопки питания 0,5 с»."
            Build.VERSION.SDK_INT>=31->"Настройки → Система → Жесты → Нажатие и удерживание кнопки питания → Цифровой помощник."
            else->"На Android 11 удержание питания открывает меню выключения. Остров распознаёт его сам — отдельно ничего включать не нужно."
        }
        val first=if(selected)"1. Ассистент — готово ✓" else "1. Сделай остров ассистентом\n$assistant"
        val message="$first\n\n2. Включи удержание питания\n$power\n\nНазвания пунктов могут немного отличаться в разных версиях${if(xiaomi)" HyperOS и MIUI" else " Android"}. Голос и микрофон не используются."
        AppDialogs.show(this,if(xiaomi)"Кнопка питания на Xiaomi" else "Кнопка питания",message,if(selected)"Открыть настройки" else "Выбрать ассистента",{
            if(selected)open(Intent(Settings.ACTION_SETTINGS))
            else try{AssistantController.request(this)}catch(_:RuntimeException){open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))}
        },"Понятно")
    }
    private fun open(intent:Intent){try{startActivity(intent)}catch(_:ActivityNotFoundException){AppDialogs.show(this,"Открой настройки телефона","Этот раздел недоступен по прямой ссылке.","Понятно",{})}}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);refresh();if(requestCode==41&&configuring&&Permissions.notifications(this))nextSetup()}
    @Deprecated("Activity result bridge") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==72&&AssistantController.isSelected(this)){refresh();main.postDelayed({assistantGuide()},250)};if(requestCode==73){if(resultCode==RESULT_OK&&connectAfterPermission)VpnCoordinator.get(this).toggle();connectAfterPermission=false;refresh()}}
    override fun onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy()}
}
